package com.commerceos.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.commerceos.android.di.AppContainer
import com.commerceos.android.notifications.NotificationChannelHelper
import com.google.android.gms.maps.MapsInitializer

/**
 * Application-level composition root. The dependency graph is owned here, never
 * by an Activity, so process-scoped collaborators (session, repository, network)
 * survive Activity recreation.
 *
 * Implements [ImageLoaderFactory] to configure a high-performance centralized Coil
 * image pipeline with a 25% memory pool and 60MB disk cache for silky 120 FPS scrolling.
 */
class CommerceOSApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        try {
            NotificationChannelHelper.createChannels(this)
        } catch (_: Exception) {}
        try {
            MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, null)
        } catch (_: Exception) {}
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(60L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
