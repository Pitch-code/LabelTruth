package com.labellens.app

import android.app.Application
import com.labellens.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LabelLensApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Import the bundled dictionary off the main thread so first launch
        // never blocks the camera coming up.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.repository.ensureDictionaryReady() }
        }
    }
}
