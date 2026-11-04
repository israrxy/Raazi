package com.israrxy.raazi

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.data.repository.MusicRepository

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first

class RaaziApplication : Application(), ImageLoaderFactory {
    lateinit var database: AppDatabase
    lateinit var container: AppContainer

    val settingsDataStore by lazy { SettingsDataStore(this) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB ceiling for artwork
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = try {
            Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "raazi-db"
            )
            .addMigrations(
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16
            )
            .fallbackToDestructiveMigration()
            .build()
        } catch (e: Exception) {
            android.util.Log.e("RaaziApp", "Database creation failed, using destructive fallback", e)
            Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "raazi-db"
            )
            .fallbackToDestructiveMigration()
            .build()
        }
        container = AppContainer(database, applicationContext)

        bluetoothDeviceManager = com.israrxy.raazi.data.local.BluetoothDeviceManager(applicationContext)

        MainScope().launch(Dispatchers.IO) {
            try {
                bluetoothDeviceManager.start()
            } catch (e: Exception) {
                android.util.Log.e("RaaziApp", "Bluetooth device manager start failed", e)
            }
            try {
                com.israrxy.raazi.service.YouTubeMusicExtractor.getInstance()
            } catch (e: Exception) {
                android.util.Log.e("RaaziApp", "NewPipe extractor init failed", e)
            }
            try {
                YouTubeAccountSession.bootstrap(SettingsDataStore(applicationContext))
            } catch (e: Exception) {
                android.util.Log.e("RaaziApp", "YouTube account bootstrap failed", e)
            }
            try {
                val settings = settingsDataStore
                val hifiUrl = settings.hifiApiUrl.first()
                if (!hifiUrl.isNullOrBlank()) {
                    com.israrxy.raazi.player.flac.HifiApiClient.customInstanceUrl = hifiUrl
                }
                if (!settings.raaziUserEmail.first().isNullOrBlank()) {
                    com.israrxy.raazi.data.account.RaaziDriveSyncWorker.triggerImmediate(applicationContext)
                }
            } catch (e: Exception) {
                android.util.Log.e("RaaziApp", "HifiApi bootstrap failed", e)
            }
        }
    }

    companion object {
        lateinit var instance: RaaziApplication
            private set
    }

    lateinit var bluetoothDeviceManager: com.israrxy.raazi.data.local.BluetoothDeviceManager
        private set
}

// Simple manual DI container
class AppContainer(private val database: AppDatabase, private val context: android.content.Context) {
    val musicRepository by lazy {
        MusicRepository(database, context)
    }
}
