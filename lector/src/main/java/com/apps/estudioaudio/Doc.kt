package com.apps.estudioaudio

/** Un apartado del índice. [start] es la posición de su primera frase. */
class Section(
    val title: String,
    val level: Int,
    val start: Int,
    /** true si la primera frase del apartado es su propio título (se lee al entrar). */
    val titleSpoken: Boolean,
)

/** Un documento listo para escuchar: frases en orden y el índice de apartados. */
class Doc(
    val id: String,
    val title: String,
    val sentences: List<String>,
    val sections: List<Section>,
) {
    /** Índice del apartado al que pertenece la frase [sentence]. */
    fun sectionAt(sentence: Int): Int {
        var lo = 0
        var hi = sections.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (sections[mid].start <= sentence) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Posición de la última frase del apartado [section]. */
    fun sectionEnd(section: Int): Int =
        (sections.getOrNull(section + 1)?.start ?: sentences.size) - 1
}

/** Pieza intermedia del análisis: un título o un párrafo. */
sealed class Block {
    class Heading(val level: Int, val text: String) : Block()
    class Paragraph(val text: String) : Block()
}
