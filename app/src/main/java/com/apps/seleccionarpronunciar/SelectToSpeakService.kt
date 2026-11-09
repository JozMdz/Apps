package com.apps.seleccionarpronunciar

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.Settings
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.view.Display
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.DecimalFormat
import kotlin.math.abs

/**
 * Servicio de accesibilidad: muestra un botón flotante (y responde al botón de accesibilidad del
 * sistema). Al activarlo se puede tocar un elemento o arrastrar un recuadro para oírlo en voz alta.
 */
class SelectToSpeakService : AccessibilityService(), Speaker.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        /** Instancia activa, o null si el servicio no está habilitado. */
        @Volatile
        var instance: SelectToSpeakService? = null
            private set

        private const val TILE_DELAY_MS = 450L
        private const val CAPTURE_DELAY_MS = 200L
        private const val OCR_TIMEOUT_MS = 15_000L
    }

    private var ocr: OcrReader? = null
    private var ocrBusy = false
    private var ocrStartedAt = 0L

    /** El sistema muestra su propio botón de accesibilidad para este servicio. */
    private var systemButtonAvailable = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var themed: Context

    private var floatingButton: ImageView? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var selectionOverlay: View? = null
    private var highlightView: HighlightView? = null
    private var controlBar: View? = null
    private var playPauseButton: ImageButton? = null
    private var speedLabel: TextView? = null

    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    private val density get() = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    // ---------------------------------------------------------------- ciclo de vida

    override fun onServiceConnected() {
        super.onServiceConnected()
        CrashLog.install(this)
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        themed = ContextThemeWrapper(this, android.R.style.Theme_Material)

        Speaker.init(this)
        Speaker.addListener(this)
        Prefs.of(this).registerOnSharedPreferenceChangeListener(this)

        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) = toggle()

            override fun onAvailabilityChanged(controller: AccessibilityButtonController, available: Boolean) {
                systemButtonAvailable = available
                updateFloatingButton()
            }
        }
        systemButtonAvailable = accessibilityButtonController.isAccessibilityButtonAvailable
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(SystemShortcut.BUTTON_TARGETS_KEY), false, shortcutObserver,
        )
        accessibilityButtonController.registerAccessibilityButtonCallback(callback)
        buttonCallback = callback

        updateFloatingButton()
        onSpeakerChanged(Speaker.state, Speaker.currentSegment)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
        receiverRegistered = true
        checkCarConnection()
    }

    // ---------------------------------------------------------------- modo manejo (Bluetooth del carro)

    private var receiverRegistered = false

    private val shortcutObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (instance === this@SelectToSpeakService) updateFloatingButton()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!Prefs.carAuto(context)) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val address = device?.address ?: return
            if (address !in Prefs.carDevices(context)) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> setDrivingMode(true)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> setDrivingMode(false)
            }
        }
    }

    private fun setDrivingMode(on: Boolean) {
        if (!on) Speaker.stop()
        if (Prefs.floatingButton(this) == on) return
        Prefs.setFloatingButton(this, on)
        Toast.makeText(this, if (on) R.string.driving_on else R.string.driving_off, Toast.LENGTH_SHORT).show()
    }

    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Con el modo automático activo, muestra u oculta el botón según si el carro está conectado ahora. */
    private fun checkCarConnection() {
        if (!Prefs.carAuto(this) || !hasBluetoothPermission()) return
        val devices = Prefs.carDevices(this)
        if (devices.isEmpty()) return
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        var pending = profiles.size
        var connected = false
        for (profile in profiles) {
            val ok = try {
                adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        try {
                            if (proxy.connectedDevices.any { it.address in devices }) connected = true
                        } catch (e: SecurityException) {
                            // Sin permiso: no podemos saberlo.
                        }
                        adapter.closeProfileProxy(p, proxy)
                        if (--pending == 0 && instance === this@SelectToSpeakService) setDrivingMode(connected)
                    }

                    override fun onServiceDisconnected(p: Int) = Unit
                }, profile)
            } catch (e: SecurityException) {
                false
            }
            if (!ok) pending--
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        Speaker.stop()
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanUp()
        return super.onUnbind(intent)
    }

    private fun cleanUp() {
        if (instance !== this) return
        instance = null
        handler.removeCallbacksAndMessages(null)
        ocr?.close()
        ocr = null
        if (receiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            receiverRegistered = false
        }
        contentResolver.unregisterContentObserver(shortcutObserver)
        buttonCallback?.let { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) }
        buttonCallback = null
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(this)
        Speaker.removeListener(this)
        Speaker.shutdown()
        hideSelection()
        hideControls()
        removeView(floatingButton)
        floatingButton = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Tras girar la pantalla, el botón flotante podría quedar fuera de la pantalla.
        val button = floatingButton ?: return
        val params = floatingParams ?: return
        clampToScreen(params)
        windowManager.updateViewLayout(button, params)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_FLOATING_BUTTON -> updateFloatingButton()
            Prefs.KEY_HIGHLIGHT -> onSpeakerChanged(Speaker.state, Speaker.currentSegment)
            Prefs.KEY_RATE -> updateSpeedLabel()
            Prefs.KEY_CAR_AUTO, Prefs.KEY_CAR_DEVICES -> checkCarConnection()
        }
    }

    // ---------------------------------------------------------------- acciones

    /** Botón flotante / botón de accesibilidad. */
    fun toggle() = safely {
        when {
            selectionOverlay != null -> hideSelection()
            Speaker.state != Speaker.State.IDLE -> Speaker.stop()
            else -> showSelection()
        }
    }

    /** Ejecuta una acción sin dejar que un error cierre el servicio; el error queda registrado. */
    private inline fun safely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            CrashLog.record(this, e)
            ocrBusy = false
            Toast.makeText(this, getString(R.string.error_toast, e.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }

    /** Icono de Ajustes rápidos: se espera a que el panel termine de cerrarse antes de actuar. */
    fun runTileAction() {
        hideSelection()
        if (Speaker.state != Speaker.State.IDLE) {
            Speaker.stop()
            return
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (instance !== this) return@postDelayed
            safely {
                when (Prefs.tileAction(this)) {
                    Prefs.TILE_READ_ALL -> readAll()
                    else -> showSelection()
                }
            }
        }, TILE_DELAY_MS)
    }

    private fun readAt(x: Int, y: Int) {
        hideSelection()
        val mode = Prefs.ocrMode(this)
        val ocrAllowed = mode != Prefs.OCR_NEVER && ocrSupported()
        if (mode != Prefs.OCR_ALWAYS) {
            val fromTree = ScreenTextReader.textAt(this, x, y, dp(24), Prefs.tapMode(this), ocrAllowed)
            if (fromTree != null && (fromTree.isNotEmpty() || !ocrAllowed)) {
                speakOrWarn(fromTree)
                return
            }
        }
        // Sin texto accesible bajo el dedo (PDF, imagen…), o sin forma de ubicar la frase:
        // reconocemos el texto de la captura.
        withOcr { lines -> speakOrWarn(OcrReader.textAt(lines, x, y, dp(24), Prefs.tapMode(this))) }
    }

    private fun readArea(area: Rect) {
        hideSelection()
        val mode = Prefs.ocrMode(this)
        if (mode == Prefs.OCR_ALWAYS) {
            withOcr { lines -> speakOrWarn(OcrReader.textIn(lines, area)) }
            return
        }
        val ocrAllowed = mode != Prefs.OCR_NEVER && ocrSupported()
        val scan = ScreenTextReader.scan(this, area, minBlankArea(), ocrAllowed)
        if (!ocrAllowed || (scan.segments.isNotEmpty() && scan.blankRegions.isEmpty())) {
            speakOrWarn(scan.segments)
            return
        }
        withOcr { lines ->
            val fromImage = if (scan.segments.isEmpty()) {
                OcrReader.textIn(lines, area)
            } else {
                // Solo lo que hay dentro de las zonas sin texto accesible, sin repetir lo ya leído.
                OcrReader.textIn(lines, area, scan.blankRegions).filter { ocr ->
                    val b = ocr.bounds!!
                    scan.segments.none { it.bounds!!.contains(b.centerX(), b.centerY()) }
                }
            }
            speakOrWarn(OcrReader.merge(scan.segments, fromImage))
        }
    }

    private fun readAll() {
        readArea(ScreenTextReader.screenBounds(this))
    }

    /** Zonas sin texto a partir de este tamaño se consideran contenido a reconocer (≈ 120 × 120 dp). */
    private fun minBlankArea(): Long = dp(120).toLong() * dp(120)

    private fun ocrSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Hace una captura de pantalla (sin nuestras capas de selección) y reconoce su texto.
     * [onResult] recibe las líneas reconocidas; si falla, se avisa al usuario.
     */
    private fun withOcr(onResult: (List<OcrReader.Line>) -> Unit) {
        if (!ocrSupported()) {
            Toast.makeText(this, R.string.no_text_found, Toast.LENGTH_SHORT).show()
            return
        }
        // Si un reconocimiento anterior se quedó colgado, no bloqueamos para siempre.
        if (ocrBusy && System.currentTimeMillis() - ocrStartedAt < OCR_TIMEOUT_MS) return
        ocrBusy = true
        ocrStartedAt = System.currentTimeMillis()
        Toast.makeText(this, R.string.ocr_working, Toast.LENGTH_SHORT).show()
        // Esperamos a que desaparezca la capa de selección antes de capturar.
        handler.postDelayed({
            if (instance !== this) return@postDelayed
            safely { captureScreen { bitmap -> safely {
                if (bitmap == null) {
                    ocrBusy = false
                    Toast.makeText(this, R.string.ocr_failed, Toast.LENGTH_SHORT).show()
                    return@captureScreen
                }
                val reader = ocr ?: OcrReader().also { ocr = it }
                reader.recognize(bitmap) { lines ->
                    ocrBusy = false
                    if (instance !== this) return@recognize
                    if (lines == null) {
                        Toast.makeText(this, R.string.ocr_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        safely { onResult(lines) }
                    }
                }
            } } }
        }, CAPTURE_DELAY_MS)
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun captureScreen(onBitmap: (Bitmap?) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.let { hardware ->
                        hardware.copy(Bitmap.Config.ARGB_8888, false).also { hardware.recycle() }
                    }
                } catch (e: RuntimeException) {
                    CrashLog.record(this@SelectToSpeakService, e)
                    null
                } finally {
                    buffer.close()
                }
                onBitmap(bitmap)
            }

            override fun onFailure(errorCode: Int) = onBitmap(null)
        })
    }

    private fun speakOrWarn(segments: List<Segment>) {
        if (segments.isEmpty()) {
            Toast.makeText(this, R.string.no_text_found, Toast.LENGTH_SHORT).show()
        } else {
            Speaker.speak(this, segments)
        }
    }

    // ---------------------------------------------------------------- Speaker.Listener

    override fun onSpeakerChanged(state: Speaker.State, segment: Segment?) {
        floatingButton?.setImageResource(if (state == Speaker.State.IDLE) R.drawable.ic_speak else R.drawable.ic_stop)
        if (state == Speaker.State.IDLE) {
            hideControls()
            return
        }
        showControls()
        playPauseButton?.setImageResource(
            if (state == Speaker.State.SPEAKING) R.drawable.ic_pause else R.drawable.ic_play
        )
        highlightView?.setTarget(if (Prefs.highlight(this)) segment?.bounds else null)
    }

    // ---------------------------------------------------------------- ventanas superpuestas

    private fun overlayParams(width: Int, height: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun fullScreenFlags(touchable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun removeView(view: View?) {
        if (view == null) return
        try {
            windowManager.removeViewImmediate(view)
        } catch (e: IllegalArgumentException) {
            // La vista ya no estaba añadida.
        }
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    // --- botón flotante

    private fun updateFloatingButton() {
        // Si el sistema ya muestra el botón de accesibilidad, no duplicamos el nuestro.
        val systemButton = systemButtonAvailable || SystemShortcut.isAssigned(this)
        if (Prefs.floatingButton(this) && !systemButton) showFloatingButton() else {
            removeView(floatingButton)
            floatingButton = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        if (floatingButton != null) return
        val size = dp(56)
        val button = ImageView(themed).apply {
            setImageResource(if (Speaker.state == Speaker.State.IDLE) R.drawable.ic_speak else R.drawable.ic_stop)
            background = circle(getColor(R.color.primary))
            scaleType = ImageView.ScaleType.CENTER
            elevation = dp(6).toFloat()
            alpha = 0.9f
            contentDescription = getString(R.string.cd_floating_button)
            isClickable = true
            setOnClickListener { toggle() }
            // Mantener pulsado: ocultar el botón (p. ej. al terminar de manejar).
            setOnLongClickListener {
                handler.post {
                    Speaker.stop()
                    hideSelection()
                    Prefs.setFloatingButton(this@SelectToSpeakService, false)
                    Toast.makeText(this@SelectToSpeakService, R.string.button_hidden, Toast.LENGTH_LONG).show()
                }
                true
            }
        }
        val metrics = resources.displayMetrics
        val (x, y) = Prefs.buttonPosition(this, metrics.widthPixels - size - dp(8), metrics.heightPixels / 3)
        val params = overlayParams(
            size, size,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        clampToScreen(params)

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        button.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                    false // deja que la vista gestione la pulsación (ripple / clic)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        v.isPressed = false
                        v.cancelLongPress()
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clampToScreen(params)
                        windowManager.updateViewLayout(v, params)
                    }
                    dragging
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        Prefs.setButtonPosition(this, params.x, params.y)
                        dragging = false
                        true // consumido: no es un clic
                    } else false
                }
                else -> false
            }
        }

        windowManager.addView(button, params)
        floatingButton = button
        floatingParams = params
    }

    private fun clampToScreen(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
    }

    // --- modo selección

    private fun showSelection() {
        if (selectionOverlay != null) return
        val root = FrameLayout(themed)
        root.addView(
            // Se ejecuta después del toque: quitar la capa en medio de su propio evento táctil
            // puede cerrar el servicio.
            SelectionView(
                themed, Prefs.dragRows(this),
                { x, y -> handler.post { safely { readAt(x, y) } } },
                { area -> handler.post { safely { readArea(area) } } },
            ),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val bar = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(235, 32, 33, 36))
            }
            elevation = dp(8).toFloat()
        }
        bar.addView(TextView(themed).apply {
            setText(if (Prefs.dragRows(this@SelectToSpeakService)) R.string.select_hint_rows else R.string.select_hint)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(barButton(R.string.read_all) { handler.post { safely { readAll() } } })
        bar.addView(barButton(R.string.cancel) { handler.post { safely { hideSelection() } } })

        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ).apply {
            setMargins(dp(12), statusBarHeight() + dp(8), dp(12), 0)
        })

        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            fullScreenFlags(touchable = true),
        )
        windowManager.addView(root, params)
        selectionOverlay = root
    }

    private fun barButton(textRes: Int, onClick: () -> Unit) =
        Button(themed, null, android.R.attr.borderlessButtonStyle).apply {
            setText(textRes)
            setTextColor(getColor(R.color.primary_light))
            setOnClickListener { onClick() }
        }

    private fun hideSelection() {
        removeView(selectionOverlay)
        selectionOverlay = null
    }

    private fun statusBarHeight(): Int {
        @SuppressLint("InternalInsetResource", "DiscouragedApi")
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    // --- barra de control y resaltado

    private fun showControls() {
        if (highlightView == null) {
            val view = HighlightView(themed)
            windowManager.addView(
                view,
                overlayParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    fullScreenFlags(touchable = false),
                ),
            )
            highlightView = view
        }
        if (controlBar != null) return

        val bar = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.argb(240, 32, 33, 36))
            }
            elevation = dp(8).toFloat()
        }
        bar.addView(controlButton(R.drawable.ic_previous, R.string.cd_previous) { Speaker.previous() })
        playPauseButton = controlButton(R.drawable.ic_pause, R.string.cd_play_pause) { Speaker.togglePause() }
        bar.addView(playPauseButton)
        bar.addView(controlButton(R.drawable.ic_next, R.string.cd_next) { Speaker.next() })
        speedLabel = TextView(themed).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            minWidth = dp(56)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = getString(R.string.cd_speed)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Prefs.setRate(this@SelectToSpeakService, Prefs.nextRateStep(Prefs.rate(this@SelectToSpeakService)))
                Speaker.restartCurrent()
            }
        }
        bar.addView(speedLabel)
        updateSpeedLabel()
        bar.addView(controlButton(R.drawable.ic_close, R.string.cd_stop) { Speaker.stop() })

        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        windowManager.addView(bar, params)
        controlBar = bar
    }

    private fun controlButton(icon: Int, description: Int, onClick: () -> Unit) =
        ImageButton(themed).apply {
            setImageResource(icon)
            contentDescription = getString(description)
            val outValue = TypedValue()
            themed.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { onClick() }
        }

    private fun updateSpeedLabel() {
        speedLabel?.text = formatRate(Prefs.rate(this))
    }

    private fun hideControls() {
        removeView(controlBar)
        controlBar = null
        playPauseButton = null
        speedLabel = null
        removeView(highlightView)
        highlightView = null
    }
}

/** 1.0 -> "1,0x", 1.25 -> "1,25x" (según el idioma del dispositivo). */
fun formatRate(rate: Float): String = DecimalFormat("0.0#").format(rate) + "x"
