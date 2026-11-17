package com.apps.estudioaudio

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.Writer

/** Saca de un PDF sus líneas de texto (con tamaño y negrita), su índice incrustado y su título. */
object PdfExtractor {

    class Result(val lines: List<PdfLine>, val outline: List<Pair<String, Int>>, val title: String?, val pages: Int)

    /** El PDF está protegido con contraseña. */
    class ProtectedPdfException : Exception()

    fun extract(context: Context, file: File): Result {
        PDFBoxResourceLoader.init(context.applicationContext)
        val document = try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw ProtectedPdfException()
        }
        document.use { doc ->
            val stripper = LineStripper()
            stripper.writeText(doc, NullWriter)
            val outline = ArrayList<Pair<String, Int>>()
            try {
                doc.documentCatalog?.documentOutline?.let { collectOutline(it, 1, outline) }
            } catch (e: Exception) {
                outline.clear() // marcadores dañados: se deduce el índice por la letra
            }
            val title = try {
                doc.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
            return Result(stripper.lines, outline, title, doc.numberOfPages)
        }
    }

    private fun collectOutline(node: PDOutlineNode, level: Int, out: MutableList<Pair<String, Int>>) {
        if (level > 6 || out.size > 2000) return
        for (item in node.children()) {
            val title = item.title?.trim()
            if (!title.isNullOrEmpty()) out.add(title to level)
            collectOutline(item, level + 1, out)
        }
    }

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

    /** Junta las palabras que entrega PDFBox en líneas, anotando letra y saltos de párrafo. */
    private class LineStripper : PDFTextStripper() {
        val lines = ArrayList<PdfLine>()
        private val text = StringBuilder()
        private val positions = ArrayList<TextPosition>()
        private var paragraphStart = true
        private var page = 0

        init {
            sortByPosition = true
        }

        override fun writePageStart() {
            page++
            paragraphStart = true
            super.writePageStart()
        }

        override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
            text.append(string)
            positions.addAll(textPositions)
            super.writeString(string, textPositions)
        }

        override fun writeWordSeparator() {
            text.append(' ')
            super.writeWordSeparator()
        }

        override fun writeLineSeparator() {
            flushLine()
            super.writeLineSeparator()
        }

        override fun writeParagraphStart() {
            flushLine()
            paragraphStart = true
            super.writeParagraphStart()
        }

        override fun writeParagraphEnd() {
            flushLine()
            super.writeParagraphEnd()
        }

        override fun writePageEnd() {
            flushLine()
            super.writePageEnd()
        }

        private fun flushLine() {
            val line = text.toString().replace(' ', ' ').trim()
            if (line.isNotEmpty()) {
                var sizeSum = 0f
                var counted = 0
                var bold = 0
                for (p in positions) {
                    if (p.unicode.isNullOrBlank()) continue
                    sizeSum += p.fontSizeInPt
                    counted++
                    if (isBold(p)) bold++
                }
                val size = if (counted > 0) sizeSum / counted else 0f
                lines.add(PdfLine(line, size, counted > 0 && bold * 10 >= counted * 9, paragraphStart, page))
                paragraphStart = false
            }
            text.setLength(0)
            positions.clear()
        }

        private fun isBold(position: TextPosition): Boolean {
            val font = position.font ?: return false
            val name = font.name?.lowercase().orEmpty()
            if ("bold" in name || "black" in name || "heavy" in name || "semibold" in name || "demi" in name) return true
            val descriptor = font.fontDescriptor ?: return false
            return descriptor.isForceBold || descriptor.fontWeight >= 600f
        }
    }
}
