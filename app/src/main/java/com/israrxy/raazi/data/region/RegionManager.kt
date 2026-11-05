package com.israrxy.raazi.data.region

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import com.israrxy.raazi.data.local.SettingsDataStore
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeLocale
import kotlinx.coroutines.flow.first
import java.util.Locale

data class RegionInfo(
    val code: String,
    val name: String,
    val flagEmoji: String = ""
)

object RegionManager {
    private const val TAG = "RegionManager"
    private const val DEFAULT_FALLBACK_COUNTRY = "US"
    private const val DEFAULT_FALLBACK_LANGUAGE = "en-US"

    val SUPPORTED_REGIONS = listOf(
        RegionInfo("AUTO", "Auto-detect", "🌐"),
        RegionInfo("PK", "Pakistan", "🇵🇰"),
        RegionInfo("IN", "India", "🇮🇳"),
        RegionInfo("US", "United States", "🇺🇸"),
        RegionInfo("GB", "United Kingdom", "🇬🇧"),
        RegionInfo("CA", "Canada", "🇨🇦"),
        RegionInfo("AU", "Australia", "🇦🇺"),
        RegionInfo("DE", "Germany", "🇩🇪"),
        RegionInfo("FR", "France", "🇫🇷"),
        RegionInfo("BR", "Brazil", "🇧🇷"),
        RegionInfo("ID", "Indonesia", "🇮🇩"),
        RegionInfo("JP", "Japan", "🇯🇵"),
        RegionInfo("KR", "South Korea", "🇰🇷"),
        RegionInfo("TR", "Turkey", "🇹🇷"),
        RegionInfo("SA", "Saudi Arabia", "🇸🇦"),
        RegionInfo("AE", "United Arab Emirates", "🇦🇪"),
        RegionInfo("BD", "Bangladesh", "🇧🇩"),
        RegionInfo("NG", "Nigeria", "🇳🇬"),
        RegionInfo("EG", "Egypt", "🇪🇬"),
        RegionInfo("MX", "Mexico", "🇲🇽"),
        RegionInfo("ES", "Spain", "🇪🇸"),
        RegionInfo("IT", "Italy", "🇮🇹"),
        RegionInfo("RU", "Russia", "🇷🇺"),
        RegionInfo("ZA", "South Africa", "🇿🇦")
    )

    @Volatile
    private var activeCountryCode: String = DEFAULT_FALLBACK_COUNTRY

    @Volatile
    private var activeLanguageCode: String = DEFAULT_FALLBACK_LANGUAGE

    fun getCountryCode(): String = activeCountryCode
    fun getLanguageCode(): String = activeLanguageCode

    fun getRegionName(code: String): String {
        return SUPPORTED_REGIONS.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name
            ?: Locale("", code).displayCountry.ifBlank { code }
    }

    /**
     * Initializes the region from Settings or hardware/locale detection.
     * Must be called during Application.onCreate before any InnerTube requests.
     */
    suspend fun init(context: Context, settingsDataStore: SettingsDataStore) {
        val configuredCountry = try {
            settingsDataStore.contentCountry.first()
        } catch (_: Exception) {
            ""
        }
        val configuredLanguage = try {
            settingsDataStore.contentLanguage.first()
        } catch (_: Exception) {
            ""
        }

        val resolvedCountry = if (configuredCountry.isNotBlank() && !configuredCountry.equals("AUTO", ignoreCase = true)) {
            configuredCountry.uppercase()
        } else {
            detectHardwareCountry(context)
        }

        val resolvedLanguage = if (configuredLanguage.isNotBlank() && !configuredLanguage.equals("AUTO", ignoreCase = true)) {
            configuredLanguage
        } else {
            detectSystemLanguage()
        }

        applyLocale(resolvedCountry, resolvedLanguage)
        Log.i(TAG, "Initialized region: country=$resolvedCountry, language=$resolvedLanguage (configured=$configuredCountry)")
    }

    fun applyLocale(country: String, language: String) {
        val cleanCountry = country.trim().uppercase().takeIf { it.length == 2 } ?: DEFAULT_FALLBACK_COUNTRY
        val cleanLanguage = language.trim().ifBlank { DEFAULT_FALLBACK_LANGUAGE }

        activeCountryCode = cleanCountry
        activeLanguageCode = cleanLanguage

        YouTube.locale = YouTubeLocale(
            gl = cleanCountry,
            hl = cleanLanguage
        )
        Log.d(TAG, "Applied YouTube locale: gl=$cleanCountry, hl=$cleanLanguage")
    }

    fun detectHardwareCountry(context: Context): String {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                // 1. Try SIM country first (most reliable for cellular devices)
                val simCountry = tm.simCountryIso?.trim()?.uppercase()
                if (!simCountry.isNullOrBlank() && simCountry.length == 2) {
                    return simCountry
                }

                // 2. Try network country (connected cellular tower)
                val networkCountry = tm.networkCountryIso?.trim()?.uppercase()
                if (!networkCountry.isNullOrBlank() && networkCountry.length == 2) {
                    return networkCountry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking TelephonyManager: ${e.message}")
        }

        // 3. Try context configuration locale
        try {
            val locale = context.resources.configuration.locales.get(0)
            val country = locale?.country?.trim()?.uppercase()
            if (!country.isNullOrBlank() && country.length == 2) {
                return country
            }
        } catch (_: Exception) {}

        // 4. Try default JVM locale
        val defaultCountry = Locale.getDefault().country?.trim()?.uppercase()
        if (!defaultCountry.isNullOrBlank() && defaultCountry.length == 2) {
            return defaultCountry
        }

        return DEFAULT_FALLBACK_COUNTRY
    }

    fun detectSystemLanguage(): String {
        return try {
            val tag = Locale.getDefault().toLanguageTag()
            if (tag.isNotBlank()) tag else DEFAULT_FALLBACK_LANGUAGE
        } catch (_: Exception) {
            DEFAULT_FALLBACK_LANGUAGE
        }
    }
}
