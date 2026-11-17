package com.apps.estudioaudio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.Executors

/** Importa un PDF, Markdown o texto y lo deja listo en la biblioteca. */
object Importer {

    sealed class Result {
        class Ok(val doc: Doc) : Result()
        class Failed(val messageRes: Int) : Result()
    }

    private const val MAX_BYTES = 80L * 1024 * 1024
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun importUri(context: Context, uri: Uri, callback: (Result) -> Unit) = run(callback) {
        val app = context.applicationContext
        val name = displayName(app, uri)
        val temp = File.createTempFile("importar", null, app.cacheDir)
        try {
            val input = app.contentResolver.openInputStream(uri) ?: return@run Result.Failed(R.string.import_cannot_open)
            input.use { stream ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return@run Result.Failed(R.string.import_too_big)
                        out.write(buffer, 0, read)
                    }
                }
            }
            fromFile(app, temp, name)
        } finally {
            temp.delete()
        }
    }

    fun importText(context: Context, text: String, title: String, callback: (Result) -> Unit) = run(callback) {
        finish(context.applicationContext, MarkdownParser.parse(text), title)
    }

    private fun run(callback: (Result) -> Unit, work: () -> Result) {
        executor.execute {
            val result = try {
                work()
            } catch (e: PdfExtractor.ProtectedPdfException) {
                Result.Failed(R.string.import_protected)
            } catch (e: OutOfMemoryError) {
                Result.Failed(R.string.import_too_big)
            } catch (e: Exception) {
                android.util.Log.e("EstudioAudio", "No se pudo importar", e)
                Result.Failed(R.string.import_cannot_open)
            }
            main.post { callback(result) }
        }
    }

    private fun fromFile(context: Context, file: File, name: String): Result {
        val fallbackTitle = name.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().ifEmpty { name }
        val head = ByteArray(5)
        val headSize = file.inputStream().use { it.read(head) }
        if (headSize >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "%PDF") {
            val pdf = PdfExtractor.extract(context, file)
            if (pdf.lines.sumOf { it.text.length } < 20) return Result.Failed(R.string.import_scanned)
            return finish(context, PdfStructure.toBlocks(pdf.lines, pdf.outline), fallbackTitle)
        }
        val bytes = file.readBytes()
        if (bytes.take(4000).count { it == 0.toByte() } > 4) return Result.Failed(R.string.import_unsupported)
        return finish(context, MarkdownParser.parse(String(bytes, Charsets.UTF_8)), fallbackTitle)
    }

    private fun finish(context: Context, blocks: List<Block>, fallbackTitle: String): Result {
        val doc = DocBuilder.build(blocks, fallbackTitle)
        if (doc.sentences.isEmpty()) return Result.Failed(R.string.import_empty)
        Library.save(context, doc)
        return Result.Ok(doc)
    }

    private fun displayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
            }
        } catch (e: Exception) {
            // Algunos proveedores no dan el nombre.
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Documento"
    }
}
