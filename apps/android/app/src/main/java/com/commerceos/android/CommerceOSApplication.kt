package com.commerceos.android

import android.app.Application
import com.commerceos.android.di.AppContainer
import com.commerceos.android.notifications.NotificationChannelHelper
import com.google.android.gms.maps.MapsInitializer

/**
 * Application-level composition root. The dependency graph is owned here, never
 * by an Activity, so process-scoped collaborators (session, repository, network)
 * survive Activity recreation.
 */
class CommerceOSApplication : Application() {
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
}
