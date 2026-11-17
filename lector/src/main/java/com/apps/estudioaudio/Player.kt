package com.apps.estudioaudio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Reproductor: lleva el documento abierto, la frase actual y el estado (leyendo / en pausa).
 * Lo usan la pantalla, el aviso multimedia y los botones del carro. Solo hilo principal.
 */
object Player {

    enum class State { IDLE, PLAYING, PAUSED }

    fun interface Listener {
        fun onPlayerChanged()
    }

    val RATES = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private var app: Context? = null
    private var narrator: Narrator? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocus = false
    private var generation = 0
    private var currentId: String? = null

    var doc: Doc? = null
        private set
    var index = 0
        private set
    var state = State.IDLE
        private set
    var rate = 1.0f
        private set

    val section: Int get() = doc?.sectionAt(index) ?: 0

    fun addListener(listener: Listener) = listeners.add(listener)
    fun removeListener(listener: Listener) = listeners.remove(listener)

    /** Abre [document] en la frase [startAt]. */
    fun open(context: Context, document: Doc, startAt: Int, autoPlay: Boolean) {
        val ctx = context.applicationContext
        app = ctx
        audioManager = ctx.getSystemService(AudioManager::class.java)
        rate = Library.rate(ctx)
        cancelSpeech()
        doc = document
        // Si se había terminado, se empieza de nuevo.
        index = if (startAt in 0 until document.sentences.size - 1) startAt else 0
        state = State.PAUSED
        Library.setLastDoc(ctx, document.id)
        if (narrator == null) narrator = Narrator(ctx, ::onSentenceFinished, ::onVoiceUnavailable)
        startService(ctx)
        if (autoPlay) play() else notifyListeners()
    }

    private fun startService(ctx: Context) {
        try {
            ctx.startForegroundService(Intent(ctx, PlaybackService::class.java))
        } catch (e: Exception) {
            // Sin servicio se puede leer igual mientras la app esté abierta.
        }
    }

    fun play() {
        if (doc == null || state == State.PLAYING) return
        if (!requestFocus()) return
        resumeOnFocus = false
        state = State.PLAYING
        claimMediaButtons()
        speakCurrent(announceSection = false)
    }

    fun pause() {
        if (state != State.PLAYING) return
        resumeOnFocus = false
        cancelSpeech()
        state = State.PAUSED
        abandonFocus()
        save()
        notifyListeners()
    }

    fun toggle() = if (state == State.PLAYING) pause() else play()

    fun nextSentence() = moveTo(index + 1, announceSection = false)

    fun previousSentence() = moveTo(index - 1, announceSection = false)

    fun nextSection() {
        val d = doc ?: return
        val target = d.sections.getOrNull(section + 1) ?: return
        moveTo(target.start, announceSection = true)
    }

    /** Vuelve al inicio del apartado; si ya está al inicio, va al apartado anterior. */
    fun previousSection() {
        val d = doc ?: return
        val current = d.sections.getOrNull(section) ?: return
        val target = if (index > current.start + 1) current else d.sections.getOrNull(section - 1) ?: current
        moveTo(target.start, announceSection = true)
    }

    /** Salta al apartado [sectionIndex] del índice y empieza a leerlo. */
    fun jumpToSection(sectionIndex: Int) {
        val d = doc ?: return
        val target = d.sections.getOrNull(sectionIndex) ?: return
        if (state != State.PLAYING) {
            if (!requestFocus()) return
            state = State.PLAYING
            claimMediaButtons()
        }
        moveTo(target.start, announceSection = true)
    }

    private fun moveTo(target: Int, announceSection: Boolean) {
        val d = doc ?: return
        index = target.coerceIn(0, d.sentences.lastIndex)
        save()
        if (state == State.PLAYING) speakCurrent(announceSection) else notifyListeners()
    }

    fun cycleRate() {
        val ctx = app ?: return
        rate = RATES.firstOrNull { it > rate + 0.01f } ?: RATES.first()
        Library.setRate(ctx, rate)
        if (state == State.PLAYING) speakCurrent(announceSection = false) else notifyListeners()
    }

