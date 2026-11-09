package com.apps.seleccionarpronunciar

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibración y sonido cortos para saber, sin mirar, que la app respondió. */
object Feedback {

    private val main = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null

    /** Se abrió el modo selección: listo para elegir qué leer. */
    fun selecting(context: Context) {
        vibrate(context, longArrayOf(0, 25))
        beep(context, ToneGenerator.TONE_PROP_BEEP, 90)
    }

    /** Se va a reconocer texto en la imagen (tarda un momento). */
    fun working(context: Context) = vibrate(context, longArrayOf(0, 15, 80, 15))

    /** Se detuvo la lectura o se canceló la selección. */
    fun stopped(context: Context) {
        vibrate(context, longArrayOf(0, 40))
        beep(context, ToneGenerator.TONE_PROP_NACK, 120)
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        if (!Prefs.feedbackVibrate(context)) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: RuntimeException) {
            // Sin vibrador o sin permiso: no es importante.
        }
    }

    private fun beep(context: Context, toneType: Int, durationMs: Int) {
        if (!Prefs.feedbackSound(context)) return
        try {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 45).also { tone = it }
            generator.startTone(toneType, durationMs)
        } catch (e: RuntimeException) {
            tone = null
        }
    }

    fun release() {
        main.post {
            tone?.release()
            tone = null
        }
    }
}
