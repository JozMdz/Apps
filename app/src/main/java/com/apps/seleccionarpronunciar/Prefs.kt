package com.apps.seleccionarpronunciar

import android.content.Context
import android.content.SharedPreferences

/** Ajustes persistentes de la app. */
object Prefs {
    const val KEY_RATE = "speech_rate"
    const val KEY_PITCH = "speech_pitch"
    const val KEY_HIGHLIGHT = "highlight"
    const val KEY_FLOATING_BUTTON = "floating_button"
    const val KEY_TILE_ACTION = "tile_action"
    const val TILE_SELECT = "select"
    const val TILE_READ_ALL = "read_all"
    private const val KEY_BUTTON_X = "button_x"
    private const val KEY_BUTTON_Y = "button_y"

    val RATE_STEPS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun rate(context: Context): Float = of(context).getFloat(KEY_RATE, 1.0f)
    fun setRate(context: Context, value: Float) = of(context).edit().putFloat(KEY_RATE, value).apply()

    fun pitch(context: Context): Float = of(context).getFloat(KEY_PITCH, 1.0f)
    fun setPitch(context: Context, value: Float) = of(context).edit().putFloat(KEY_PITCH, value).apply()

    fun highlight(context: Context): Boolean = of(context).getBoolean(KEY_HIGHLIGHT, true)
    fun setHighlight(context: Context, value: Boolean) =
        of(context).edit().putBoolean(KEY_HIGHLIGHT, value).apply()

    fun floatingButton(context: Context): Boolean = of(context).getBoolean(KEY_FLOATING_BUTTON, true)
    fun setFloatingButton(context: Context, value: Boolean) =
        of(context).edit().putBoolean(KEY_FLOATING_BUTTON, value).apply()

    fun tileAction(context: Context): String = of(context).getString(KEY_TILE_ACTION, TILE_SELECT) ?: TILE_SELECT
    fun setTileAction(context: Context, value: String) =
        of(context).edit().putString(KEY_TILE_ACTION, value).apply()

    fun buttonPosition(context: Context, defX: Int, defY: Int): Pair<Int, Int> {
        val p = of(context)
        return p.getInt(KEY_BUTTON_X, defX) to p.getInt(KEY_BUTTON_Y, defY)
    }

    fun setButtonPosition(context: Context, x: Int, y: Int) =
        of(context).edit().putInt(KEY_BUTTON_X, x).putInt(KEY_BUTTON_Y, y).apply()

    /** Siguiente velocidad del ciclo rápido (botón de velocidad de la barra de control). */
    fun nextRateStep(current: Float): Float =
        RATE_STEPS.firstOrNull { it > current + 0.01f } ?: RATE_STEPS.first()
}
