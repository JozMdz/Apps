package com.apps.estudioaudio

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView

/** Pantalla principal: los documentos guardados, cada uno con su avance. */
class LibraryActivity : Activity(), Player.Listener {

    companion object {
        private const val REQUEST_PICK = 1
    }

    private lateinit var list: ListView
    private lateinit var empty: View
    private lateinit var continueButton: Button
    private var entries: List<Library.Entry> = emptyList()
    private val sectionNames = HashMap<String, String>()

    @Suppress("DEPRECATION")
    private var progress: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        continueButton = findViewById(R.id.continue_button)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> openPlayer(entries[position].id, autoPlay = true) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            showOptions(entries[position])
            true
        }
        findViewById<Button>(R.id.add_button).setOnClickListener { pickDocument() }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Player.addListener(this)
        refresh()
    }

    override fun onPause() {
        Player.removeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        progress?.dismiss()
        progress = null
        super.onDestroy()
    }

    override fun onPlayerChanged() = refresh()

    // ---------------------------------------------------------------- lista

    private fun refresh() {
        entries = Library.entries(this)
        empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        adapter.notifyDataSetChanged()

        val last = Library.lastDoc(this)?.let { id -> entries.firstOrNull { it.id == id } }
        if (last != null) {
            continueButton.visibility = View.VISIBLE
            continueButton.text = getString(R.string.continue_last, last.title)
            continueButton.setOnClickListener { openPlayer(last.id, autoPlay = true) }
        } else {
            continueButton.visibility = View.GONE
        }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_document, parent, false)
            val entry = entries[position]
            val at = Library.position(this@LibraryActivity, entry.id)
            val percent = if (entry.total > 1) at * 100 / (entry.total - 1) else 0
            view.findViewById<TextView>(R.id.title).text = entry.title
            view.findViewById<TextView>(R.id.subtitle).text =
                if (at == 0) getString(R.string.entry_new, entry.sections)
                else getString(R.string.entry_progress, entry.sections, percent, sectionName(entry, at))
            view.findViewById<ProgressBar>(R.id.progress).progress = percent
            return view
        }
    }

    /** Nombre del apartado en que se quedó un documento (se carga una vez y se recuerda). */
    private fun sectionName(entry: Library.Entry, at: Int): String {
        val key = "${entry.id}:$at"
        return sectionNames.getOrPut(key) {
            val doc = if (Player.doc?.id == entry.id) Player.doc else Library.load(this, entry.id)
            doc?.headingAt(at)?.title ?: ""
        }
    }

    private fun showOptions(entry: Library.Entry) {
        AlertDialog.Builder(this)
            .setTitle(entry.title)
            .setItems(arrayOf(getString(R.string.restart), getString(R.string.delete))) { _, which ->
                if (which == 0) {
                    Library.setPosition(this, entry.id, 0)
                    openPlayer(entry.id, autoPlay = true, fromStart = true)
                } else {
                    if (Player.doc?.id == entry.id) Player.close()
                    Library.delete(this, entry.id)
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openPlayer(id: String, autoPlay: Boolean, fromStart: Boolean = false) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_DOC_ID, id)
                .putExtra(PlayerActivity.EXTRA_AUTO_PLAY, autoPlay)
                .putExtra(PlayerActivity.EXTRA_FROM_START, fromStart)
        )
    }

    // ---------------------------------------------------------------- importar

    private fun pickDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/pdf", "text/markdown", "text/x-markdown", "text/plain", "application/octet-stream"),
            )
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PICK)
        } catch (e: ActivityNotFoundException) {
            showError(R.string.import_cannot_open)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK) data?.data?.let { importUri(it) }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { importUri(it) }
            Intent.ACTION_SEND -> {
                val stream: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                when {
                    stream != null -> importUri(stream)
                    !text.isNullOrBlank() -> {
                        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.shared_text_title)
                        showProgress()
                        Importer.importText(this, text, title, ::onImported)
                    }
                }
            }
        }
        // Para no volver a importar lo mismo al girar la pantalla o regresar.
        intent.action = Intent.ACTION_MAIN
    }

    private fun importUri(uri: Uri) {
        showProgress()
        Importer.importUri(this, uri, ::onImported)
    }

    @Suppress("DEPRECATION")
    private fun showProgress() {
        progress?.dismiss()
        progress = ProgressDialog(this).apply {
            setMessage(getString(R.string.importing))
            setCancelable(false)
            show()
        }
    }

    private fun onImported(result: Importer.Result) {
        if (isDestroyed) return
        progress?.dismiss()
        progress = null
        when (result) {
            is Importer.Result.Ok -> {
                refresh()
                openPlayer(result.doc.id, autoPlay = true)
            }
            is Importer.Result.Failed -> showError(result.messageRes)
        }
    }

    private fun showError(messageRes: Int) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setMessage(messageRes).setPositiveButton(R.string.ok, null).show()
    }
}
