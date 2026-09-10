package io.github.milankablar.carkaraoke

import android.app.Application
import io.github.milankablar.carkaraoke.diagnostics.DiagnosticLogger

class CarKaraokeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.initialize(this)
        io.github.milankablar.carkaraoke.karaoke.KaraokeRuntime.initialize(this)
    }
}
