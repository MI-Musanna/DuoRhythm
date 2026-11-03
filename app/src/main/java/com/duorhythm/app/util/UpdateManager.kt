package com.duorhythm.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.duorhythm.app.BuildConfig
import com.duorhythm.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/MI-Musanna/DuoRhythm/releases"

    data class ReleaseInfo(
        val tagName: String,
        val isPrerelease: Boolean,
        val downloadUrl: String,
        val bodyText: String
    )

    fun cleanVersion(ver: String): String {
        return ver.trim().removePrefix("v").removePrefix("V").lowercase()
    }

    /**
     * Strictly verifies whether remoteTag represents a higher semantic release version
     * than the locally running version, stripping any 'v' or 'V' prefix.
     */
    fun isRemoteVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = cleanVersion(remoteTag)
        val cleanCurrent = cleanVersion(currentVersion)

        if (cleanRemote.isBlank() || cleanCurrent.isBlank()) return false
        if (cleanRemote == cleanCurrent) return false

        // Extract base digits x.y.z
        val remoteParts = cleanRemote.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // If numeric parts are identical, a release without suffix (e.g. 1.0.2) is newer than pre-release (1.0.2-beta)
        val remoteHasPre = cleanRemote.contains("-")
        val currentHasPre = cleanCurrent.contains("-")
        if (!remoteHasPre && currentHasPre) return true
        if (remoteHasPre && !currentHasPre) return false

        return cleanRemote > cleanCurrent
    }

    fun checkForUpdates(context: Context, isManualCheck: Boolean, onResult: ((ReleaseInfo?, ReleaseInfo?) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(GITHUB_RELEASES_API)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "DuoRhythm-Android")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val rawJson = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(rawJson)

                    var latestStable: ReleaseInfo? = null
                    var latestBeta: ReleaseInfo? = null

                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val tag = item.getString("tag_name")
                        val body = item.optString("body", "Bug fixes and enhancements.")
                        val assets = item.getJSONArray("assets")
                        var apkUrl = ""
                        for (a in 0 until assets.length()) {
                            val asset = assets.getJSONObject(a)
                            if (asset.getString("name").endsWith(".apk")) {
                                apkUrl = asset.getString("browser_download_url")
                                break
                            }
                        }

                        if (apkUrl.isNotEmpty()) {
                            val isExplicitBeta = item.optBoolean("prerelease", false) ||
                                    tag.contains("beta", ignoreCase = true) ||
                                    tag.contains("alpha", ignoreCase = true) ||
                                    tag.contains("rc", ignoreCase = true)

                            val release = ReleaseInfo(tag, isExplicitBeta, apkUrl, body)

                            if (!isExplicitBeta && latestStable == null) {
                                latestStable = release
                            }
                            if (isExplicitBeta && latestBeta == null) {
                                latestBeta = release
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (onResult != null) {
                            onResult(latestStable, latestBeta)
                        } else if (isManualCheck) {
                            showModernDualChannelDialog(context, latestStable, latestBeta)
                        } else {
                            // Restrict auto-prompt to official stable releases only (never auto-prompt beta)
                            handleAutoPrompt(context, latestStable)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (isManualCheck) {
                            AlertDialog.Builder(context)
                                .setTitle("Update Check Failed")
                                .setMessage("GitHub API response code: ${conn.responseCode}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isManualCheck) {
                        AlertDialog.Builder(context)
                            .setTitle("Update Check Failed")
                            .setMessage("Network error or GitHub API rate limit: ${e.localizedMessage}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun handleAutoPrompt(context: Context, stable: ReleaseInfo?) {
        if (stable == null || stable.isPrerelease) return
        val currentVer = BuildConfig.VERSION_NAME

        // Only prompt if stable release is strictly newer than current version and not running dev build
        if (isRemoteVersionNewer(stable.tagName, currentVer) && !cleanVersion(currentVer).contains("dev")) {
            AlertDialog.Builder(context)
                .setTitle("🚀 New Update Available: ${stable.tagName}")
                .setMessage("A verified stable release of DuoRhythm is ready.\n\nHighlights:\n${stable.bodyText.take(180)}...")
                .setPositiveButton("Update Now") { _, _ ->
                    downloadAndInstallApk(context, stable.downloadUrl, "${cleanVersion(stable.tagName)}.apk")
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    fun showModernDualChannelDialog(context: Context, stable: ReleaseInfo?, beta: ReleaseInfo?) {
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_updater, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.background = null
        }

        val currentVersion = BuildConfig.VERSION_NAME

        val cardStable = view.findViewById<View>(R.id.cardStableTrack)
        val tvNoStable = view.findViewById<View>(R.id.tvNoStableBanner)

        // Only show stable card if it truly exists and does NOT contain 'beta'
        if (stable != null && !stable.tagName.contains("beta", ignoreCase = true)) {
            cardStable.visibility = View.VISIBLE
            tvNoStable.visibility = View.GONE
            val tvStable = view.findViewById<TextView>(R.id.tvStableVersion)
            val btnStable = view.findViewById<MaterialButton>(R.id.btnInstallStable)
            tvStable.text = "${stable.tagName} • Verified Stable"
            if (currentVersion == stable.tagName) {
                btnStable.text = "Installed"
                btnStable.isEnabled = false
            } else {
                btnStable.text = "Install"
                btnStable.setOnClickListener {
                    downloadAndInstallApk(context, stable.downloadUrl, "${stable.tagName}.apk")
                    dialog.dismiss()
                }
            }
        } else {
            // Zero stable releases on GitHub -> HIDE IT COMPLETELY
            cardStable.visibility = View.GONE
            tvNoStable.visibility = View.VISIBLE
        }

        // Bind Beta Track
        val cardBeta = view.findViewById<View>(R.id.cardBetaTrack)
        val tvBeta = view.findViewById<TextView>(R.id.tvBetaVersion)
        val badgeBeta = view.findViewById<TextView>(R.id.badgeBetaInstalled)
        if (beta != null) {
            cardBeta.visibility = View.VISIBLE
            tvBeta.text = "${beta.tagName} • Bleeding-Edge Preview"
            if (currentVersion == beta.tagName) {
                badgeBeta.text = "INSTALLED"
            } else {
                badgeBeta.text = "UPDATE"
                val startBetaInstall = View.OnClickListener {
                    downloadAndInstallApk(context, beta.downloadUrl, "${beta.tagName}.apk")
                    dialog.dismiss()
                }
                cardBeta.setOnClickListener(startBetaInstall)
                badgeBeta.setOnClickListener(startBetaInstall)
            }
        }

        view.findViewById<View>(R.id.btnCloseUpdateDialog).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showResultOrientedWhatsNew(context: Context, force: Boolean = true) {
        val prefs = context.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE)
        val lastSeenVer = prefs.getString("last_seen_version", "")
        val currentVer = BuildConfig.VERSION_NAME

        // Show when forced (e.g. from settings button) or when a new version code is detected
        if (force || lastSeenVer != currentVer) {
            prefs.edit().putString("last_seen_version", currentVer).apply()

            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
            val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_whats_new, null)
            dialog.setContentView(view)

            dialog.setOnShowListener {
                val bottomSheet = dialog.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                bottomSheet?.background = null
            }

            view.findViewById<android.widget.TextView>(R.id.tvWhatsNewTitle).text = "What's New in $currentVer ✨"
            view.findViewById<android.widget.TextView>(R.id.tvWhatsNewBody).text = """
                🎯 100% Dynamic Course Filters: Hardcoded categories completely removed. Filter chips only show categories with active items.
                🧹 Zero Home Top-Gap: Top bar and profile section seamlessly flush with OLED deep obsidian black.
                📝 Redesigned Scratchpad: Clean single-note card without ghost duplicate previews.
                🔇 Sound Module Purged: Unnecessary synthesizer stripped out for high performance.
                ⏰ Unified Morning Brief: No more duplicate 8:00 AM or 9:00 AM alarms.
            """.trimIndent()

            view.findViewById<android.view.View>(R.id.btnDismissWhatsNew).setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    fun downloadAndInstallApk(context: Context, apkUrl: String, fileName: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Downloading DuoRhythm Update")
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadId = dm.enqueue(request)

        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            c.unregisterReceiver(this)
                        } catch (_: Exception) {}
                        val file = File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", file)
                        } else {
                            Uri.fromFile(file)
                        }
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        c.startActivity(installIntent)
                    }
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun showWhatsNewIfUpdated(context: Context) {
        val prefs = context.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE)
        val lastSeenVer = prefs.getString("last_seen_version", "")
        val currentVer = BuildConfig.VERSION_NAME
        if (lastSeenVer != currentVer) {
            showResultOrientedWhatsNew(context, force = true)
        }
    }
}
