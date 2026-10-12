package com.novadesk

import android.app.Application
import com.novadesk.core.storage.AppContainer

/**
 * Process-wide entry point.
 *
 * We deliberately keep a hand-written service locator ([AppContainer])
 * instead of a DI framework: NovaDesk has to boot on low-end devices and
 * every dependency is cheaper to reason about when it is constructed here.
 */
class NovaDeskApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
