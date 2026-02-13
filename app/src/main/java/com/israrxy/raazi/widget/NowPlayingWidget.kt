package com.israrxy.raazi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.israrxy.raazi.MainActivity
import com.israrxy.raazi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Now Playing Widget for home screen
 * Standard feature in 2026 music apps
 */
class NowPlayingWidget : AppWidgetProvider() {
    
    companion object {
        const val ACTION_PLAY_PAUSE = "com.israrxy.raazi.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.israrxy.raazi.widget.NEXT"
        const val ACTION_PREVIOUS = "com.israrxy.raazi.widget.PREVIOUS"
        
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        fun updateWidget(
            context: Context,
            title: String?,
            artist: String?,
            thumbnailUrl: String?,
            isPlaying: Boolean
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NowPlayingWidget::class.java)
            )
            
            widgetIds.forEach { widgetId ->
                updateAppWidget(context, appWidgetManager, widgetId, title, artist, thumbnailUrl, isPlaying)
            }
        }
        
        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            title: String?,
            artist: String?,
            thumbnailUrl: String?,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            
            // Set text
            views.setTextViewText(R.id.widget_title, title ?: "Not Playing")
            views.setTextViewText(R.id.widget_artist, artist ?: "")
            
            // Set play/pause icon
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            
            // Click intents
            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                getPendingIntent(context, ACTION_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                getPendingIntent(context, ACTION_NEXT)
            )
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                getPendingIntent(context, ACTION_PREVIOUS)
            )
            
            // Open app on widget click
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)
            
            // Load thumbnail async
            if (!thumbnailUrl.isNullOrEmpty()) {
                scope.launch {
                    try {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .size(128, 128)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                views.setImageViewBitmap(R.id.widget_thumbnail, bitmap)
                                appWidgetManager.updateAppWidget(appWidgetId, views)
                            }
                        }
                    } catch (e: Exception) {
                        // Fallback to default image
                    }
                }
            }
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, NowPlayingWidget::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateAppWidget(context, appWidgetManager, widgetId, null, null, null, false)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_PLAY_PAUSE -> sendControlIntent(context, "PLAY_PAUSE")
            ACTION_NEXT -> sendControlIntent(context, "NEXT")
            ACTION_PREVIOUS -> sendControlIntent(context, "PREVIOUS")
        }
    }
    
    private fun sendControlIntent(context: Context, action: String) {
        val serviceIntent = Intent(context, com.israrxy.raazi.service.MusicPlaybackService::class.java).apply {
            this.action = action
        }
        context.startService(serviceIntent)
    }
}
