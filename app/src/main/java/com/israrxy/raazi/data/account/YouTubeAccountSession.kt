package com.israrxy.raazi.data.account

import android.util.Log
import com.israrxy.raazi.data.local.SettingsDataStore
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AccountInfo
import kotlinx.coroutines.flow.first

object YouTubeAccountSession {
    private const val TAG = "YouTubeAccountSession"

    suspend fun bootstrap(settingsDataStore: SettingsDataStore) {
        val cookie = settingsDataStore.innerTubeCookie.first().nullIfBlank()
        val visitorData = settingsDataStore.visitorData.first().nullIfBlank()
        val dataSyncId = normalizeDataSyncId(settingsDataStore.dataSyncId.first())
        val useLoginForBrowse = settingsDataStore.useLoginForBrowse.first()

        YouTube.cookie = cookie
        YouTube.dataSyncId = dataSyncId
        YouTube.useLoginForBrowse = useLoginForBrowse

        if (visitorData != null) {
            YouTube.visitorData = visitorData
            return
        }

        try {
            val freshVisitorData = YouTube.visitorData().getOrNull()
            if (!freshVisitorData.isNullOrBlank()) {
                YouTube.visitorData = freshVisitorData
                settingsDataStore.setVisitorData(freshVisitorData)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to bootstrap visitor data", error)
        }
    }

    suspend fun persistSession(
        settingsDataStore: SettingsDataStore,
        cookie: String,
        visitorData: String,
        dataSyncId: String?,
        accountInfo: AccountInfo?
    ) {
        val normalizedDataSyncId = normalizeDataSyncId(dataSyncId)
        settingsDataStore.saveYouTubeSession(cookie, visitorData, normalizedDataSyncId)
        settingsDataStore.saveYouTubeAccountInfo(
            name = accountInfo?.name,
            email = accountInfo?.email,
            channelHandle = accountInfo?.channelHandle
        )

        YouTube.cookie = cookie.nullIfBlank()
        YouTube.visitorData = visitorData.nullIfBlank()
        YouTube.dataSyncId = normalizedDataSyncId
    }

    suspend fun clear(settingsDataStore: SettingsDataStore) {
        settingsDataStore.clearYouTubeAccount()
        YouTube.cookie = null
        YouTube.dataSyncId = null
        YouTube.visitorData = null
        bootstrap(settingsDataStore)
    }

    suspend fun setUseLoginForBrowse(
        settingsDataStore: SettingsDataStore,
        enabled: Boolean
    ) {
        settingsDataStore.setUseLoginForBrowse(enabled)
        YouTube.useLoginForBrowse = enabled
    }

    fun normalizeDataSyncId(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        return when {
            !value.contains("||") -> value
            value.endsWith("||") -> value.substringBefore("||")
            else -> value.substringAfter("||")
        }.nullIfBlank()
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() && it != "null" }
}

