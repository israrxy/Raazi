# ============================================================
# Raazi ProGuard / R8 Rules
# ============================================================

# Keep source file and line number info for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Kotlin Serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.israrxy.raazi.**$$serializer { *; }
-keepclassmembers class com.israrxy.raazi.** {
    *** Companion;
}
-keepclasseswithmembers class com.israrxy.raazi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Retrofit / OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---- Gson ----
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * { @androidx.room.* <methods>; }

# ---- Ktor ----
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# ---- NewPipe Extractor & Rhino ----
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }

# ---- InnerTube vendored code ----
-keep class com.zionhuang.innertube.** { *; }

# ---- Brotli ----
-keep class org.brotli.** { *; }

# ---- Coil ----
-dontwarn coil.**

# ---- AndroidX Media3 / ExoPlayer ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- WebView JavaScript interface (PoToken) ----
-keepclassmembers class com.israrxy.raazi.player.potoken.** {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- DataStore ----
-keep class androidx.datastore.** { *; }

# ---- WorkManager ----
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# ---- Credentials / Google Identity ----
-keep class com.google.android.libraries.identity.** { *; }
-keep class androidx.credentials.** { *; }

# ---- Keep data / model classes used in serialization ----
-keep class com.israrxy.raazi.model.** { *; }
-keep class com.israrxy.raazi.data.ai.SongIdentification { *; }
-keep class com.israrxy.raazi.data.ai.WebVerification { *; }
-keep class com.israrxy.raazi.data.ai.ImagePayload { *; }
-keep class com.israrxy.raazi.data.ai.AudioPayload { *; }
-keep class com.israrxy.raazi.data.account.GoogleDriveSyncPayload { *; }
-keep class com.israrxy.raazi.data.playlist.YouTubePlaylistMetadata { *; }

# ---- Biometric ----
-keep class androidx.biometric.** { *; }

# ---- Compose (R8 full mode compatibility) ----
-dontwarn androidx.compose.**
