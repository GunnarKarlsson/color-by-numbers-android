package network.bahn.colorbynumber.android

import android.app.Application

class ColorByNumberApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PuzzleCatalog.initialize(this)
    }
}
