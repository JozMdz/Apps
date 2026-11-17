package com.apps.estudioaudio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Voz del teléfono (texto a voz). Dice una frase cada vez y avisa al terminar.
 * Todos los métodos se llaman desde el hilo principal y los avisos llegan en él.
 */
class Narrator(
    context: Context,
    private val onFinished: (id: String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed = false
    private var pending: (() -> Unit)? = null
    private var finalUtterance: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                configure()
                pending?.invoke()
            } else {
                failed = true
                onUnavailable()
            }
            pending = null
        }
    }

    private fun configure() {
        // Contenido principal (como un audiolibro): sale por el audio multimedia del carro.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        })
    }

    private fun finished(utteranceId: String?) {
        utteranceId ?: return
        main.post {
            if (utteranceId == finalUtterance) {
                finalUtterance = null
                onFinished(utteranceId.substringBeforeLast('#'))
            }
        }
    }

    /** Dice [text] (cortando lo que estuviera diciendo) y deja [pauseAfterMs] de silencio al final. */
    fun speak(text: String, id: String, rate: Float, pauseAfterMs: Long = 0) {
        if (failed) {
            onUnavailable()
            return
        }
        if (!ready) {
            pending = { speak(text, id, rate, pauseAfterMs) }
            return
        }
        tts.setSpeechRate(rate)
        finalUtterance = if (pauseAfterMs > 0) "$id#p" else "$id#t"
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "$id#t")
        if (result == TextToSpeech.ERROR) {
            finished(finalUtterance)
            return
        }
        if (pauseAfterMs > 0 &&
            tts.playSilentUtterance(pauseAfterMs, TextToSpeech.QUEUE_ADD, "$id#p") == TextToSpeech.ERROR
        ) {
            finalUtterance = "$id#t" // sin pausa: se avanza al terminar la frase
        }
    }

    fun stop() {
        pending = null
        finalUtterance = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
