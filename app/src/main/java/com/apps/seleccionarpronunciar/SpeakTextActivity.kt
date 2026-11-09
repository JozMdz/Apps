package com.apps.seleccionarpronunciar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

/**
 * Recibe texto desde el menú de selección ("Pronunciar") o desde "Compartir".
 * Si el servicio de accesibilidad está activo, delega en él (muestra su barra de control);
 * si no, muestra un pequeño diálogo reproductor.
 */
class SpeakTextActivity : Activity(), Speaker.Listener {

    private lateinit var preview: TextView
    private lateinit var playPause: ImageButton
    private lateinit var speed: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = extractText(intent)
        if (text == null) {
            Toast.makeText(this, R.string.no_text_received, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (SelectToSpeakService.instance != null) {
            Speaker.speakText(this, text)
            finish()
            return
        }

        setContentView(R.layout.activity_speak_text)
        preview = findViewById(R.id.text_preview)
        playPause = findViewById(R.id.btn_play_pause)
        speed = findViewById(R.id.btn_speed)

        playPause.setOnClickListener {
            if (Speaker.state == Speaker.State.IDLE) {
                Speaker.speakText(this, preview.text.toString())
            } else {
                Speaker.togglePause()
            }
        }
        findViewById<ImageButton>(R.id.btn_previous).setOnClickListener { Speaker.previous() }
        findViewById<ImageButton>(R.id.btn_next).setOnClickListener { Speaker.next() }
        findViewById<ImageButton>(R.id.btn_stop).setOnClickListener { finish() }
        speed.setOnClickListener {
            Prefs.setRate(this, Prefs.nextRateStep(Prefs.rate(this)))
            updateSpeed()
            Speaker.restartCurrent()
        }

        Speaker.addListener(this)
        start(text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val text = extractText(intent) ?: return
        if (::preview.isInitialized) start(text)
    }

    private fun start(text: String) {
        preview.text = text
        updateSpeed()
        Speaker.speakText(this, text)
    }

    override fun onDestroy() {
        if (::preview.isInitialized) {
            Speaker.removeListener(this)
            if (SelectToSpeakService.instance == null) Speaker.shutdown()
        }
        super.onDestroy()
    }

    override fun onSpeakerChanged(state: Speaker.State, segment: Segment?) {
        playPause.setImageResource(if (state == Speaker.State.SPEAKING) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateSpeed() {
        speed.text = formatRate(Prefs.rate(this))
    }

    private fun extractText(intent: Intent?): String? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            else -> null
        }
        return text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
