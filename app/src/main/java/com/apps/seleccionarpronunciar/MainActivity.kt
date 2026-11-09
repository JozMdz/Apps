package com.apps.seleccionarpronunciar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
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

    companion object {
        private const val REQUEST_BLUETOOTH = 1
        private const val REQUEST_NOTIFICATIONS = 2
    }

    private lateinit var statusText: TextView
    private lateinit var openSettings: Button
    private lateinit var rateLabel: TextView
    private lateinit var rateSeek: SeekBar
    private lateinit var pitchLabel: TextView
    private lateinit var pitchSeek: SeekBar
    private lateinit var highlightSwitch: Switch
    private lateinit var floatingSwitch: Switch
    private lateinit var carAutoSwitch: Switch
    private lateinit var carPick: Button
    private var afterBluetoothPermission: (() -> Unit)? = null
    private lateinit var restrictedButton: Button
    private lateinit var restrictedHelp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
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

        bindSwitch(R.id.keep_alive_switch, Prefs.keepAlive(this)) { checked ->
            Prefs.setKeepAlive(this, checked)
            if (checked) requestNotificationPermission()
        }
        bindSwitch(R.id.feedback_vibrate_switch, Prefs.feedbackVibrate(this)) { Prefs.setFeedbackVibrate(this, it) }
        bindSwitch(R.id.feedback_sound_switch, Prefs.feedbackSound(this)) { Prefs.setFeedbackSound(this, it) }
        if (Prefs.keepAlive(this)) requestNotificationPermission()

        findViewById<Button>(R.id.error_clear).setOnClickListener {
            CrashLog.clear(this)
            updateErrorSection()
        }
        findViewById<Button>(R.id.error_copy).setOnClickListener {
            val text = CrashLog.last(this) ?: return@setOnClickListener
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("error", text))
            Toast.makeText(this, R.string.error_copied, Toast.LENGTH_SHORT).show()
        }

        val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("POCO", ignoreCase = true) || Build.BRAND.equals("Redmi", ignoreCase = true)
        findViewById<View>(R.id.xiaomi_section).visibility = if (isXiaomi) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.xiaomi_autostart).setOnClickListener {
            val autostart = Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            if (!tryStart(autostart)) openAppDetails()
        }
        findViewById<Button>(R.id.xiaomi_battery).setOnClickListener {
            val battery = Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                .putExtra("package_name", packageName)
                .putExtra("package_label", getString(R.string.app_name))
            if (!tryStart(battery)) openAppDetails()
        }
        restrictedButton = findViewById(R.id.allow_restricted)
        restrictedHelp = findViewById(R.id.restricted_help)
        restrictedButton.setOnClickListener { openAppDetails() }

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

        bindRadio(
            R.id.tap_group, Prefs.tapMode(this),
            mapOf(R.id.tap_sentence to Prefs.TAP_SENTENCE, R.id.tap_from to Prefs.TAP_FROM, R.id.tap_element to Prefs.TAP_ELEMENT),
        ) { Prefs.setTapMode(this, it) }
        bindRadio(
            R.id.music_group, Prefs.musicMode(this),
            mapOf(R.id.music_duck to Prefs.MUSIC_DUCK, R.id.music_pause to Prefs.MUSIC_PAUSE, R.id.music_none to Prefs.MUSIC_NONE),
        ) { Prefs.setMusicMode(this, it) }
        findViewById<Switch>(R.id.drag_rows_switch).apply {
            isChecked = Prefs.dragRows(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setDragRows(this@MainActivity, checked) }
        }

        carAutoSwitch = findViewById(R.id.car_auto_switch)
        carPick = findViewById(R.id.car_pick)
        carAutoSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && Prefs.carDevices(this).isEmpty()) {
                carAutoSwitch.isChecked = false
                withBluetoothPermission { pickCarDevices(enableAuto = true) }
            } else if (checked) {
                withBluetoothPermission { Prefs.setCarAuto(this, true) }
            } else {
                Prefs.setCarAuto(this, false)
            }
        }
        carPick.setOnClickListener { withBluetoothPermission { pickCarDevices(enableAuto = false) } }

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
        updateErrorSection()
        val enabled = isServiceEnabled()
        updateStatus()
        // Al abrir la app el sistema puede volver a conectar el servicio: revisamos de nuevo en un momento.
        statusText.postDelayed({ updateStatus() }, 1500)
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
        carAutoSwitch.isChecked = Prefs.carAuto(this)
        val systemButton = SystemShortcut.isAssigned(this)
        // Con el botón del sistema, las opciones del botón propio (visible, modo manejo) no aplican.
        findViewById<View>(R.id.system_button_info).visibility = if (systemButton) View.VISIBLE else View.GONE
        findViewById<View>(R.id.own_button_options).visibility = if (systemButton) View.GONE else View.VISIBLE
        updateCarPickLabel()
    }

    override fun onDestroy() {
        // Si el servicio no está activo, nadie más usa el motor de voz.
        if (isFinishing && SelectToSpeakService.instance == null) Speaker.shutdown()
        super.onDestroy()
    }

    private fun updateStatus() {
        val running = SelectToSpeakService.instance != null
        openSettings.visibility = if (running) View.GONE else View.VISIBLE
        statusText.setText(
            when {
                running -> R.string.status_on
                isServiceEnabled() -> R.string.status_stopped
                else -> R.string.status_off
            }
        )
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<Switch>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun updateErrorSection() {
        val error = CrashLog.last(this)
        findViewById<View>(R.id.error_section).visibility = if (error == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.error_text).text = error
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun bindRadio(groupId: Int, current: String, values: Map<Int, String>, onChange: (String) -> Unit) {
        val group = findViewById<RadioGroup>(groupId)
        group.check(values.entries.firstOrNull { it.value == current }?.key ?: values.keys.first())
        group.setOnCheckedChangeListener { _, id -> values[id]?.let(onChange) }
    }

    // ---------------------------------------------------------------- Bluetooth del carro

    private fun withBluetoothPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            afterBluetoothPermission = action
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BLUETOOTH) return
        val action = afterBluetoothPermission
        afterBluetoothPermission = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            action?.invoke()
        } else {
            Toast.makeText(this, R.string.car_permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun pickCarDevices(enableAuto: Boolean) {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val bonded = try {
            adapter?.bondedDevices?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }.sortedBy { it.name ?: it.address }
        if (bonded.isEmpty()) {
            Toast.makeText(this, R.string.car_none_paired, Toast.LENGTH_LONG).show()
            return
        }
        val chosen = Prefs.carDevices(this).toMutableSet()
        val names = bonded.map { it.name ?: it.address }.toTypedArray()
        val checked = BooleanArray(bonded.size) { bonded[it].address in chosen }
        AlertDialog.Builder(this)
            .setTitle(R.string.car_pick_title)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) chosen.add(bonded[which].address) else chosen.remove(bonded[which].address)
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                Prefs.setCarDevices(this, chosen)
                if (enableAuto && chosen.isNotEmpty()) {
                    Prefs.setCarAuto(this, true)
                    carAutoSwitch.isChecked = true
                }
                updateCarPickLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateCarPickLabel() {
        val count = Prefs.carDevices(this).size
        carPick.text = if (count == 0) getString(R.string.car_pick) else getString(R.string.car_pick_count, count)
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
