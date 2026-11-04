package com.duorhythm.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.duorhythm.app.BuildConfig
import com.duorhythm.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object UpdateManager {

    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/MI-Musanna/DuoRhythm/releases"
    private var downloadJob: Job? = null

    data class ReleaseInfo(
        val tagName: String,
        val isPrerelease: Boolean,
        val downloadUrl: String,
        val bodyText: String
    )

    // Helper: Normalize version string by stripping 'v', 'V' and whitespace
    fun cleanVersion(ver: String): String {
        return ver.trim().removePrefix("v").removePrefix("V").lowercase()
    }

    // 1. AUTOMATIC CLEANUP: Purges leftover .apk files from downloads directory
    fun cleanupDownloadedApks(context: Context) {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            val apkFiles = downloadDir.listFiles { file -> file.name.endsWith(".apk") }
            apkFiles?.forEach { file -> if (file.exists()) file.delete() }
        } catch (_: Exception) {}
    }

    // 2. CHECK UPDATES: Strictly discriminates between Stable and Beta
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

                    val prefs = context.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE)

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

                            // Permanently cache the release body using normalized key
                            prefs.edit().putString("online_body_${cleanVersion(tag)}", body).apply()

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

        if (cleanVersion(stable.tagName) != cleanVersion(currentVer) && !currentVer.contains("dev", ignoreCase = true)) {
            val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_auto_stable_update, null)
            dialog.setContentView(view)

            dialog.findViewById<TextView>(R.id.tvAutoUpdateTitle)?.text = "🚀 New Stable Update: ${stable.tagName}"
            dialog.findViewById<TextView>(R.id.tvAutoUpdateBody)?.text = stable.bodyText

            dialog.findViewById<MaterialButton>(R.id.btnUpdateNow)?.setOnClickListener {
                dialog.dismiss()
                showInAppDownloadDialog(context, stable.downloadUrl, "${stable.tagName}.apk")
            }
            dialog.findViewById<MaterialButton>(R.id.btnUpdateLater)?.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        }
    }

    // 3. STREMIO-STYLE IN-APP STREAMING DOWNLOAD PROGRESS MODAL
    fun showInAppDownloadDialog(context: Context, apkUrl: String, fileName: String) {
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_inapp_download, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        try {
            dialog.behavior.isHideable = false
        } catch (_: Exception) {}

        val tvTitle = view.findViewById<TextView>(R.id.tvDownloadTitle)
        val tvProgress = view.findViewById<TextView>(R.id.tvDownloadProgressDetails)
        val progressBar = view.findViewById<ProgressBar>(R.id.pbDownloadHorizontal)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelDownload)
        val btnInstall = view.findViewById<MaterialButton>(R.id.btnInstallDownloadedApk)

        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) destinationFile.delete()

        btnCancel.setOnClickListener {
            downloadJob?.cancel()
            if (destinationFile.exists()) destinationFile.delete()
            dialog.dismiss()
        }

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var connection: HttpURLConnection? = null

            try {
                val url = URL(apkUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "DuoRhythm-Android")
                connection.connect()

                // Resolve HTTP 302 redirects from GitHub to AWS S3 CDN
                var responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    connection = URL(newUrl).openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "DuoRhythm-Android")
                    connection.connect()
                }

                val totalLength = connection.contentLengthLong
                inputStream = connection.inputStream
                outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                val totalMB = totalLength.toDouble() / (1024 * 1024)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        inputStream.close()
                        destinationFile.delete()
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val downloadedMB = totalBytesRead.toDouble() / (1024 * 1024)
                    val progressPercent = if (totalLength > 0) ((totalBytesRead * 100) / totalLength).toInt() else 0

                    withContext(Dispatchers.Main) {
                        progressBar.progress = progressPercent
                        tvProgress.text = String.format(
                            Locale.US,
                            "%d%% (%.2f MB / %.2f MB)",
                            progressPercent,
                            downloadedMB,
                            totalMB
                        )
                    }
                }

                outputStream.flush()

                withContext(Dispatchers.Main) {
                    progressBar.progress = 100
                    tvTitle.text = "Update Downloaded 🎉"
                    tvProgress.text = "Ready to install • Tap Install to restart"
                    btnCancel.visibility = View.GONE
                    btnInstall.visibility = View.VISIBLE
                    btnInstall.setOnClickListener {
                        dialog.dismiss()
                        launchPackageInstaller(context, destinationFile)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvTitle.text = "Download Failed"
                    tvProgress.text = e.localizedMessage ?: "Network interrupted"
                    btnCancel.text = "Close"
                    if (destinationFile.exists()) destinationFile.delete()
                }
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { inputStream?.close() } catch (_: Exception) {}
                connection?.disconnect()
            }
        }

        dialog.show()
    }

    private fun launchPackageInstaller(context: Context, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    // 4. WHAT'S NEW MODAL: Dynamically Fetches GitHub Release Body with Normalized Tags
    fun showWhatsNewDialog(context: Context, forceManual: Boolean = false) {
        val prefs = context.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE)
        val currentVer = BuildConfig.VERSION_NAME
        val cleanVer = cleanVersion(currentVer)
        val prefKey = "suppress_whats_new_$cleanVer"

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

        // 1. Instant Cache Hit
        val cachedBody = prefs.getString("online_body_$cleanVer", null)
        if (!cachedBody.isNullOrBlank()) {
            progressBar?.visibility = View.GONE
            tvBody?.visibility = View.VISIBLE
            tvBody?.text = formatReleaseNotes(cachedBody)
        } else {
            // 2. Query GitHub using properly normalized tag (with 'v')
            CoroutineScope(Dispatchers.IO).launch {
                var fetchedBody: String? = null
                val targetTag = if (currentVer.startsWith("v", ignoreCase = true)) currentVer else "v$currentVer"

                try {
                    val url = URL("$GITHUB_RELEASES_API/tags/$targetTag")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "DuoRhythm-Android")
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000

                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        fetchedBody = json.optString("body", "")
                        if (!fetchedBody.isNullOrBlank()) {
                            prefs.edit().putString("online_body_$cleanVer", fetchedBody).apply()
                        }
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    tvBody?.visibility = View.VISIBLE
                    if (!fetchedBody.isNullOrBlank()) {
                        tvBody?.text = formatReleaseNotes(fetchedBody)
                    } else {
                        tvBody?.text = "• Version $currentVer applied successfully.\n• In-app download progress modal enabled.\n• Installed versions now marked and disabled in release hub.\n• All audio modules completely purged."
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

    // 5. MANUAL RELEASE CHANNEL DIALOG (Settings Hub) - Fixes Button Disabled State
    fun showModernDualChannelDialog(context: Context, stable: ReleaseInfo?, beta: ReleaseInfo?) {
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_updater, null)
        dialog.setContentView(view)

        val currentVersion = BuildConfig.VERSION_NAME
        val cleanCurrent = cleanVersion(currentVersion)

        val cardStable = view.findViewById<View>(R.id.cardStableTrack)
        val tvNoStable = view.findViewById<View>(R.id.tvNoStableBanner)

        // --- STABLE TRACK ---
        if (stable != null && !stable.tagName.contains("beta", ignoreCase = true)) {
            cardStable?.visibility = View.VISIBLE
            tvNoStable?.visibility = View.GONE
            val tvStable = view.findViewById<TextView>(R.id.tvStableVersion)
            val btnStable = view.findViewById<MaterialButton>(R.id.btnInstallStable)
            tvStable?.text = "${stable.tagName} • Verified Stable"

            val isStableCurrent = cleanVersion(stable.tagName) == cleanCurrent
            if (isStableCurrent) {
                btnStable?.text = "INSTALLED"
                btnStable?.isEnabled = false
                btnStable?.setBackgroundColor(Color.parseColor("#1E293B"))
                btnStable?.setTextColor(Color.parseColor("#64748B"))
                btnStable?.strokeColor = null
            } else {
                btnStable?.text = "Install"
                btnStable?.isEnabled = true
                btnStable?.setBackgroundColor(Color.parseColor("#10B981"))
                btnStable?.setTextColor(Color.parseColor("#06080F"))
                btnStable?.setOnClickListener {
                    dialog.dismiss()
                    showInAppDownloadDialog(context, stable.downloadUrl, "${stable.tagName}.apk")
                }
            }
        } else {
            cardStable?.visibility = View.GONE
            tvNoStable?.visibility = View.VISIBLE
        }

        // --- BETA / PREVIEW TRACK ---
        val cardBeta = view.findViewById<View>(R.id.cardBetaTrack)
        val tvBeta = view.findViewById<TextView>(R.id.tvBetaVersion)
        val btnBeta = view.findViewById<TextView>(R.id.badgeBetaInstalled)

        if (beta != null) {
            tvBeta?.text = "${beta.tagName} • Bleeding-Edge Preview"

            val isBetaCurrent = cleanVersion(beta.tagName) == cleanCurrent
            if (isBetaCurrent) {
                // Version currently running on phone -> Mark as INSTALLED, Gray out, and disable click!
                btnBeta?.text = "INSTALLED"
                btnBeta?.setTextColor(Color.parseColor("#64748B"))
                btnBeta?.setBackgroundResource(R.drawable.bg_pill_dark)
                btnBeta?.isClickable = false
                btnBeta?.setOnClickListener(null)
                cardBeta?.isClickable = false
                cardBeta?.setOnClickListener(null)
            } else {
                // Newer beta available -> Active Update Button
                btnBeta?.text = "UPDATE"
                btnBeta?.setTextColor(Color.parseColor("#00E5FF"))
                btnBeta?.setBackgroundResource(R.drawable.bg_pill_blue)
                btnBeta?.isClickable = true
                val clickAction = View.OnClickListener {
                    dialog.dismiss()
                    showInAppDownloadDialog(context, beta.downloadUrl, "${beta.tagName}.apk")
                }
                btnBeta?.setOnClickListener(clickAction)
                cardBeta?.setOnClickListener(clickAction)
            }
        }

        view.findViewById<View>(R.id.btnCloseUpdateDialog)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
