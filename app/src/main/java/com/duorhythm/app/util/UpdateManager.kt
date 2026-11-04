package com.duorhythm.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
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
import org.json.JSONObject
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

    fun cleanupDownloadedApks(context: Context) {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            val apkFiles = downloadDir.listFiles { file -> file.name.endsWith(".apk") }
            apkFiles?.forEach { file -> if (file.exists()) file.delete() }
        } catch (_: Exception) {}
    }

    fun checkForUpdates(
        context: Context,
        isManualCheck: Boolean,
        onResult: ((ReleaseInfo?, ReleaseInfo?) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = URL(GITHUB_RELEASES_API).openConnection() as HttpURLConnection
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
                        val body = item.optString("body", "Bug fixes and performance improvements.")
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
                            val isBeta = item.optBoolean("prerelease", false) ||
                                    tag.contains("beta", ignoreCase = true) ||
                                    tag.contains("alpha", ignoreCase = true) ||
                                    tag.contains("rc", ignoreCase = true)

                            val release = ReleaseInfo(tag, isBeta, apkUrl, body)

                            // Cache the online release body for this tag
                            val prefs = context.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE)
                            prefs.edit().putString("online_body_$tag", body).apply()

                            if (!isBeta && latestStable == null) {
                                latestStable = release
                            }
                            if (isBeta && latestBeta == null) {
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
                            handleAutoStablePrompt(context, latestStable)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isManualCheck) {
                        AlertDialog.Builder(context)
                            .setTitle("Update Check Failed")
                            .setMessage("Network error or GitHub API limit: ${e.localizedMessage}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun handleAutoStablePrompt(context: Context, stable: ReleaseInfo?) {
        if (stable == null) return
        val currentVer = BuildConfig.VERSION_NAME

        if (stable.tagName != currentVer && !currentVer.contains("dev", ignoreCase = true)) {
            val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_auto_stable_update, null)
            dialog.setContentView(view)

            dialog.findViewById<TextView>(R.id.tvAutoUpdateTitle)?.text = "🚀 New Stable Update: ${stable.tagName}"
            dialog.findViewById<TextView>(R.id.tvAutoUpdateBody)?.text = stable.bodyText

            dialog.findViewById<MaterialButton>(R.id.btnUpdateNow)?.setOnClickListener {
                downloadAndInstallApk(context, stable.downloadUrl, "${stable.tagName}.apk")
                dialog.dismiss()
            }
            dialog.findViewById<MaterialButton>(R.id.btnUpdateLater)?.setOnClickListener {
                dialog.dismiss()
            }
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

        val receiver = object : BroadcastReceiver() {
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
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    // 100% DYNAMIC WHAT'S NEW: Fetches live GitHub Release body for the current version
    fun showWhatsNewDialog(context: Context, forceManual: Boolean = false) {
        val prefs = context.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE)
        val currentVer = BuildConfig.VERSION_NAME
        val prefKey = "suppress_whats_new_$currentVer"

        if (!forceManual && prefs.getBoolean(prefKey, false)) {
            return
        }

        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_whats_new, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvWhatsNewTitle)?.text = "What's New in $currentVer ✨"
        val tvBody = view.findViewById<TextView>(R.id.tvWhatsNewBody)
        val progressBar = view.findViewById<ProgressBar>(R.id.pbLoadingReleaseNotes)
        val cbDontShow = view.findViewById<CheckBox>(R.id.cbDontShowAgain)

        // 1. Check local cache first
        val cachedBody = prefs.getString("online_body_$currentVer", null)
        if (!cachedBody.isNullOrBlank()) {
            progressBar?.visibility = View.GONE
            tvBody?.visibility = View.VISIBLE
            tvBody?.text = formatReleaseNotes(cachedBody)
        } else {
            // 2. Fetch directly from GitHub Releases API
            CoroutineScope(Dispatchers.IO).launch {
                var fetchedBody: String? = null
                try {
                    val url = URL("$GITHUB_RELEASES_API/tags/$currentVer")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "DuoRhythm-Android")
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        fetchedBody = json.optString("body", "")
                        if (!fetchedBody.isNullOrBlank()) {
                            prefs.edit().putString("online_body_$currentVer", fetchedBody).apply()
                        }
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    tvBody?.visibility = View.VISIBLE
                    if (!fetchedBody.isNullOrBlank()) {
                        tvBody?.text = formatReleaseNotes(fetchedBody)
                    } else {
                        tvBody?.text = "• Version $currentVer applied successfully.\n• Real-time GitHub release notes unavailable offline.\n• Visit github.com/MI-Musanna/DuoRhythm/releases for full notes."
                    }
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.btnDismissWhatsNew)?.setOnClickListener {
            if (cbDontShow?.isChecked == true) {
                prefs.edit().putBoolean(prefKey, true).apply()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun formatReleaseNotes(rawMarkdown: String): String {
        return rawMarkdown
            .replace("###", "•")
            .replace("##", "•")
            .replace("#", "")
            .replace("**", "")
            .trim()
    }

    fun showModernDualChannelDialog(context: Context, stable: ReleaseInfo?, beta: ReleaseInfo?) {
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_updater, null)
        dialog.setContentView(view)

        val currentVersion = BuildConfig.VERSION_NAME
        val cardStable = view.findViewById<View>(R.id.cardStableTrack)
        val tvNoStable = view.findViewById<View>(R.id.tvNoStableBanner)

        if (stable != null && !stable.tagName.contains("beta", ignoreCase = true)) {
            cardStable?.visibility = View.VISIBLE
            tvNoStable?.visibility = View.GONE
            val tvStable = view.findViewById<TextView>(R.id.tvStableVersion)
            val btnStable = view.findViewById<MaterialButton>(R.id.btnInstallStable)
            tvStable?.text = "${stable.tagName} • Verified Stable"
            if (currentVersion == stable.tagName) {
                btnStable?.text = "Installed"
                btnStable?.isEnabled = false
            } else {
                btnStable?.text = "Install"
                btnStable?.setOnClickListener {
                    downloadAndInstallApk(context, stable.downloadUrl, "${stable.tagName}.apk")
                    dialog.dismiss()
                }
            }
        } else {
            cardStable?.visibility = View.GONE
            tvNoStable?.visibility = View.VISIBLE
        }

        val tvBeta = view.findViewById<TextView>(R.id.tvBetaVersion)
        val btnBeta = view.findViewById<TextView>(R.id.badgeBetaInstalled)
        if (beta != null) {
            tvBeta?.text = "${beta.tagName} • Bleeding-Edge Preview"
            if (currentVersion == beta.tagName) {
                btnBeta?.text = "INSTALLED"
            } else {
                btnBeta?.text = "UPDATE"
                btnBeta?.setOnClickListener {
                    downloadAndInstallApk(context, beta.downloadUrl, "${beta.tagName}.apk")
                    dialog.dismiss()
                }
            }
        }

        view.findViewById<View>(R.id.btnCloseUpdateDialog)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
