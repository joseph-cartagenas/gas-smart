package com.smartgas.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point annotated with [@HiltAndroidApp] to trigger Hilt's
 * component generation and inject the [HiltWorkerFactory] required for
 * WorkManager to create workers with Hilt dependencies.
 */
@HiltAndroidApp
class SmartGasApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
