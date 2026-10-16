package com.pocketdev.duorhythm.ui.wallet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AccountPreset(
    val name: String,
    val icon: String, // "cash", "phone", "bank", "card", "crypto", "globe", "shield", "target", "tools"
    val emoji: String,
    val colorHex: String,
    val isCustom: Boolean = false
)

object AccountPresetManager {
    private const val PREFS_NAME = "lifesync_prefs"
    private const val KEY_CUSTOM_PRESETS = "custom_account_presets_json_v1"

    val DEFAULT_ICON_PRESETS = listOf(
        AccountPreset("Cash", "cash", "💵", "#10B981"),
        AccountPreset("Mobile / MFS", "phone", "📱", "#EC4899"),
        AccountPreset("Bank", "bank", "🏦", "#6366F1"),
        AccountPreset("Card", "card", "💳", "#3B82F6"),
        AccountPreset("Crypto", "crypto", "🪙", "#F59E0B"),
        AccountPreset("Online", "globe", "🌐", "#0EA5E9"),
        AccountPreset("Vault", "shield", "🛡️", "#F97316"),
        AccountPreset("Savings", "target", "🎯", "#8B5CF6"),
        AccountPreset("Work", "tools", "💼", "#14B8A6")
    )

    fun getCustomPresets(context: Context): List<AccountPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        val list = mutableListOf<AccountPreset>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AccountPreset(
                        name = obj.getString("name"),
                        icon = obj.optString("icon", "wallet"),
                        emoji = obj.optString("emoji", "🏷️"),
                        colorHex = obj.optString("colorHex", "#0284C7"),
                        isCustom = true
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCustomPreset(context: Context, preset: AccountPreset) {
        val current = getCustomPresets(context).toMutableList()
        current.removeAll { it.name.equals(preset.name, ignoreCase = true) }
        current.add(0, preset)

        val arr = JSONArray()
        for (item in current) {
            val obj = JSONObject().apply {
                put("name", item.name)
                put("icon", item.icon)
                put("emoji", item.emoji)
                put("colorHex", item.colorHex)
            }
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_PRESETS, arr.toString()).apply()
    }
}
