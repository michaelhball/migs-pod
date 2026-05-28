package com.migspod

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.migspod.data.AppDatabase
import com.migspod.data.FeedRefreshWorker
import java.util.concurrent.TimeUnit

class MigsPodApp : Application(), ImageLoaderFactory {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.create(this)
        createNotificationChannel()
        scheduleFeedRefresh()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.3)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .crossfade(false)
            .build()
    }

    private fun scheduleFeedRefresh() {
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "feed_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Podcast playback controls"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "migspod_playback"
        lateinit var instance: MigsPodApp
            private set
    }
}
