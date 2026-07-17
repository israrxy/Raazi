plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.israrxy.raazi"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.israrxy.raazi"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "5.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // NewPipe Extractor - Real YouTube Music extraction
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.2")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Media playback
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")
    implementation("androidx.media3:media3-session:1.6.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.6.0")
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Biometric authentication (app lock)
    implementation("androidx.biometric:biometric:1.1.0")

    // Extended Icons
    implementation("androidx.compose.material:material-icons-extended:1.5.4")

    // Google AI Client SDK (Gemini)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // Android Credential Manager
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Palette API for dynamic colors
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Legacy Media for NotificationCompat.MediaStyle
    implementation("androidx.media:media:1.7.0")
    coreLibraryDesugaring(libs.desugaring)
    
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    // OuterTune InnerTube Module
    implementation(project(":innertube"))
}
