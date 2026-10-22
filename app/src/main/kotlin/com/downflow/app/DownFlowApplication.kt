package com.downflow.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DownFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hilt initialises the dependency graph here.
        // Additional one-time setup (notification channels, etc.) is done
        // lazily inside the injected NotificationHelper to keep this class thin.
    }
}
