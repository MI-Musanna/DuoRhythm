package com.duorhythm.app.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AppUpdateChecker {

    private const val GITHUB_API_URL = "https://api.github.com/repos/MI-Musanna/DuoRhythm/releases/latest"
    private const val USER_AGENT = "DuoRhythm-App"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.1-beta"
        } catch (_: Exception) {
            "1.0.1-beta"
        }
    }

    fun checkForUpdates(activity: Activity, manual: Boolean = false) {
        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (manual) {
                    mainHandler.post {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            Toast.makeText(
                                activity,
                                "Could not check for updates. Check your connection.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        if (manual) {
                            mainHandler.post {
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    Toast.makeText(
                                        activity,
                                        "Could not check for updates (${res.code}).",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        return@use
                    }

                    val bodyString = res.body?.string() ?: return@use
                    try {
                        val json = JSONObject(bodyString)
                        val tagName = json.optString("tag_name", "").trim()
                        val releaseNotes = json.optString("body", "No changelog provided.").trim()
                        val releaseHtmlUrl = json.optString("html_url", "https://github.com/MI-Musanna/DuoRhythm/releases")

                        // Look for .apk asset
                        var apkDownloadUrl = releaseHtmlUrl
                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.optJSONObject(i) ?: continue
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkDownloadUrl = asset.optString("browser_download_url", releaseHtmlUrl)
                                    break
                                }
                            }
                        }

                        val currentVersion = getAppVersion(activity)
                        val isNewer = isRemoteVersionNewer(tagName, currentVersion)

                        mainHandler.post {
                            if (activity.isFinishing || activity.isDestroyed) return@post

                            if (isNewer) {
                                showUpdateDialog(activity, tagName, releaseNotes, apkDownloadUrl)
                            } else if (manual) {
                                Toast.makeText(
                                    activity,
                                    "DuoRhythm is already up to date! (v$currentVersion)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (manual) {
                            mainHandler.post {
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    Toast.makeText(
                                        activity,
                                        "Error parsing update info.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    return@use
                }
            }
        })
    }

    private fun showUpdateDialog(
        activity: Activity,
        newVersion: String,
        changelog: String,
        downloadUrl: String
    ) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available ($newVersion)")
            .setMessage("A new version of DuoRhythm is available on GitHub!\n\nChangelog:\n$changelog")
            .setPositiveButton("Download Update") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(activity, "Could not open download link.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    /**
     * Compares remote tag (e.g. "v1.0.2" or "1.0.1") with local version (e.g. "1.0.1-beta")
     */
    fun isRemoteVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = currentVersion.removePrefix("v").removePrefix("V").trim()

        if (cleanRemote.isBlank() || cleanCurrent.isBlank()) return false
        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false

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

        // If numeric parts are identical, a release without suffix (e.g. 1.0.1) is newer than pre-release (1.0.1-beta)
        val remoteHasPre = cleanRemote.contains("-")
        val currentHasPre = cleanCurrent.contains("-")
        if (!remoteHasPre && currentHasPre) return true
        if (remoteHasPre && !currentHasPre) return false

        return cleanRemote.compareTo(cleanCurrent, ignoreCase = true) > 0
    }
}
