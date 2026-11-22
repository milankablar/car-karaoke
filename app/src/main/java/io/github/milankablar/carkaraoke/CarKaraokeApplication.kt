package io.github.milankablar.carkaraoke

import android.app.Application
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger

class CarKaraokeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.initialize(this)
        container = AppContainer(this)
    }
}
