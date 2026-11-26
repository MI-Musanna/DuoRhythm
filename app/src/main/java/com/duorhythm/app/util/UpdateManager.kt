package com.duorhythm.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

    // Helper: Semantic version comparison (returns > 0 if v1 > v2, 0 if equal, < 0 if v1 < v2)
    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = cleanVersion(v1)
        val clean2 = cleanVersion(v2)
        if (clean1 == clean2) return 0

        val numPart1 = clean1.substringBefore("-")
        val numPart2 = clean2.substringBefore("-")
        val parts1 = numPart1.split(".").map { seg ->
            seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        val parts2 = numPart2.split(".").map { seg ->
            seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }

        val suffix1 = if (clean1.contains("-")) clean1.substringAfter("-") else ""
        val suffix2 = if (clean2.contains("-")) clean2.substringAfter("-") else ""
        if (suffix1 == suffix2) return 0
        if (suffix1.isEmpty()) return 1 // Stable > Beta
        if (suffix2.isEmpty()) return -1 // Beta < Stable
        return suffix1.compareTo(suffix2)
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
                            val isPrerelease = item.optBoolean("prerelease", false)

                            val release = ReleaseInfo(tag, isPrerelease, apkUrl, body)

                            // Permanently cache the release body using normalized key
                            prefs.edit().putString("online_body_${cleanVersion(tag)}", body).apply()

                            if (!isPrerelease && (latestStable == null || compareVersions(release.tagName, latestStable.tagName) > 0)) {
                                latestStable = release
                            }
                            if (isPrerelease && (latestBeta == null || compareVersions(release.tagName, latestBeta.tagName) > 0)) {
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

        if (compareVersions(currentVer, stable.tagName) < 0 && !currentVer.contains("dev", ignoreCase = true)) {
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
            // Check if installed version already matches or exceeds the release tag
            val versionTag = fileName.removeSuffix(".apk")
            if (compareVersions(BuildConfig.VERSION_NAME, versionTag) >= 0) {
                withContext(Dispatchers.Main) {
                    tvTitle?.text = "Already Up to Date 🎉"
                    tvProgress?.text = "You are already running version ${BuildConfig.VERSION_NAME}."
                    progressBar?.progress = 100
                    btnCancel?.text = "Close"
                }
                return@launch
            }

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var connection: HttpURLConnection? = null

            try {
                var currentUrl = apkUrl
                var redirects = 0
                while (redirects < 5) {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", "DuoRhythm-Android")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                        responseCode == 307 || responseCode == 308) {
                        val newUrl = connection.getHeaderField("Location") ?: break
                        connection.disconnect()
                        currentUrl = newUrl
                        redirects++
                    } else {
                        break
                    }
                }

                val totalLength = connection?.contentLengthLong ?: -1L
                inputStream = connection?.inputStream
                outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                val totalMB = if (totalLength > 0) totalLength.toDouble() / (1024 * 1024) else 0.0

                while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        inputStream?.close()
                        destinationFile.delete()
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val downloadedMB = totalBytesRead.toDouble() / (1024 * 1024)
                    val progressPercent = if (totalLength > 0) ((totalBytesRead * 100) / totalLength).toInt() else 0

                    withContext(Dispatchers.Main) {
                        progressBar?.progress = progressPercent
                        if (totalLength > 0) {
                            tvProgress?.text = String.format(
                                Locale.US,
                                "%d%% (%.2f MB / %.2f MB)",
                                progressPercent,
                                downloadedMB,
                                totalMB
                            )
                        } else {
                            tvProgress?.text = String.format(
                                Locale.US,
                                "%.2f MB downloaded",
                                downloadedMB
                            )
                        }
                    }
                }

                outputStream.flush()

                withContext(Dispatchers.Main) {
                    progressBar?.progress = 100
                    tvTitle?.text = "Update Downloaded 🎉"
                    tvProgress?.text = "Ready to install • Tap Install to restart"
                    btnCancel?.visibility = View.GONE
                    btnInstall?.visibility = View.VISIBLE
                    btnInstall?.setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                            launchPackageInstaller(context, destinationFile)
                        } else {
                            dialog.dismiss()
                            launchPackageInstaller(context, destinationFile)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvTitle?.text = "Download Failed"
                    tvProgress?.text = e.localizedMessage ?: "Network interrupted"
                    btnCancel?.text = "Close"
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

    fun launchPackageInstaller(context: Context, file: File) {
        if (!file.exists()) {
            com.duorhythm.app.ui.custom.DuoAlertBanner.show(context, "Installation file not found.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                com.duorhythm.app.ui.custom.DuoAlertBanner.show(context, "Please allow DuoRhythm to install updates, then tap Install again.")
                return
            }
        }

        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            com.duorhythm.app.ui.custom.DuoAlertBanner.show(context, "Failed to start installer: ${e.message}")
        }
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

        // 1. Check local assets for exact version release notes
        val localChangelog = try {
            val jsonStr = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
            val obj = org.json.JSONObject(jsonStr)
            val note = obj.optString(currentVer, obj.optString(cleanVer, ""))
            if (note.isNotBlank()) note else null
        } catch (_: Exception) { null }

        // 2. Instant Cache Hit
        val targetTag = if (currentVer.startsWith("v", ignoreCase = true)) currentVer else "v$currentVer"
        val cachedBody = prefs.getString("online_body_$targetTag", null)
            ?: prefs.getString("online_body_$cleanVer", null)
            ?: prefs.getString("online_body_$currentVer", null)
        if (!cachedBody.isNullOrBlank()) {
            progressBar?.visibility = View.GONE
            tvBody?.visibility = View.VISIBLE
            tvBody?.text = formatReleaseNotes(cachedBody)
        } else if (!localChangelog.isNullOrBlank()) {
            progressBar?.visibility = View.GONE
            tvBody?.visibility = View.VISIBLE
            tvBody?.text = formatReleaseNotes(localChangelog)
        } else {
            // 3. Query GitHub using properly normalized tag (with 'v')
            CoroutineScope(Dispatchers.IO).launch {
                var fetchedBody: String? = null

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
                            prefs.edit()
                                .putString("online_body_$targetTag", fetchedBody)
                                .putString("online_body_$cleanVer", fetchedBody)
                                .putString("online_body_$currentVer", fetchedBody)
                                .apply()
                        }
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    progressBar?.visibility = View.GONE
                    tvBody?.visibility = View.VISIBLE
                    if (!fetchedBody.isNullOrBlank()) {
                        tvBody?.text = formatReleaseNotes(fetchedBody)
                    } else if (!localChangelog.isNullOrBlank()) {
                        tvBody?.text = formatReleaseNotes(localChangelog)
                    } else {
                        tvBody?.text = "• Version $currentVer applied successfully.\n• Enforced explicit oval outline clipping for Home & Profile avatars.\n• Required profile password validation across all workspace creation slots.\n• Fixed 4-segment updater version check & blocked ghost notifications for deleted classes."
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

    // 5. MANUAL RELEASE CHANNEL DIALOG (Settings Hub) - High-Contrast Material 3 UI
    fun showModernDualChannelDialog(context: Context, stable: ReleaseInfo?, beta: ReleaseInfo?) {
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_updater, null)
        dialog.setContentView(view)

        val currentVersion = BuildConfig.VERSION_NAME

        val btnClose = view.findViewById<View>(R.id.btnCloseUpdateDialog)
        btnClose?.setOnClickListener { dialog.dismiss() }

        val tvStatusPill = view.findViewById<TextView>(R.id.tvUpdateStatusPill)
        val tvInstalledVersion = view.findViewById<TextView>(R.id.tvCurrentInstalledVersion)
        tvInstalledVersion?.text = "Installed: v$currentVersion"

        val tvReleaseNotes = view.findViewById<TextView>(R.id.tvReleaseNotesPreview)
        val notes = beta?.bodyText?.takeIf { it.isNotBlank() } ?: stable?.bodyText ?: "No release notes available."
        tvReleaseNotes?.text = formatReleaseNotes(notes)

        val cardStable = view.findViewById<View>(R.id.cardStableTrack)
        val tvNoStable = view.findViewById<View>(R.id.tvNoStableBanner)

        // --- STABLE / LATEST TRACK ---
        var stableHasUpdate = false
        if (stable != null && !stable.isPrerelease) {
            cardStable?.visibility = View.VISIBLE
            tvNoStable?.visibility = View.GONE
            val tvStable = view.findViewById<TextView>(R.id.tvStableVersion)
            val btnStable = view.findViewById<MaterialButton>(R.id.btnInstallStable)
            tvStable?.text = "${stable.tagName} • Verified Latest"

            val isStableCurrent = compareVersions(currentVersion, stable.tagName) >= 0
            if (isStableCurrent) {
                btnStable?.text = "INSTALLED"
                btnStable?.isEnabled = false
                btnStable?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
                btnStable?.setTextColor(Color.parseColor("#64748B"))
                btnStable?.strokeColor = null
            } else {
                stableHasUpdate = true
                btnStable?.text = "INSTALL LATEST"
                btnStable?.isEnabled = true
                btnStable?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
                btnStable?.setTextColor(Color.parseColor("#0A0B0E"))
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
        val badgeBeta = view.findViewById<TextView>(R.id.badgeBetaInstalled)
        val btnActionBeta = view.findViewById<MaterialButton>(R.id.btnActionBeta)

        val isBetaCurrent = beta == null || compareVersions(currentVersion, beta.tagName) >= 0
        if (!isBetaCurrent && beta != null) {
            tvBeta?.text = "${beta.tagName} • Bleeding-Edge Preview"
            tvStatusPill?.text = "UPDATE AVAILABLE • ${beta.tagName}"
            tvStatusPill?.setTextColor(Color.parseColor("#38BDF8"))
            tvStatusPill?.setBackgroundResource(R.drawable.bg_pill_blue)

            badgeBeta?.text = "UPDATE"
            badgeBeta?.setTextColor(Color.parseColor("#38BDF8"))

            btnActionBeta?.text = "DOWNLOAD UPDATE"
            btnActionBeta?.isEnabled = true
            btnActionBeta?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            btnActionBeta?.setTextColor(Color.parseColor("#0A0B0E"))

            val clickAction = View.OnClickListener {
                dialog.dismiss()
                showInAppDownloadDialog(context, beta.downloadUrl, "${beta.tagName}.apk")
            }
            btnActionBeta?.setOnClickListener(clickAction)
        } else {
            tvBeta?.text = if (beta != null) "${beta.tagName} • Up To Date" else "v$currentVersion • Up To Date"
            if (stableHasUpdate) {
                tvStatusPill?.text = "UPDATE AVAILABLE • ${stable?.tagName}"
                tvStatusPill?.setTextColor(Color.parseColor("#10B981"))
                tvStatusPill?.setBackgroundResource(R.drawable.bg_pill_dark)
            } else {
                tvStatusPill?.text = "UP TO DATE"
                tvStatusPill?.setTextColor(Color.parseColor("#10B981"))
                tvStatusPill?.setBackgroundResource(R.drawable.bg_pill_dark)
            }

            badgeBeta?.text = "UP TO DATE"
            badgeBeta?.setTextColor(Color.parseColor("#10B981"))

            btnActionBeta?.text = "NO NEWER BETA"
            btnActionBeta?.isEnabled = false
            btnActionBeta?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
            btnActionBeta?.setTextColor(Color.parseColor("#64748B"))
            btnActionBeta?.setOnClickListener(null)
        }

        dialog.show()
    }
}
