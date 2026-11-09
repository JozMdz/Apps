package com.apps.seleccionarpronunciar

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var openSettings: Button
    private lateinit var rateLabel: TextView
    private lateinit var rateSeek: SeekBar
    private lateinit var pitchLabel: TextView
    private lateinit var pitchSeek: SeekBar
    private lateinit var highlightSwitch: Switch
    private lateinit var floatingSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        openSettings = findViewById(R.id.open_settings)
        rateLabel = findViewById(R.id.rate_label)
        rateSeek = findViewById(R.id.rate_seek)
        pitchLabel = findViewById(R.id.pitch_label)
        pitchSeek = findViewById(R.id.pitch_seek)
        highlightSwitch = findViewById(R.id.highlight_switch)
        floatingSwitch = findViewById(R.id.floating_switch)

        openSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Velocidad: 0,25x … 3,0x en pasos de 0,05. Tono: 0,5x … 2,0x en pasos de 0,05.
        rateSeek.setOnSeekBarChangeListener(seekListener { progress ->
            val rate = 0.25f + progress * 0.05f
            Prefs.setRate(this, rate)
            rateLabel.text = getString(R.string.speech_rate, rate)
        })
        pitchSeek.setOnSeekBarChangeListener(seekListener { progress ->
            val pitch = 0.5f + progress * 0.05f
            Prefs.setPitch(this, pitch)
            pitchLabel.text = getString(R.string.speech_pitch, pitch)
        })

        highlightSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setHighlight(this, checked) }
        floatingSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setFloatingButton(this, checked) }

        findViewById<Button>(R.id.tts_settings).setOnClickListener {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        val testText = findViewById<EditText>(R.id.test_text)
        findViewById<Button>(R.id.test_speak).setOnClickListener {
            val text = testText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.no_text_received, Toast.LENGTH_SHORT).show()
            } else {
                Speaker.speakText(this, text)
            }
        }
        findViewById<Button>(R.id.test_stop).setOnClickListener { Speaker.stop() }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        statusText.setText(if (enabled) R.string.status_on else R.string.status_off)

        val rate = Prefs.rate(this)
        rateSeek.progress = ((rate - 0.25f) / 0.05f).toInt().coerceIn(0, rateSeek.max)
        rateLabel.text = getString(R.string.speech_rate, rate)
        val pitch = Prefs.pitch(this)
        pitchSeek.progress = ((pitch - 0.5f) / 0.05f).toInt().coerceIn(0, pitchSeek.max)
        pitchLabel.text = getString(R.string.speech_pitch, pitch)
        highlightSwitch.isChecked = Prefs.highlight(this)
        floatingSwitch.isChecked = Prefs.floatingButton(this)
    }

    override fun onDestroy() {
        // Si el servicio no está activo, nadie más usa el motor de voz.
        if (isFinishing && SelectToSpeakService.instance == null) Speaker.shutdown()
        super.onDestroy()
    }

    private fun isServiceEnabled(): Boolean {
        if (SelectToSpeakService.instance != null) return true
        val expected = ComponentName(this, SelectToSpeakService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun seekListener(onUserChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onUserChange(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) = Speaker.restartCurrent()
    }
}
