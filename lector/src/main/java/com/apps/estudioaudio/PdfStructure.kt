package com.apps.estudioaudio

import kotlin.math.roundToInt

/** Una línea de texto de un PDF con los datos necesarios para deducir títulos y párrafos. */
class PdfLine(
    val text: String,
    /** Tamaño de letra en puntos. */
    val size: Float,
    val bold: Boolean,
    /** La línea empieza un párrafo nuevo. */
    val paragraphStart: Boolean,
    val page: Int,
)

/**
 * Deduce la estructura de un PDF a partir de sus líneas: quita encabezados y pies repetidos,
 * detecta títulos (por los marcadores del PDF o por tamaño/negrita de la letra) y une los párrafos.
 */
object PdfStructure {

    private val PAGE_NUMBER = Regex("^(?:p[áa]g(?:ina)?\\.?|page)?\\s*\\d+\\s*(?:(?:de|of|/)\\s*\\d+)?$", RegexOption.IGNORE_CASE)
    private val DIGITS = Regex("\\d+")
    private val SPACES = Regex("\\s+")

    /** [outline]: títulos del índice incrustado del PDF (marcadores) con su nivel, si los tiene. */
    fun toBlocks(source: List<PdfLine>, outline: List<Pair<String, Int>> = emptyList()): List<Block> {
        val lines = removeRunningText(source)
        if (lines.isEmpty()) return emptyList()

        val levels = headingLevels(lines, outline)
        val blocks = ArrayList<Block>()
        val paragraph = StringBuilder()
        var paragraphPage = -1

        fun flush() {
            val text = paragraph.toString().trim()
            paragraph.setLength(0)
            if (text.isNotEmpty()) blocks.add(Block.Paragraph(text))
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val level = levels[i]
            if (level != null) {
                flush()
                // Un título partido en varias líneas seguidas del mismo nivel es un solo título.
                val title = StringBuilder(line.text)
                while (i + 1 < lines.size && levels[i + 1] == level && lines[i + 1].page == line.page &&
                    !lines[i + 1].paragraphStart
                ) {
                    title.append(' ').append(lines[++i].text)
                }
                blocks.add(Block.Heading(level, title.toString()))
                i++
                continue
            }
            if (line.paragraphStart && paragraph.isNotEmpty()) {
                // Un párrafo que sigue en la página siguiente no se corta.
                val continues = line.page != paragraphPage && !endsSentence(paragraph) &&
                    line.text.firstOrNull()?.isLowerCase() == true
                if (!continues) flush()
            }
            append(paragraph, line.text)
            paragraphPage = line.page
            i++
        }
        flush()
        return blocks
    }

    private fun append(paragraph: StringBuilder, text: String) {
        if (paragraph.isEmpty()) {
            paragraph.append(text)
        } else if (paragraph.last() == '-' && paragraph.length >= 2 && paragraph[paragraph.length - 2].isLetter() &&
            text.first().isLowerCase()
        ) {
            // Palabra partida con guion al final del renglón.
            paragraph.setLength(paragraph.length - 1)
            paragraph.append(text)
        } else {
            paragraph.append(' ').append(text)
        }
    }

    private fun endsSentence(text: CharSequence): Boolean {
        val last = text.trimEnd().lastOrNull() ?: return true
        return last == '.' || last == '!' || last == '?' || last == ':' || last == '…' || last == '»' || last == '"'
    }

    /** Quita números de página y encabezados/pies que se repiten en casi todas las páginas. */
    private fun removeRunningText(lines: List<PdfLine>): List<PdfLine> {
        val pages = lines.groupBy { it.page }
        val repeated = HashSet<String>()
        if (pages.size >= 3) {
            val counts = HashMap<String, Int>()
            for (pageLines in pages.values) {
                val edge = (pageLines.take(2) + pageLines.takeLast(2)).map { key(it.text) }.toSet()
                for (k in edge) counts[k] = (counts[k] ?: 0) + 1
            }
            val threshold = maxOf(3, (pages.size * 0.6).roundToInt())
            for ((k, count) in counts) if (count >= threshold) repeated.add(k)
        }
        return lines.filter { line ->
            val text = line.text.trim()
            text.isNotEmpty() && !PAGE_NUMBER.matches(text) && key(text) !in repeated
        }
    }

    private fun key(text: String) = DIGITS.replace(SPACES.replace(text.trim().lowercase(), " "), "#")

    /** Nivel de título de cada línea (null si es texto normal). */
    private fun headingLevels(lines: List<PdfLine>, outline: List<Pair<String, Int>>): Array<Int?> {
        val levels = arrayOfNulls<Int>(lines.size)

        // 1) Marcadores del PDF: son el índice que definió quien hizo el documento.
        if (outline.isNotEmpty()) {
            var matched = 0
            var from = 0
            for ((title, level) in outline) {
                val wanted = key(title)
                if (wanted.isEmpty()) continue
                val at = (from until lines.size).firstOrNull { key(lines[it].text) == wanted }
                    ?: (from until lines.size).firstOrNull { key(lines[it].text).startsWith(wanted) && lines[it].text.length <= title.length + 20 }
                if (at != null) {
                    levels[at] = level.coerceIn(1, 6)
                    matched++
                    from = at + 1
                }
            }
            if (matched >= 2 && matched * 2 >= outline.size) return levels
            levels.fill(null)
        }

        // 2) Por la letra: más grande que el texto normal, o toda la línea en negrita y corta.
        val body = bodySize(lines)
        val bigSizes = lines.filter { isBig(it, body) }.map { rounded(it.size) }.distinct().sortedDescending()
        for ((i, line) in lines.withIndex()) {
            if (isBig(line, body)) {
                levels[i] = (bigSizes.indexOf(rounded(line.size)) + 1).coerceAtMost(4)
            } else if (isBoldTitle(lines, i, body)) {
                levels[i] = (bigSizes.size + 1).coerceAtMost(5)
            }
        }
        return levels
    }

    private fun rounded(size: Float) = (size * 2).roundToInt() / 2f

    /** Tamaño de letra del texto normal: el más usado, contando caracteres. */
    private fun bodySize(lines: List<PdfLine>): Float {
        val weight = HashMap<Float, Int>()
        for (line in lines) weight[rounded(line.size)] = (weight[rounded(line.size)] ?: 0) + line.text.length
        return weight.maxByOrNull { it.value }?.key ?: 0f
    }

    private fun isBig(line: PdfLine, body: Float) =
        body > 0 && line.size >= body * 1.12f && line.text.length <= 150 && line.text.any { it.isLetter() }

    private fun isBoldTitle(lines: List<PdfLine>, i: Int, body: Float): Boolean {
        val line = lines[i]
        if (!line.bold || line.text.length > 80 || line.size < body * 0.98f) return false
        if (!line.text.any { it.isLetter() }) return false
        val last = line.text.last()
        if (last == '.' || last == ',' || last == ';') return false
        // Debe ser una línea suelta en negrita: lo anterior y lo siguiente no son parte del mismo bloque en negrita.
        val previous = lines.getOrNull(i - 1)
        val next = lines.getOrNull(i + 1)
        val startsBlock = previous == null || !previous.bold || line.paragraphStart || previous.page != line.page
        val endsBlock = next == null || !next.bold || next.paragraphStart || next.page != line.page
        return startsBlock && endsBlock
    }
}
