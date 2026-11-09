package com.apps.seleccionarpronunciar

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda el último error para poder verlo en la pantalla de la app (el teléfono no muestra
 * por qué se cerró un servicio de accesibilidad).
 */
object CrashLog {
    private const val KEY = "last_error"
    private const val TAG = "Pronunciar"
    private var installed = false

    fun record(context: Context, error: Throwable, fatal: Boolean = false) {
        Log.e(TAG, "Error", error)
        val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
        val text = buildString {
            append(time)
            append(if (fatal) " (la app se cerró)\n" else "\n")
            append(Log.getStackTraceString(error).take(2500))
        }
        // commit(): debe quedar guardado aunque el proceso termine justo después.
        Prefs.of(context).edit().putString(KEY, text).commit()
    }

    fun last(context: Context): String? = Prefs.of(context).getString(KEY, null)

    fun clear(context: Context) = Prefs.of(context).edit().remove(KEY).apply()

    /** Registra también los errores no controlados que cierran la app. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(app, error, fatal = true)
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
