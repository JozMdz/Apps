package com.apps.estudioaudio

import java.security.MessageDigest
import java.util.Locale

/** Convierte la lista de títulos y párrafos en un [Doc] con frases e índice. */
object DocBuilder {

    /** Sin títulos en el documento, se crea un apartado "Parte N" cada tantas frases. */
    private const val AUTO_PART_SIZE = 30

    fun build(blocks: List<Block>, fallbackTitle: String, locale: Locale = Locale.getDefault()): Doc {
        val sentences = ArrayList<String>()
        val sections = ArrayList<Section>()
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
                    sentences.addAll(pieces)
                    sinceAutoPart += pieces.size
                }
            }
        }

        val title = blocks.firstNotNullOfOrNull { (it as? Block.Heading)?.takeIf { h -> h.level == 1 }?.text }
            ?: fallbackTitle
        normalizeLevels(sections)
        return Doc(idOf(title, sentences), title.trim().ifEmpty { fallbackTitle }, sentences, sections)
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
