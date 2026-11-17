package com.apps.estudioaudio

import java.text.BreakIterator
import java.util.Locale

/** Divide párrafos en frases cómodas de escuchar y de saltar. */
object Sentences {

    private const val MAX_LENGTH = 3000
    private const val SHORT = 25
    private val NUMBERING = Regex("^\\(?(?:\\d{1,3}|[a-zA-Z]|[ivxIVX]{1,4})[.)]$")

    /** Abreviaturas tras las que no se debe cortar la frase. */
    private val ABBREVIATIONS = setOf(
        "fig", "figs", "dr", "dra", "sr", "sra", "srta", "lic", "ing", "mtro", "mtra", "prof", "art", "arts",
        "núm", "num", "no", "nº", "pág", "págs", "pag", "p", "pp", "cap", "caps", "vol", "ed", "eds", "vs",
        "etc", "ej", "aprox", "tel", "ee", "uu", "av", "col", "cd", "gral", "ud", "uds", "cf", "ca", "op",
        "cit", "al", "tab", "ec", "sec", "inc", "ltd", "mr", "mrs", "ms", "jr", "st", "e.g", "i.e",
    )

    fun split(paragraph: String, locale: Locale = Locale.getDefault()): List<String> {
        val text = paragraph.trim()
        if (text.isEmpty()) return emptyList()
        val raw = ArrayList<String>()
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val piece = text.substring(start, end).trim()
            if (piece.isNotEmpty()) raw.add(piece)
            start = end
            end = iterator.next()
        }

        // Une los cortes falsos tras abreviaturas ("Fig. 1", "Dr. Pérez") o iniciales ("J. Pérez").
        val merged = ArrayList<String>()
        for (piece in raw) {
            val previous = merged.lastOrNull()
            if (previous != null && endsWithAbbreviation(previous)) {
                merged[merged.lastIndex] = "$previous $piece"
            } else {
                merged.add(piece)
            }
        }
        return joinShort(merged).flatMap { chunk(it) }
    }

    /**
     * Las frases muy cortas («Buenos días.», «Fig. 1») se unen a su vecina del mismo párrafo:
     * así cada salto de "frase anterior / siguiente" avanza algo que vale la pena.
     */
    private fun joinShort(pieces: List<String>): List<String> {
        if (pieces.size < 2) return pieces
        val out = ArrayList<String>()
        var carry: String? = null // frase corta al inicio, pendiente de unirse a la siguiente
        for (piece in pieces) {
            val current = if (carry != null) "$carry $piece" else piece
            carry = null
            when {
                current.length >= SHORT -> out.add(current)
                out.isNotEmpty() -> out[out.lastIndex] = "${out.last()} $current"
                else -> carry = current
            }
        }
        if (carry != null) out.add(carry)
        return out
    }

    private fun endsWithAbbreviation(sentence: String): Boolean {
        if (!sentence.endsWith('.')) return false
        val word = sentence.dropLast(1).takeLastWhile { !it.isWhitespace() && it != '(' && it != '«' && it != '"' }
        if (word.isEmpty()) return false
        if (NUMBERING.matches(sentence.trim())) return true // numeración de lista: "1.", "a.", "ii."
        if (word.length == 1 && word[0].isUpperCase()) return true // inicial de un nombre
        return word.lowercase() in ABBREVIATIONS
    }

    /** El motor de voz tiene un máximo por frase: las muy largas se parten en espacios. */
    private fun chunk(sentence: String): List<String> {
        if (sentence.length <= MAX_LENGTH) return listOf(sentence)
        val parts = ArrayList<String>()
        var rest = sentence
        while (rest.length > MAX_LENGTH) {
            var cut = rest.lastIndexOf(' ', MAX_LENGTH)
            if (cut <= 0) cut = MAX_LENGTH
            parts.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts.add(rest)
        return parts
    }
}
