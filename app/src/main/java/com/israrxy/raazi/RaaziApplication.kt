package com.israrxy.raazi

import android.app.Application
import androidx.room.Room
import com.israrxy.raazi.data.account.YouTubeAccountSession
import com.israrxy.raazi.data.db.AppDatabase
import com.israrxy.raazi.data.local.SettingsDataStore
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
        database = try {
            Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "raazi-db"
            )
            .addMigrations(AppDatabase.MIGRATION_10_11)
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

        MainScope().launch(Dispatchers.IO) {
            try {
                YouTubeAccountSession.bootstrap(SettingsDataStore(applicationContext))
            } catch (e: Exception) {
                android.util.Log.e("RaaziApp", "YouTube account bootstrap failed", e)
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
