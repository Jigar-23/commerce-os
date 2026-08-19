package com.commerceos.android

import android.app.Application
import com.commerceos.android.di.AppContainer

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
    }
}
