package com.apps.estudioaudio

/**
 * Convierte Markdown (o texto plano) en títulos y párrafos limpios para leer en voz alta:
 * sin símbolos de formato, sin direcciones web y con las tablas y listas como frases.
 */
object MarkdownParser {

    /** Marca invisible que algunos editores ponen al inicio del archivo. */
    private const val BOM = "\uFEFF"

    private val ATX = Regex("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val SETEXT_1 = Regex("^ {0,3}=+\\s*$")
    private val SETEXT_2 = Regex("^ {0,3}-{2,}\\s*$")
    private val RULE = Regex("^ {0,3}([-*_])( *\\1){2,}\\s*$")
    private val FENCE = Regex("^ {0,3}(```|~~~).*$")
    private val LIST_ITEM = Regex("^\\s*(?:[-*+•]|(\\d+)[.)])\\s+(.*)$")
    private val TASK = Regex("^\\[[ xX]]\\s+")
    private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
    private val QUOTE = Regex("^\\s*(>\\s?)+")

    private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val REF_LINK = Regex("\\[([^\\]]+)]\\[[^\\]]*]")
    private val FOOTNOTE = Regex("\\[\\^[^\\]]+]")
    private val AUTOLINK = Regex("<(?:https?://|mailto:)[^>]+>")
    private val URL = Regex("(?:https?://|www\\.)\\S+")
    private val HTML_TAG = Regex("</?[a-zA-Z][^>]*>")
    private val CODE = Regex("`+([^`]*)`+")
    private val STRONG = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1")
    private val EMPHASIS_STAR = Regex("\\*(?=\\S)([^*]+?)(?<=\\S)\\*")
    private val EMPHASIS_UNDERSCORE = Regex("(?<![\\p{L}\\p{N}])_(?=\\S)([^_]+?)(?<=\\S)_(?![\\p{L}\\p{N}])")
    private val STRIKE = Regex("~~(.+?)~~")
    private val ESCAPE = Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!|>~])")
    private val SPACES = Regex("\\s+")

    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'",
        "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
    )

    fun parse(source: String): List<Block> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').removePrefix(BOM).split('\n')
        val blocks = ArrayList<Block>()
        val paragraph = ArrayList<String>()
        var inFence = false
        var i = 0

        fun flush() {
            if (paragraph.isEmpty()) return
            val text = clean(paragraph.joinToString(" "))
            paragraph.clear()
            if (text.isNotEmpty()) blocks.add(Block.Paragraph(ensureStop(text)))
        }

        // Metadatos al inicio (--- ... ---).
        if (lines.firstOrNull()?.trim() == "---") {
            val close = (1 until lines.size).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." }
            if (close != null) i = close + 1
        }

        while (i < lines.size) {
            val raw = lines[i++]
            if (FENCE.matches(raw)) {
                if (!inFence) {
                    flush()
                    blocks.add(Block.Paragraph("Bloque de código omitido."))
                }
                inFence = !inFence
                continue
            }
            if (inFence) continue

            val line = QUOTE.replace(raw, "")
            if (line.isBlank()) {
                flush()
                continue
            }

            val atx = ATX.matchEntire(line)
            if (atx != null) {
                flush()
                val text = clean(atx.groupValues[2])
                if (text.isNotEmpty()) blocks.add(Block.Heading(atx.groupValues[1].length, text))
                continue
            }
            // Título subrayado: una sola línea de texto seguida de === o ---.
            if (paragraph.size == 1 && (SETEXT_1.matches(line) || SETEXT_2.matches(line))) {
                val text = clean(paragraph[0])
                paragraph.clear()
                if (text.isNotEmpty()) blocks.add(Block.Heading(if (SETEXT_1.matches(line)) 1 else 2, text))
                continue
            }
            if (RULE.matches(line)) {
                flush()
                continue
            }
            if (TABLE_SEPARATOR.matches(line) && line.contains('-') && (line.contains('|') || paragraph.isEmpty())) {
                // Línea |---|---| de una tabla: el encabezado ya quedó como fila.
                flush()
                continue
            }
            if (line.trim().startsWith("|") || (line.count { it == '|' } >= 2)) {
                flush()
                val cells = line.trim().trim('|').split('|').map { clean(it) }.filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) blocks.add(Block.Paragraph(ensureStop(cells.joinToString(", "))))
                continue
            }
            val item = LIST_ITEM.matchEntire(line)
            if (item != null) {
                flush()
                val number = item.groupValues[1]
                val body = TASK.replace(item.groupValues[2], "")
                paragraph.add(if (number.isEmpty()) body else "$number. $body")
                continue
            }
            paragraph.add(line.trim())
        }
        flush()
        return blocks
    }

    /** Quita la sintaxis de Markdown dejando solo lo que se debe oír. */
    fun clean(text: String): String {
        var s = text
        s = IMAGE.replace(s) { m -> m.groupValues[1].ifBlank { "imagen" } }
        s = LINK.replace(s, "$1")
        s = REF_LINK.replace(s, "$1")
        s = FOOTNOTE.replace(s, "")
        s = AUTOLINK.replace(s, "enlace")
        s = URL.replace(s, "enlace")
        s = HTML_TAG.replace(s, " ")
        s = CODE.replace(s, "$1")
        s = STRONG.replace(s, "$2")
        s = STRIKE.replace(s, "$1")
        s = EMPHASIS_STAR.replace(s, "$1")
        s = EMPHASIS_UNDERSCORE.replace(s, "$1")
        s = ESCAPE.replace(s, "$1")
        for ((entity, value) in ENTITIES) s = s.replace(entity, value)
        return SPACES.replace(s, " ").trim()
    }

    /** Las líneas sin punto final (listas, celdas) se cierran para que la voz haga pausa. */
    private fun ensureStop(text: String): String =
        if (text.last().isLetterOrDigit() || text.last() == ')' || text.last() == '»' || text.last() == '"') "$text." else text
}
