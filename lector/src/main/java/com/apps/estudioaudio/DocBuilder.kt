package com.apps.estudioaudio

import java.security.MessageDigest
import java.util.Locale

/** Convierte la lista de títulos y párrafos en un [Doc] con frases e índice. */
object DocBuilder {

    /** Sin títulos en el documento, se crea un apartado "Parte N" cada tantas frases. */
    private const val AUTO_PART_SIZE = 30

    /** Los apartados con más frases que esto reciben puntos intermedios. */
    private const val LONG_SECTION = 6

    /** Frases mínimas entre un punto del índice y el siguiente, y antes del final del apartado. */
    private const val MARK_GAP = 3
    private const val MARK_TAIL = 2

    /** Texto mínimo (en caracteres) entre puntos, para no llenar el índice en listas de frases cortas. */
    private const val MARK_CHARS = 200

    /** Largo máximo de una línea corta que encabeza un párrafo. */
    private const val LEAD_LENGTH = 80
    private const val MARK_LABEL = 60

    fun build(blocks: List<Block>, fallbackTitle: String, locale: Locale = Locale.getDefault()): Doc {
        val sentences = ArrayList<String>()
        val sections = ArrayList<Section>()
        val paragraphStarts = ArrayList<Int>()
        val hasHeadings = blocks.any { it is Block.Heading }
        var sinceAutoPart = 0

        for (block in blocks) {
            when (block) {
                is Block.Heading -> {
                    val title = block.text.trim()
                    if (title.isEmpty()) continue
                    sections.add(Section(title, block.level, sentences.size, titleSpoken = true))
                    // El título se lee como una frase más: así se oye en qué apartado se entra.
                    sentences.add(if (title.last().isLetterOrDigit()) "$title." else title)
                }
                is Block.Paragraph -> {
                    val pieces = Sentences.split(block.text, locale)
                    if (pieces.isEmpty()) continue
                    if (!hasHeadings && (sections.isEmpty() || sinceAutoPart >= AUTO_PART_SIZE)) {
                        sections.add(Section("Parte ${sections.size + 1}", 1, sentences.size, titleSpoken = false))
                        sinceAutoPart = 0
                    } else if (sections.isEmpty()) {
                        // Texto antes del primer título.
                        sections.add(Section("Inicio", 1, 0, titleSpoken = false))
                    }
                    paragraphStarts.add(sentences.size)
                    sentences.addAll(pieces)
                    sinceAutoPart += pieces.size
                }
            }
        }

        // El título del documento es su título principal solo si es único; si el nivel más alto se usa
        // para los apartados ("# 1. …", "# 2. …"), el nombre del archivo describe mejor el documento.
        val headings = blocks.filterIsInstance<Block.Heading>().filter { it.text.isNotBlank() }
        val top = headings.minOfOrNull { it.level }
        val title = headings.singleOrNull { it.level == top }?.takeIf { it === headings.first() }?.text?.trim()
            ?: fallbackTitle
        normalizeLevels(sections)
        return Doc(idOf(title, sentences), title, sentences, withMarks(sections, paragraphStarts, sentences))
    }

    /**
     * Añade puntos intermedios dentro de los apartados largos, al inicio de párrafos, para poder volver
     * a un punto cercano sin regresar hasta el título. Se nombran con las primeras palabras del párrafo.
     * Una línea corta que encabeza un párrafo ("Nota: figura 2", "Ejemplo") va junto con él y es el
     * lugar preferido para el punto.
     */
    private fun withMarks(sections: List<Section>, paragraphStarts: List<Int>, sentences: List<String>): List<Section> {
        // lengthUpTo[i] = caracteres de las frases anteriores a la i.
        val lengthUpTo = IntArray(sentences.size + 1)
        for (i in sentences.indices) lengthUpTo[i + 1] = lengthUpTo[i] + sentences[i].length
        fun paragraphEnd(k: Int) = paragraphStarts.getOrNull(k + 1) ?: sentences.size
        fun isLead(k: Int): Boolean {
            val start = paragraphStarts[k]
            if (paragraphEnd(k) - start != 1 || sentences[start].length > LEAD_LENGTH) return false
            if (k + 1 >= paragraphStarts.size) return false
            val nextEnd = paragraphEnd(k + 1)
            return nextEnd - paragraphStarts[k + 1] >= 2 || lengthUpTo[nextEnd] - lengthUpTo[paragraphStarts[k + 1]] > 120
        }

        val result = ArrayList<Section>()
        var k = 0
        for ((i, section) in sections.withIndex()) {
            result.add(section)
            val end = sections.getOrNull(i + 1)?.start ?: sentences.size
            while (k < paragraphStarts.size && paragraphStarts[k] <= section.start) k++
            val long = end - section.start > LONG_SECTION
            var last = section.start
            var afterLead = false // este párrafo va unido a la línea corta anterior
            while (k < paragraphStarts.size && paragraphStarts[k] < end) {
                val at = paragraphStarts[k]
                val lead = isLead(k) && paragraphStarts[k + 1] < end
                val attached = afterLead
                afterLead = lead
                k++
                if (!long || attached) continue
                if (at - last < MARK_GAP || end - at < MARK_TAIL || lengthUpTo[at] - lengthUpTo[last] < MARK_CHARS) continue
                // Si enseguida viene una línea corta que encabeza párrafo, el punto va mejor ahí.
                if (!lead) {
                    val next = (k until paragraphStarts.size).firstOrNull { paragraphStarts[it] < end && isLead(it) }
                    if (next != null && paragraphStarts[next] - at < MARK_GAP && end - paragraphStarts[next] >= MARK_TAIL) continue
                }
                result.add(Section(label(sentences[at]), section.level + 1, at, titleSpoken = false, mark = true))
                last = at
            }
        }
        return result
    }

    private fun label(sentence: String): String {
        if (sentence.length <= MARK_LABEL) return sentence
        val cut = sentence.lastIndexOf(' ', MARK_LABEL).takeIf { it > MARK_LABEL / 2 } ?: MARK_LABEL
        return sentence.substring(0, cut).trimEnd(',', ';', ':', ' ') + "…"
    }

    /** Hace que el nivel más alto usado sea 1 (para que la sangría del índice empiece al margen). */
    private fun normalizeLevels(sections: MutableList<Section>) {
        val min = sections.minOfOrNull { it.level } ?: return
        if (min <= 1) return
        for (i in sections.indices) {
            val s = sections[i]
            sections[i] = Section(s.title, s.level - min + 1, s.start, s.titleSpoken)
        }
    }

    /** Identificador estable según el contenido: volver a importar el mismo archivo conserva el avance. */
    private fun idOf(title: String, sentences: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(title.toByteArray())
        for (s in sentences) digest.update(s.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}
