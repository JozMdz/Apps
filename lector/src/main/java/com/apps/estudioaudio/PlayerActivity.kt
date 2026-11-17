package com.apps.estudioaudio

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.DecimalFormat

/** Reproductor: índice de apartados arriba y controles grandes abajo. */
class PlayerActivity : Activity(), Player.Listener {

    companion object {
        const val EXTRA_DOC_ID = "doc_id"
        const val EXTRA_AUTO_PLAY = "auto_play"
        const val EXTRA_FROM_START = "from_start"
        private const val MENU_VOICE = 1
        private const val MENU_CLOSE = 2
        private const val MANUAL_SCROLL_HOLD_MS = 6000L
        private const val NEVER = Long.MIN_VALUE / 2
    }

    private lateinit var sections: ListView
    private lateinit var text: ListView
    private lateinit var tabText: Button
    private lateinit var tabIndex: Button
    private var showingText = true
    private var shownIndex = -1
    /** Nivel de título de cada frase (0 = texto normal), para dibujar los títulos distintos. */
    private var headingLevel = IntArray(0)
    /** Momento del último desplazamiento manual: mientras se explora el texto no se recentra solo. */
    private var touchedAt = NEVER
    private lateinit var nowSection: TextView
    private lateinit var nowSentence: TextView
    private lateinit var position: TextView
    private lateinit var progress: ProgressBar
    private lateinit var playPause: ImageButton
    private lateinit var speed: Button
    private lateinit var loop: Button
    /** Frase marcada como inicio de un tramo a repetir (falta marcar el final). */
    private var loopAnchor = -1
    private var shownLoop = ""
    private var shownSection = -1
    private var shownDoc: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        sections = findViewById(R.id.sections)
        nowSection = findViewById(R.id.now_section)
        nowSentence = findViewById(R.id.now_sentence)
        position = findViewById(R.id.position)
        progress = findViewById(R.id.progress)
        playPause = findViewById(R.id.play_pause)
        speed = findViewById(R.id.speed)

        text = findViewById(R.id.text)
        tabText = findViewById(R.id.tab_text)
        tabIndex = findViewById(R.id.tab_index)