    /** Cierra el documento y libera la voz. */
    fun close() {
        if (state == State.IDLE && doc == null) return
        cancelSpeech()
        save()
        abandonFocus()
        state = State.IDLE
        doc = null
        narrator?.shutdown()
        narrator = null
        notifyListeners()
    }

    // ---------------------------------------------------------------- lectura

    private fun speakCurrent(announceSection: Boolean) {
        val d = doc ?: return
        val voice = narrator ?: return
        val sentence = d.sentences.getOrNull(index) ?: return
        val sectionInfo = d.sections.getOrNull(d.sectionAt(index))
        val id = "f${++generation}"
        currentId = id
        save()
        notifyListeners()
        val isHeading = sectionInfo != null && sectionInfo.start == index && sectionInfo.titleSpoken
        // Al saltar a una "Parte N" (documentos sin títulos) se dice su nombre para ubicarse.
        val text = if (announceSection && sectionInfo != null && sectionInfo.start == index && !sectionInfo.titleSpoken) {
            "${sectionInfo.title}. $sentence"
        } else {
            sentence
        }
        voice.speak(text, id, rate, pauseAfterMs = if (isHeading) 450 else 0)
    }

    private fun onSentenceFinished(id: String) {
        if (id != currentId || state != State.PLAYING) return
        val d = doc ?: return
        if (index < d.sentences.lastIndex) {
            index++
            speakCurrent(announceSection = false)
        } else {
            // Fin del documento.
            currentId = null
            state = State.PAUSED
            abandonFocus()
            app?.let { Library.setPosition(it, d.id, 0) }
            index = 0
            notifyListeners()
            app?.let { Toast.makeText(it, R.string.finished, Toast.LENGTH_LONG).show() }
        }
    }

    private fun onVoiceUnavailable() {
        cancelSpeech()
        if (state == State.PLAYING) state = State.PAUSED
        abandonFocus()
        notifyListeners()
        app?.let { Toast.makeText(it, R.string.voice_unavailable, Toast.LENGTH_LONG).show() }
    }

    private fun cancelSpeech() {
        generation++
        currentId = null
        narrator?.stop()
    }

    private fun save() {
        val ctx = app ?: return
        val d = doc ?: return
        Library.setPosition(ctx, d.id, index)
    }

    private fun notifyListeners() {
        for (l in listeners) l.onPlayerChanged()
    }

    // ---------------------------------------------------------------- audio

    private val mediaAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Pide el audio como un reproductor: la música (Spotify, etc.) se pausa mientras se lee. */
    private fun requestFocus(): Boolean {
        val am = audioManager ?: return true
        if (focusRequest != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(mediaAttributes)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
        if (am.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            app?.let { Toast.makeText(it, R.string.audio_busy, Toast.LENGTH_SHORT).show() }
            return false
        }
        focusRequest = request
        return true
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        audioManager?.abandonAudioFocusRequest(request)
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // Llamada, indicación del GPS o nota de voz: se pausa y se sigue después.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                if (state == State.PLAYING) {
                    cancelSpeech()
                    state = State.PAUSED
                    resumeOnFocus = true
                    notifyListeners()
                }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Otra app empezó a reproducir: el foco ya no es nuestro.
                focusRequest = null
                if (state == State.PLAYING) {
                    cancelSpeech()
                    state = State.PAUSED
                    save()
                    notifyListeners()
                }
                resumeOnFocus = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus && state == State.PAUSED) {
                resumeOnFocus = false
                state = State.PLAYING
                speakCurrent(announceSection = false)
            }
        }
    }

    /**
     * La voz la reproduce el motor de texto a voz, no esta app, y Android manda los botones multimedia
     * (volante, estéreo, audífonos) a la última app que sonó. Medio segundo de silencio propio hace que
     * esos botones lleguen aquí y no a la app de música.
     */
    private fun claimMediaButtons() {
        try {
            val sampleRate = 8000
            val samples = ShortArray(sampleRate / 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            main.postDelayed({
                try {
                    track.release()
                } catch (e: Exception) {
                    // Ya liberado.
                }
            }, 1500)
        } catch (e: Exception) {
            // Sin esto solo se pierde el control desde los botones del carro.
        }
    }
}
