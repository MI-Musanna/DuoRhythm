package com.pocketdev.duorhythm.data.repository

import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.CurrencyRate
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object CurrencyService {

    interface SyncCallback {
        fun onSyncSuccess(message: String)
        fun onSyncError(errorMessage: String)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun syncLatestRates(context: Context, callback: SyncCallback) {
        if (!isNetworkAvailable(context)) {
            callback.onSyncError("No internet connection. Using cached offline currency rates.")
            return
        }

        thread {
            var connection: HttpURLConnection? = null
            try {
                // Free, open rate API without API key required
                val url = URL("https://open.er-api.com/v6/latest/USD")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    if (json.has("rates")) {
                        val rates = json.getJSONObject("rates")
                        val today = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                        val db = DatabaseHelper.getInstance(context)

                        val targetCurrencies = listOf(
                            "BDT", "USD", "EUR", "GBP", "INR", "JPY",
                            "CAD", "AUD", "AED", "SAR", "SGD", "MYR", "PKR"
                        )
                        for (curr in targetCurrencies) {
                            if (rates.has(curr)) {
                                val rate = rates.getDouble(curr)
                                db.updateCurrencyRate(curr, rate, today)
                            }
                        }

                        callback.onSyncSuccess("Currency exchange rates updated successfully ($today)")
                    } else {
                        callback.onSyncError("Invalid response format from currency server.")
                    }
                } else {
                    callback.onSyncError("Server responded with HTTP code ${connection.responseCode}")
                }
            } catch (e: Exception) {
                callback.onSyncError("Sync failed: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                connection?.disconnect()
            }
        }
    }
}
