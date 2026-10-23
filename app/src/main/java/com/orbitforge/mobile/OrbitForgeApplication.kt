package com.orbitforge.mobile

import android.app.Application
import android.os.Process
import com.orbitforge.mobile.diagnostics.DiagnosticLog
import kotlin.system.exitProcess

/** Records a compact crash trace before delegating to Android's default crash handler. */
class OrbitForgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try { DiagnosticLog.recordCrash(applicationContext, throwable) } catch (_: Exception) { }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
