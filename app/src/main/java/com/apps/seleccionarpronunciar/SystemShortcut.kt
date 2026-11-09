package com.apps.seleccionarpronunciar

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Detecta si el botón de accesibilidad del sistema (botón flotante de Android) está asignado a esta app. */
object SystemShortcut {

    /** Lista de servicios asignados al botón de accesibilidad (Android 11+). */
    const val BUTTON_TARGETS_KEY = "accessibility_button_targets"

    fun isAssigned(context: Context): Boolean {
        val value = try {
            Settings.Secure.getString(context.contentResolver, BUTTON_TARGETS_KEY)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return false
        val ours = ComponentName(context, SelectToSpeakService::class.java)
        return value.split(':').any { ComponentName.unflattenFromString(it.trim()) == ours }
    }
}
