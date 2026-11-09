package com.apps.seleccionarpronunciar

import android.content.Context
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/** Un fragmento a pronunciar; [bounds] (coordenadas de pantalla) se usa para resaltarlo. */
class Segment(val text: String, val bounds: Rect? = null)

/**
 * Motor de voz compartido por el servicio de accesibilidad y la actividad "Pronunciar".
 * Divide el texto en frases para poder pausar, reanudar y saltar adelante/atrás.
 * Todos los métodos deben llamarse desde el hilo principal.
 */
object Speaker {

    enum class State { IDLE, SPEAKING, PAUSED }

    fun interface Listener {
        fun onSpeakerChanged(state: State, segment: Segment?)
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    private var appContext: Context? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingStart = false

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var pausedByFocusLoss = false

    /** Como las indicaciones del GPS: el sistema y el carro las mezclan sobre la música. */
    private val speechAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var segments: List<Segment> = emptyList()
    private var index = 0
    private var currentUtteranceId: String? = null
    private var generation = 0

    var state = State.IDLE
        private set

    val currentSegment: Segment?
        get() = segments.getOrNull(index)

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Prepara el motor TTS (es asíncrono; se puede llamar varias veces). */
    fun init(context: Context) {
        if (tts != null) return
        val ctx = context.applicationContext
        appContext = ctx
        audioManager = ctx.getSystemService(AudioManager::class.java)
        ready = false
        tts = TextToSpeech(ctx) { status ->
            main.post { onTtsInit(status) }
        }
    }

    private fun onTtsInit(status: Int) {
        val engine = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            engine.setAudioAttributes(speechAttributes)
            engine.setOnUtteranceProgressListener(progressListener)
            if (pendingStart) {
                pendingStart = false
                speakCurrent()
            }
        } else {
            appContext?.let {
                Toast.makeText(it, R.string.tts_unavailable, Toast.LENGTH_LONG).show()
            }
            engine.shutdown()
            tts = null
            abandonFocus()
            pendingStart = false
            segments = emptyList()
            setState(State.IDLE)
        }
    }

    fun speakText(context: Context, text: String) = speak(context, listOf(Segment(text)))

    fun speak(context: Context, items: List<Segment>) {
        init(context)
        cancelCurrent()
        segments = items.flatMap { splitSegment(it) }
        index = 0
        if (segments.isEmpty()) {
            setState(State.IDLE)
            return
        }
        state = State.SPEAKING
        requestFocus()
        if (ready) speakCurrent() else {
            pendingStart = true
            notifyListeners()
        }
    }

    fun pause() {
        if (state != State.SPEAKING) return
        pausedByFocusLoss = false
        cancelCurrent()
        abandonFocus()
        setState(State.PAUSED)
    }

    fun resume() {
        if (state != State.PAUSED) return
        pausedByFocusLoss = false
        state = State.SPEAKING
        requestFocus()
        if (ready) speakCurrent() else pendingStart = true
    }

    fun togglePause() {
        when (state) {
            State.SPEAKING -> pause()
            State.PAUSED -> resume()
            State.IDLE -> Unit
        }
    }

    fun next() = jumpTo(index + 1)

    fun previous() = jumpTo(index - 1)

    private fun jumpTo(target: Int) {
        if (state == State.IDLE || segments.isEmpty()) return
        index = target.coerceIn(0, segments.lastIndex)
        if (state == State.SPEAKING && ready) speakCurrent() else notifyListeners()
    }

    /** Vuelve a decir la frase actual (p. ej. tras cambiar la velocidad). */
    fun restartCurrent() {
        if (state == State.SPEAKING && ready) speakCurrent()
    }

    fun stop() {
        cancelCurrent()
        abandonFocus()
        pausedByFocusLoss = false
        pendingStart = false
        segments = emptyList()
        index = 0
        setState(State.IDLE)
    }

    /** Libera el motor TTS. */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    // ---------------------------------------------------------------- foco de audio

    /** Pide el audio para que la música (Spotify, etc.) baje el volumen o se pause mientras se lee. */
    private fun requestFocus() {
        val am = audioManager ?: return
        val ctx = appContext ?: return
        val gain = when (Prefs.musicMode(ctx)) {
            Prefs.MUSIC_PAUSE -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            Prefs.MUSIC_DUCK -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            else -> return
        }
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
        focusRequest = request
        am.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        audioManager?.abandonAudioFocusRequest(request)
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // Una llamada u otra app necesita el audio: pausamos y seguimos al recuperarlo.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (state == State.SPEAKING) {
                cancelCurrent()
                pausedByFocusLoss = true
                setState(State.PAUSED)
            }
            AudioManager.AUDIOFOCUS_LOSS -> if (state == State.SPEAKING) pause()
            AudioManager.AUDIOFOCUS_GAIN -> if (state == State.PAUSED && pausedByFocusLoss) {
                pausedByFocusLoss = false
                state = State.SPEAKING
                if (ready) speakCurrent() else pendingStart = true
            }
        }
    }

    private fun cancelCurrent() {
        generation++
        currentUtteranceId = null
        if (ready) tts?.stop()
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        val segment = segments.getOrNull(index)
        if (segment == null) {
            stop()
            return
        }
        val ctx = appContext
        if (ctx != null) {
            engine.setSpeechRate(Prefs.rate(ctx))
            engine.setPitch(Prefs.pitch(ctx))
        }
        generation++
        val id = "utt-$generation-$index"
        currentUtteranceId = id
        notifyListeners()
        val result = engine.speak(segment.text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (result == TextToSpeech.ERROR) {
            main.post { onUtteranceFinished(id) }
        }
    }

    private fun onUtteranceFinished(id: String) {
        if (id != currentUtteranceId || state != State.SPEAKING) return
        if (index < segments.lastIndex) {
            index++
            speakCurrent()
        } else {
            stop()
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            utteranceId ?: return
            main.post { onUtteranceFinished(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            utteranceId ?: return
            main.post { onUtteranceFinished(utteranceId) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId ?: return
            main.post { onUtteranceFinished(utteranceId) }
        }
    }

    private fun setState(newState: State) {
        state = newState
        notifyListeners()
    }

    private fun notifyListeners() {
        val segment = if (state == State.IDLE) null else currentSegment
        for (l in listeners) l.onSpeakerChanged(state, segment)
    }

    /** Divide un bloque de texto en frases (y éstas en trozos si superan el máximo del motor). */
    private fun splitSegment(segment: Segment): List<Segment> {
        val text = segment.text.trim()
        if (text.isEmpty()) return emptyList()
        val maxLen = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(200)
        val out = ArrayList<Segment>()
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        iterator.setText(text)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) {
                for (chunk in chunk(sentence, maxLen)) out.add(Segment(chunk, segment.bounds))
            }
            start = end
            end = iterator.next()
        }
        return out
    }

    private fun chunk(sentence: String, maxLen: Int): List<String> {
        if (sentence.length <= maxLen) return listOf(sentence)
        val parts = ArrayList<String>()
        var rest = sentence
        while (rest.length > maxLen) {
            var cut = rest.lastIndexOf(' ', maxLen)
            if (cut <= 0) cut = maxLen
            parts.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts.add(rest)
        return parts
    }
}
