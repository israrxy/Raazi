package com.israrxy.raazi

import android.app.Application
import androidx.room.Room
import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.repository.MusicRepository

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope

class RaaziApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "raazi-db"
        )
        .fallbackToDestructiveMigration()
        .build()
        container = AppContainer(database, applicationContext)

        // Initialize YouTube Visitor Data for PoToken
        MainScope().launch(Dispatchers.IO) {
            try {
                android.util.Log.d("RaaziApplication", "Initializing YouTube visitor data...")
                val visitorData = com.zionhuang.innertube.YouTube.visitorData().getOrNull()
                if (visitorData != null) {
                    com.zionhuang.innertube.YouTube.visitorData = visitorData
                    android.util.Log.d("RaaziApplication", "YouTube visitor data initialized: $visitorData")
                } else {
                    android.util.Log.w("RaaziApplication", "Failed to fetch YouTube visitor data")
                }
            } catch (e: Exception) {
                android.util.Log.e("RaaziApplication", "Error initializing YouTube visitor data", e)
            }
        }
    }

    companion object {
        lateinit var instance: RaaziApplication
            private set
    }
}

// Simple manual DI container
class AppContainer(private val database: AppDatabase, private val context: android.content.Context) {
    val musicRepository by lazy {
        MusicRepository(database, context)
    }
}
