package com.apps.seleccionarpronunciar

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.app.StatusBarManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.view.View
import android.widget.RadioGroup
import android.annotation.TargetApi
import android.os.Bundle
import android.net.Uri
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
    private lateinit var restrictedButton: Button
    private lateinit var restrictedHelp: TextView

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

        openSettings.setOnClickListener { openAccessibilitySettings() }
        restrictedButton = findViewById(R.id.allow_restricted)
        restrictedHelp = findViewById(R.id.restricted_help)
        restrictedButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
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

        val ocrGroup = findViewById<RadioGroup>(R.id.ocr_group)
        ocrGroup.check(
            when (Prefs.ocrMode(this)) {
                Prefs.OCR_ALWAYS -> R.id.ocr_always
                Prefs.OCR_NEVER -> R.id.ocr_never
                else -> R.id.ocr_auto
            }
        )
        ocrGroup.setOnCheckedChangeListener { _, id ->
            Prefs.setOcrMode(
                this,
                when (id) {
                    R.id.ocr_always -> Prefs.OCR_ALWAYS
                    R.id.ocr_never -> Prefs.OCR_NEVER
                    else -> Prefs.OCR_AUTO
                },
            )
        }

        val tileGroup = findViewById<RadioGroup>(R.id.tile_action_group)
        tileGroup.check(
            if (Prefs.tileAction(this) == Prefs.TILE_READ_ALL) R.id.tile_action_read_all else R.id.tile_action_select
        )
        tileGroup.setOnCheckedChangeListener { _, id ->
            Prefs.setTileAction(this, if (id == R.id.tile_action_read_all) Prefs.TILE_READ_ALL else Prefs.TILE_SELECT)
            TileService.requestListeningState(this, ComponentName(this, QuickTileService::class.java))
        }

        val addTile = findViewById<Button>(R.id.tile_add)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addTile.setOnClickListener { requestAddTile() }
        } else {
            addTile.visibility = View.GONE
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
        openSettings.visibility = if (enabled) View.GONE else View.VISIBLE
        // Android 13+ bloquea los servicios de accesibilidad de apps instaladas fuera de la tienda.
        val showRestricted = !enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        restrictedHelp.visibility = if (showRestricted) View.VISIBLE else View.GONE
        restrictedButton.visibility = if (showRestricted) View.VISIBLE else View.GONE

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

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        val statusBar = getSystemService(StatusBarManager::class.java) ?: return
        statusBar.requestAddTileService(
            ComponentName(this, QuickTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_speak),
            mainExecutor,
        ) { result ->
            val msg = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
                else -> R.string.tile_add_failed
            }
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Abre Accesibilidad intentando resaltar directamente este servicio en la lista. */
    private fun openAccessibilitySettings() {
        val key = ComponentName(this, SelectToSpeakService::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", key)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
        Toast.makeText(this, R.string.find_service_hint, Toast.LENGTH_LONG).show()
        startActivity(intent)
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