        sections.adapter = adapter
        sections.setOnItemClickListener { _, _, index, _ ->
            Player.jumpToSection(index)
            showTab(text = true) // tras elegir apartado se vuelve al texto, ya en ese lugar
        }
        text.adapter = textAdapter
        text.setOnItemClickListener { _, _, index, _ ->
            touchedAt = NEVER
            Player.jumpToSentence(index)
        }
        // Tramo a la medida: mantener pulsada la primera frase y luego la última.
        text.setOnItemLongClickListener { _, _, index, _ ->
            if (loopAnchor < 0) {
                loopAnchor = index
                Toast.makeText(this, R.string.loop_mark_start, Toast.LENGTH_LONG).show()
                textAdapter.notifyDataSetChanged()
            } else {
                val from = loopAnchor
                loopAnchor = -1
                touchedAt = NEVER
                Player.setCustomLoop(from, index)
            }
            true
        }
        text.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, state: Int) {
                if (state == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) touchedAt = SystemClock.elapsedRealtime()
            }

            override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) = Unit
        })
        tabText.setOnClickListener { showTab(text = true) }
        tabIndex.setOnClickListener { showTab(text = false) }
        showTab(getSharedPreferences("avance", MODE_PRIVATE).getBoolean("_vista_texto", true), remember = false)
        playPause.setOnClickListener { Player.toggle() }
        speed.setOnClickListener { Player.cycleRate() }
        loop = findViewById(R.id.loop)
        loop.setOnClickListener {
            loopAnchor = -1
            Player.cycleLoop()
        }
        findViewById<View>(R.id.previous_section).setOnClickListener { Player.previousSection() }
        findViewById<View>(R.id.next_section).setOnClickListener { Player.nextSection() }
        findViewById<View>(R.id.previous_sentence).setOnClickListener { Player.previousSentence() }
        findViewById<View>(R.id.next_sentence).setOnClickListener { Player.nextSentence() }

        if (savedInstanceState == null) openFromIntent(intent)
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    private fun openFromIntent(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_DOC_ID) ?: Player.doc?.id ?: Library.lastDoc(this)
        val autoPlay = intent.getBooleanExtra(EXTRA_AUTO_PLAY, false)
        val fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)
        // Para no reabrir ni reiniciar al girar la pantalla o volver del aviso.
        intent.removeExtra(EXTRA_AUTO_PLAY)
        intent.removeExtra(EXTRA_FROM_START)
        if (id == null) {
            finish()
            return
        }
        val current = Player.doc
        if (current != null && current.id == id && !fromStart) {
            if (autoPlay) Player.play()
            return
        }
        val doc = Library.load(this, id)
        if (doc == null) {
            finish()
            return
        }
        Player.open(this, doc, Library.position(this, id), autoPlay)
    }

    override fun onResume() {
        super.onResume()
        Player.addListener(this)
        onPlayerChanged()
    }

    override fun onPause() {
        Player.removeListener(this)
        super.onPause()
    }

    override fun onPlayerChanged() {
        val doc = Player.doc
        if (doc == null) {
            finish()
            return
        }
        if (shownDoc != doc.id) {
            shownDoc = doc.id
            shownSection = -1
            shownIndex = -1
            title = doc.title
            headingLevel = IntArray(doc.sentences.size)
            for (section in doc.sections) {
                if (section.titleSpoken && !section.mark) headingLevel[section.start] = section.level
            }
            adapter.notifyDataSetChanged()
            textAdapter.notifyDataSetChanged()
        }
        val index = Player.index
        val sectionIndex = Player.section
        val total = doc.sentences.size
        val percent = if (total > 1) index * 100 / (total - 1) else 0

        nowSection.text = doc.headingAt(index)?.title ?: doc.title
        nowSentence.text = doc.sentences.getOrNull(index).orEmpty()
        position.text = getString(R.string.position, index + 1, total, percent)
        progress.max = (total - 1).coerceAtLeast(1)
        progress.progress = index
        speed.text = DecimalFormat("0.0#").format(Player.rate) + "x"

        val looping = Player.loop != Player.Loop.OFF
        loop.setText(
            when (Player.loop) {
                Player.Loop.OFF -> R.string.loop_off
                Player.Loop.FRAGMENT -> R.string.loop_fragment
                Player.Loop.SECTION -> R.string.loop_section
                Player.Loop.CUSTOM -> R.string.loop_custom
            }
        )
        val loopColor = getColor(if (looping) R.color.loop_on else R.color.tab_idle)
        loop.setTextColor(loopColor)
        loop.compoundDrawableTintList = ColorStateList.valueOf(loopColor)
        loop.setTypeface(null, if (looping) Typeface.BOLD else Typeface.NORMAL)
        // El tramo que se repite se pinta en el texto; se redibuja cuando cambia.
        val loopKey = if (looping) "${Player.loopStart}-${Player.loopEnd}" else ""
        if (loopKey != shownLoop) {
            shownLoop = loopKey
            textAdapter.notifyDataSetChanged()
        }

        val playing = Player.state == Player.State.PLAYING
        playPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        playPause.contentDescription = getString(if (playing) R.string.pause else R.string.play)

        if (index != shownIndex) {
            val first = shownIndex < 0
            shownIndex = index
            textAdapter.notifyDataSetChanged()
            centerText(animated = !first)
        }

        if (sectionIndex != shownSection) {
            shownSection = sectionIndex
            adapter.notifyDataSetChanged()
            // Deja el apartado actual a la vista, con uno antes para dar contexto.
            sections.post { sections.smoothScrollToPositionFromTop((sectionIndex - 1).coerceAtLeast(0), 0, 200) }
        }
    }

    private fun showTab(text: Boolean, remember: Boolean = true) {
        showingText = text
        this.text.visibility = if (text) View.VISIBLE else View.GONE
        sections.visibility = if (text) View.GONE else View.VISIBLE
        // En la vista de texto la frase actual ya se ve resaltada: no se repite en el panel de abajo.
        nowSentence.visibility = if (text) View.GONE else View.VISIBLE
        for ((button, selected) in listOf(tabText to text, tabIndex to !text)) {
            button.setTextColor(getColor(if (selected) R.color.accent else R.color.tab_idle))
            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
        if (remember) getSharedPreferences("avance", MODE_PRIVATE).edit().putBoolean("_vista_texto", text).apply()
        if (text) {
            touchedAt = NEVER
            centerText(animated = false)
        } else if (shownSection >= 0) {
            sections.post { sections.setSelection((shownSection - 1).coerceAtLeast(0)) }
        }
    }

    /** Deja la frase actual a un tercio de la pantalla, salvo que se esté explorando el texto a mano. */
    private fun centerText(animated: Boolean) {
        if (shownIndex < 0) return
        if (SystemClock.elapsedRealtime() - touchedAt < MANUAL_SCROLL_HOLD_MS) return
        text.post {
            val offset = text.height / 3
            if (animated && kotlin.math.abs(shownIndex - text.firstVisiblePosition) < 30) {
                text.smoothScrollToPositionFromTop(shownIndex, offset, 300)
            } else {
                text.setSelectionFromTop(shownIndex, offset)
            }
        }
    }

    private fun inLoopRange(position: Int) =
        Player.loop != Player.Loop.OFF && position in Player.loopStart..Player.loopEnd

    private val textAdapter = object : BaseAdapter() {
        override fun getCount() = Player.doc?.sentences?.size ?: 0
        override fun getItem(position: Int) = Player.doc?.sentences?.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView ?: layoutInflater.inflate(R.layout.row_sentence, parent, false)) as TextView
            val sentence = Player.doc?.sentences?.getOrNull(position) ?: return view
            val current = position == shownIndex
            val level = headingLevel.getOrElse(position) { 0 }
            val density = resources.displayMetrics.density
            view.text = sentence
            // La frase que se está leyendo resalta; el resto queda atenuado, como la letra de una canción.
            view.alpha = if (current) 1f else if (inLoopRange(position)) 0.8f else 0.5f
            view.setTypeface(null, if (current || level > 0) Typeface.BOLD else Typeface.NORMAL)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (level == 1) 22f else if (level > 1) 20f else 19f)
            view.setTextColor(getColor(if (level > 0) R.color.accent else R.color.text))
            val inLoop = (Player.loop != Player.Loop.OFF && position in Player.loopStart..Player.loopEnd) ||
                position == loopAnchor
            view.setBackgroundColor(
                when {
                    current -> getColor(R.color.current_row)
                    inLoop -> getColor(R.color.loop_row)
                    else -> 0
                }
            )
            // Aire antes de cada título para que se distinga dónde empieza un apartado.
            val top = if (level > 0 && position > 0) 26 else 9
            view.setPaddingRelative(view.paddingStart, (top * density).toInt(), view.paddingEnd, view.paddingBottom)
            return view
        }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = Player.doc?.sections?.size ?: 0
        override fun getItem(position: Int) = Player.doc?.sections?.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView ?: layoutInflater.inflate(R.layout.row_section, parent, false)) as TextView
            val section = Player.doc?.sections?.getOrNull(position) ?: return view
            val current = position == shownSection
            val density = resources.displayMetrics.density
            val indent = (16 + (section.level - 1).coerceIn(0, 4) * 18) * density
            view.setPaddingRelative(indent.toInt(), view.paddingTop, view.paddingEnd, view.paddingBottom)
            view.text = if (current) "▶  ${section.title}" else section.title
            // Los títulos del documento van en negrita; los puntos intermedios, más discretos.
            view.setTypeface(null, if (section.mark) Typeface.NORMAL else Typeface.BOLD)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (section.mark) 15f else 17f)
            view.minHeight = ((if (section.mark) 48 else 56) * density).toInt()
            view.alpha = if (section.mark && !current) 0.75f else 1f
            view.setBackgroundColor(if (current) getColor(R.color.current_row) else 0)
            return view
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_VOICE, 0, R.string.voice_settings)
        menu.add(0, MENU_CLOSE, 1, R.string.close)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        MENU_VOICE -> {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: ActivityNotFoundException) {
                // El teléfono no tiene esa pantalla.
            }
            true
        }
        MENU_CLOSE -> {
            Player.close()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Solo se pide una vez: sirve para ver los controles en la barra de avisos y la pantalla de bloqueo. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("avance", MODE_PRIVATE)
        if (prefs.getBoolean("_aviso_pedido", false)) return
        prefs.edit().putBoolean("_aviso_pedido", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
