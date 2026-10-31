package com.israrxy.raazi.model

import com.google.gson.annotations.SerializedName

data class UpdateConfig(
    @SerializedName("latestVersionCode") val latestVersionCode: Int,
    @SerializedName("latestVersionName") val latestVersionName: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("forceUpdate") val forceUpdate: Boolean,
    @SerializedName("releaseNotes") val releaseNotes: String
)
