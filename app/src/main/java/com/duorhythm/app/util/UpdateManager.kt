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
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object UpdateManager {

    private const val GITHUB_RELEASES_ATOM = "https://github.com/MI-Musanna/DuoRhythm/releases.atom"
    private const val GITHUB_RELEASES_LATEST_URL = "https://github.com/MI-Musanna/DuoRhythm/releases/latest"
    private const val GITHUB_DOWNLOAD_BASE = "https://github.com/MI-Musanna/DuoRhythm/releases/download"
    private var downloadJob: Job? = null

    @Volatile
    var isChecking: Boolean = false
        internal set

    internal var releasesAtomUrl: String = GITHUB_RELEASES_ATOM
    internal var releasesLatestUrl: String = GITHUB_RELEASES_LATEST_URL
    internal var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    internal var mainDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main

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

    // 2. CHECK UPDATES: Zero-Rate-Limit GitHub CDN (releases.atom + /releases/latest redirect)
    fun buildCdnApkUrl(tag: String): String {
        val normalizedTag = if (tag.startsWith("v", ignoreCase = true)) tag else "v$tag"
        return "$GITHUB_DOWNLOAD_BASE/$normalizedTag/DuoRhythm-$normalizedTag.apk"
    }

    fun parseReleasesAtom(
        xml: String,
        prefs: android.content.SharedPreferences? = null
    ): Pair<ReleaseInfo?, ReleaseInfo?> {
        var latestStable: ReleaseInfo? = null
        var latestBeta: ReleaseInfo? = null

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inEntry = false
        var currentTag = ""
        var entryTitle = ""
        var entryTag = ""
        var entryContentHtml = ""
        var entryIsPrerelease = false

        val editor = prefs?.edit()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.orEmpty().lowercase(Locale.US)
                    if (currentTag == "entry") {
                        inEntry = true
                        entryTitle = ""
                        entryTag = ""
                        entryContentHtml = ""
                        entryIsPrerelease = false
                    } else if (inEntry && currentTag == "link") {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        if (href.contains("/releases/tag/")) {
                            entryTag = href.substringAfter("/releases/tag/").substringBefore("?").substringBefore("#").trim()
                        }
                    } else if (inEntry && currentTag == "category") {
                        val term = parser.getAttributeValue(null, "term").orEmpty()
                        if (term.equals("prerelease", ignoreCase = true)) {
                            entryIsPrerelease = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inEntry) {
                        val text = parser.text.orEmpty()
                        when (currentTag) {
                            "title" -> entryTitle += text
                            "content" -> entryContentHtml += text
                            "id" -> if (entryTag.isEmpty() && text.contains("/")) {
                                val candidate = text.substringAfterLast("/").trim()
                                if (candidate.startsWith("v", ignoreCase = true) || candidate.firstOrNull()?.isDigit() == true) {
                                    entryTag = candidate
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val endName = parser.name.orEmpty().lowercase(Locale.US)
                    if (endName == "entry" && inEntry) {
                        inEntry = false
                        val resolvedTag = entryTag.ifBlank {
                            entryTitle.trim().split(Regex("\\s+")).lastOrNull {
                                it.startsWith("v", ignoreCase = true) || it.firstOrNull()?.isDigit() == true
                            }.orEmpty()
                        }.trim()

                        if (resolvedTag.isNotEmpty()) {
                            val rawText = if (entryContentHtml.isNotBlank()) {
                                HtmlCompat.fromHtml(entryContentHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                                    .toString()
                                    .replace('\u00A0', ' ')
                                    .lines()
                                    .map { it.trimEnd() }
                                    .joinToString("\n")
                                    .replace(Regex("\n{3,}"), "\n\n")
                                    .trim()
                            } else {
                                entryTitle.trim().ifBlank { "Bug fixes and performance improvements." }
                            }

                            val release = ReleaseInfo(
                                tagName = resolvedTag,
                                isPrerelease = entryIsPrerelease,
                                downloadUrl = buildCdnApkUrl(resolvedTag),
                                bodyText = rawText
                            )

                            editor?.putString("online_body_${cleanVersion(resolvedTag)}", rawText)
                            editor?.putString("online_body_$resolvedTag", rawText)

                            if (!entryIsPrerelease && (latestStable == null || compareVersions(release.tagName, latestStable.tagName) > 0)) {
                                latestStable = release
                            }
                            if (entryIsPrerelease && (latestBeta == null || compareVersions(release.tagName, latestBeta.tagName) > 0)) {
                                latestBeta = release
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        editor?.apply()
        return Pair(latestStable, latestBeta)
    }

    fun resolveLatestTagRedirect(
        latestUrl: String = releasesLatestUrl,
        prefs: android.content.SharedPreferences? = null
    ): ReleaseInfo? {
        val conn = URL(latestUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "DuoRhythm-Android")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            val code = conn.responseCode
            val location = conn.getHeaderField("Location").orEmpty()
            if (code in 300..399 && location.contains("/releases/tag/")) {
                val tag = location.substringAfter("/releases/tag/").substringBefore("?").substringBefore("#").trim()
                if (tag.isNotEmpty()) {
                    val cachedBody = prefs?.getString("online_body_${cleanVersion(tag)}", null)
                        ?: prefs?.getString("online_body_$tag", null)
                        ?: "Latest release $tag available on GitHub."
                    ReleaseInfo(tag, isPrerelease = false, downloadUrl = buildCdnApkUrl(tag), bodyText = cachedBody)
                } else null
            } else {
                null
            }
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    fun checkForUpdates(
        context: Context,
        isManualCheck: Boolean,
        onResult: ((ReleaseInfo?, ReleaseInfo?) -> Unit)? = null
    ) {
        if (isChecking) {
            if (isManualCheck) {
                com.duorhythm.app.ui.custom.DuoAlertBanner.info(context, "Checking for updates...")
            }
            return
        }
        isChecking = true
        CoroutineScope(ioDispatcher).launch {
            try {
                val prefs = context.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE)
                var latestStable: ReleaseInfo? = null
                var latestBeta: ReleaseInfo? = null
                var primaryError: Exception? = null

                // Primary: GitHub CDN releases.atom (zero rate limit + full release notes)
                try {
                    val conn = URL(releasesAtomUrl).openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "DuoRhythm-Android")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val code = conn.responseCode
                    if (code == 200) {
                        val rawXml = conn.inputStream.bufferedReader().use { it.readText() }
                        val parsed = parseReleasesAtom(rawXml, prefs)
                        latestStable = parsed.first
                        latestBeta = parsed.second
                    } else {
                        primaryError = java.io.IOException("CDN feed returned HTTP $code")
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    primaryError = e
                }

                // Fallback: /releases/latest redirect resolution
                if (latestStable == null) {
                    val fallbackStable = try {
                        resolveLatestTagRedirect(releasesLatestUrl, prefs)
                    } catch (e: Exception) {
                        if (primaryError == null) primaryError = e
                        null
                    }
                    if (fallbackStable != null) {
                        latestStable = fallbackStable
                    }
                }

                if (latestStable == null && latestBeta == null && primaryError != null) {
                    throw primaryError!!
                }

                withContext(mainDispatcher) {
                    if (onResult != null) {
                        onResult(latestStable, latestBeta)
                    } else if (isManualCheck) {
                        showModernDualChannelDialog(context, latestStable, latestBeta)
                    } else {
                        handleAutoStablePrompt(context, latestStable)
                    }
                }
            } catch (e: Exception) {
                withContext(mainDispatcher) {
                    if (isManualCheck) {
                        com.duorhythm.app.ui.custom.DuoAlertBanner.error(
                            context,
                            "Update check failed: ${e.localizedMessage ?: "Unable to reach GitHub"}"
                        )
                    }
                    onResult?.invoke(null, null)
                }
            } finally {
                isChecking = false
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
            // 3. Query GitHub CDN releases.atom using properly normalized tag (with 'v')
            CoroutineScope(ioDispatcher).launch {
                var fetchedBody: String? = null

                try {
                    val url = URL(releasesAtomUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "DuoRhythm-Android")
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000

                    if (conn.responseCode == 200) {
                        val xml = conn.inputStream.bufferedReader().use { it.readText() }
                        parseReleasesAtom(xml, prefs)
                        fetchedBody = prefs.getString("online_body_$targetTag", null)
                            ?: prefs.getString("online_body_$cleanVer", null)
                            ?: prefs.getString("online_body_$currentVer", null)
                    }
                    conn.disconnect()
                } catch (_: Exception) {}

                withContext(mainDispatcher) {
                    progressBar?.visibility = View.GONE
                    tvBody?.visibility = View.VISIBLE
                    if (!fetchedBody.isNullOrBlank()) {
                        tvBody?.text = formatReleaseNotes(fetchedBody)
                    } else if (!localChangelog.isNullOrBlank()) {
                        tvBody?.text = formatReleaseNotes(localChangelog)
                    } else {
                        tvBody?.text = "• Version $currentVer applied successfully.\n• Zero-rate-limit GitHub CDN releases.atom updater & offline notes cache.\n• Reactive Flow SSOT for Tasks, Goals, and Class Schedules.\n• Unified DuoEmptyStateView & Home 1/3 Resumable Get-Started Checklist."
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
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_updater, null)
        dialog.setContentView(view)

        val currentVersion = BuildConfig.VERSION_NAME

        val btnClose = view.findViewById<View>(R.id.btnCloseUpdateDialog)
        btnClose?.setOnClickListener { dialog.dismiss() }

        val tvStatusPill = view.findViewById<TextView>(R.id.tvUpdateStatusPill)
        val tvInstalledVersion = view.findViewById<TextView>(R.id.tvCurrentInstalledVersion)
        tvInstalledVersion?.text = "Installed: v$currentVersion"

        val latestRelease = listOfNotNull(stable, beta)
            .maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) }

        val tvReleaseNotes = view.findViewById<TextView>(R.id.tvReleaseNotesPreview)
        val notes = latestRelease?.bodyText?.takeIf { it.isNotBlank() }
            ?: "You are running the latest DuoRhythm release."
        tvReleaseNotes?.text = formatReleaseNotes(notes)

        // Permanently hide secondary channel card to keep a clean single-card release view
        val cardStable = view.findViewById<View>(R.id.cardStableTrack)
        val tvNoStable = view.findViewById<View>(R.id.tvNoStableBanner)
        cardStable?.visibility = View.GONE
        tvNoStable?.visibility = View.GONE

        // --- UNIFIED SINGLE RELEASE CARD ---
        val cardRelease = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBetaTrack)
        val tvReleaseLabel = view.findViewById<TextView>(R.id.tvBetaLabel)
        val tvReleaseVersion = view.findViewById<TextView>(R.id.tvBetaVersion)
        val badgeReleaseStatus = view.findViewById<TextView>(R.id.badgeBetaInstalled)
        val btnActionRelease = view.findViewById<MaterialButton>(R.id.btnActionBeta)

        tvReleaseLabel?.text = if (latestRelease?.isPrerelease == true) "Preview Release" else "Official Release"
        cardRelease?.visibility = View.VISIBLE

        val hasUpdate = latestRelease != null && compareVersions(currentVersion, latestRelease.tagName) < 0
        if (hasUpdate && latestRelease != null) {
            tvStatusPill?.text = "UPDATE AVAILABLE • ${latestRelease.tagName}"
            tvStatusPill?.setTextColor(Color.parseColor("#38BDF8"))
            tvStatusPill?.setBackgroundResource(R.drawable.bg_pill_blue)

            badgeReleaseStatus?.text = "VERIFIED LATEST"
            badgeReleaseStatus?.setTextColor(Color.parseColor("#38BDF8"))

            tvReleaseVersion?.text = "${latestRelease.tagName} • Ready to Install"
            tvReleaseVersion?.setTextColor(Color.parseColor("#38BDF8"))

            btnActionRelease?.text = "INSTALL UPDATE"
            btnActionRelease?.isEnabled = true
            btnActionRelease?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            btnActionRelease?.setTextColor(Color.parseColor("#0A0B0E"))
            btnActionRelease?.setOnClickListener {
                dialog.dismiss()
                showInAppDownloadDialog(context, latestRelease.downloadUrl, "${latestRelease.tagName}.apk")
            }
        } else {
            tvStatusPill?.text = "UP TO DATE"
            tvStatusPill?.setTextColor(Color.parseColor("#10B981"))
            tvStatusPill?.setBackgroundResource(R.drawable.bg_pill_dark)

            badgeReleaseStatus?.text = "UP TO DATE"
            badgeReleaseStatus?.setTextColor(Color.parseColor("#10B981"))

            tvReleaseVersion?.text = if (latestRelease != null) {
                "${latestRelease.tagName} • Installed"
            } else {
                "v$currentVersion • Installed"
            }
            tvReleaseVersion?.setTextColor(Color.parseColor("#10B981"))

            btnActionRelease?.text = "UP TO DATE"
            btnActionRelease?.isEnabled = false
            btnActionRelease?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
            btnActionRelease?.setTextColor(Color.parseColor("#64748B"))
            btnActionRelease?.setOnClickListener(null)
        }

        dialog.show()
    }
}
