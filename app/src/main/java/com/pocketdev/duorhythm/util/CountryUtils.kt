package com.pocketdev.duorhythm.util

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

object CountryUtils {

    private val countryDialCodeMap: Map<String, String> = mapOf(
        "AF" to "+93", "AL" to "+355", "DZ" to "+213", "AS" to "+1684", "AD" to "+376", "AO" to "+244",
        "AR" to "+54", "AM" to "+374", "AU" to "+61", "AT" to "+43", "AZ" to "+994", "BH" to "+973",
        "BD" to "+880", "BY" to "+375", "BE" to "+32", "BZ" to "+501", "BJ" to "+229", "BT" to "+975",
        "BO" to "+591", "BA" to "+387", "BW" to "+267", "BR" to "+55", "BG" to "+359", "KH" to "+855",
        "CA" to "+1", "CL" to "+56", "CN" to "+86", "CO" to "+57", "CR" to "+506", "HR" to "+385",
        "CU" to "+53", "CY" to "+357", "CZ" to "+420", "DK" to "+45", "DO" to "+1809", "EC" to "+593",
        "EG" to "+20", "SV" to "+503", "EE" to "+372", "ET" to "+251", "FI" to "+358", "FR" to "+33",
        "DE" to "+49", "GH" to "+233", "GR" to "+30", "GT" to "+502", "HK" to "+852", "HU" to "+36",
        "IS" to "+354", "IN" to "+91", "ID" to "+62", "IR" to "+98", "IQ" to "+964", "IE" to "+353",
        "IL" to "+972", "IT" to "+39", "JM" to "+1876", "JP" to "+81", "JO" to "+962", "KZ" to "+7",
        "KE" to "+254", "KR" to "+82", "KW" to "+965", "LB" to "+961", "MY" to "+60", "MV" to "+960",
        "MX" to "+52", "NP" to "+977", "NL" to "+31", "NZ" to "+64", "NG" to "+234", "NO" to "+47",
        "OM" to "+968", "PK" to "+92", "PS" to "+970", "PA" to "+507", "PE" to "+51", "PH" to "+63",
        "PL" to "+48", "PT" to "+351", "QA" to "+974", "RO" to "+40", "RU" to "+7", "SA" to "+966",
        "SG" to "+65", "ZA" to "+27", "ES" to "+34", "LK" to "+94", "SE" to "+46", "CH" to "+41",
        "TW" to "+886", "TH" to "+66", "TR" to "+90", "UA" to "+380", "AE" to "+971", "GB" to "+44",
        "US" to "+1", "VN" to "+84"
    )

    fun getDeviceCountryCode(context: Context): String {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCountry = tm?.simCountryIso?.uppercase(Locale.US)
            if (!simCountry.isNullOrBlank() && countryDialCodeMap.containsKey(simCountry)) {
                return countryDialCodeMap[simCountry] ?: "+1"
            }
            val netCountry = tm?.networkCountryIso?.uppercase(Locale.US)
            if (!netCountry.isNullOrBlank() && countryDialCodeMap.containsKey(netCountry)) {
                return countryDialCodeMap[netCountry] ?: "+1"
            }
        } catch (_: Exception) {}
        val localeCountry = Locale.getDefault().country.uppercase(Locale.US)
        return countryDialCodeMap[localeCountry] ?: "+1"
    }

    fun getNormalizedInternationalNumber(rawNumber: String, defaultCountryCode: String): String {
        val clean = rawNumber.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (clean.isBlank()) return ""
        return if (clean.startsWith("+")) {
            clean
        } else if (clean.startsWith("0")) {
            "$defaultCountryCode${clean.removePrefix("0")}"
        } else {
            "$defaultCountryCode$clean"
        }
    }
}
