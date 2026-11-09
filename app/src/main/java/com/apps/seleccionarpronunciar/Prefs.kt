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
    const val KEY_OCR_MODE = "ocr_mode"
    const val KEY_TAP_MODE = "tap_mode"
    const val TAP_SENTENCE = "sentence"
    const val TAP_FROM = "from"
    const val TAP_ELEMENT = "element"
    const val KEY_DRAG_ROWS = "drag_rows"
    const val KEY_MUSIC_MODE = "music_mode"
    const val MUSIC_DUCK = "duck"
    const val MUSIC_PAUSE = "pause"
    const val MUSIC_NONE = "none"
    const val KEY_CAR_AUTO = "car_auto"
    const val KEY_CAR_DEVICES = "car_devices"
    const val OCR_AUTO = "auto"
    const val OCR_ALWAYS = "always"
    const val OCR_NEVER = "never"
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

    fun ocrMode(context: Context): String = of(context).getString(KEY_OCR_MODE, OCR_AUTO) ?: OCR_AUTO
    fun setOcrMode(context: Context, value: String) = of(context).edit().putString(KEY_OCR_MODE, value).apply()

    fun tapMode(context: Context): String = of(context).getString(KEY_TAP_MODE, TAP_FROM) ?: TAP_FROM
    fun setTapMode(context: Context, value: String) = of(context).edit().putString(KEY_TAP_MODE, value).apply()

    fun dragRows(context: Context): Boolean = of(context).getBoolean(KEY_DRAG_ROWS, true)
    fun setDragRows(context: Context, value: Boolean) = of(context).edit().putBoolean(KEY_DRAG_ROWS, value).apply()

    fun musicMode(context: Context): String = of(context).getString(KEY_MUSIC_MODE, MUSIC_DUCK) ?: MUSIC_DUCK
    fun setMusicMode(context: Context, value: String) = of(context).edit().putString(KEY_MUSIC_MODE, value).apply()

    /** Mostrar el botón solo mientras el teléfono está conectado al Bluetooth del carro. */
    fun carAuto(context: Context): Boolean = of(context).getBoolean(KEY_CAR_AUTO, false)
    fun setCarAuto(context: Context, value: Boolean) = of(context).edit().putBoolean(KEY_CAR_AUTO, value).apply()

    /** Direcciones Bluetooth de los dispositivos del carro. */
    fun carDevices(context: Context): Set<String> = of(context).getStringSet(KEY_CAR_DEVICES, emptySet()) ?: emptySet()
    fun setCarDevices(context: Context, value: Set<String>) =
        of(context).edit().putStringSet(KEY_CAR_DEVICES, HashSet(value)).apply()

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
