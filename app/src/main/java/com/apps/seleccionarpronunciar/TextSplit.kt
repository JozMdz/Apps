package com.apps.seleccionarpronunciar

import android.graphics.Rect
import java.text.BreakIterator
import java.util.Locale

/** Utilidades para trabajar con frases dentro de un texto. */
object TextSplit {

    /** Una frase y su posición [start, end) en el texto original. */
    class Piece(val text: String, val start: Int, val end: Int)

    fun sentences(text: String): List<Piece> {
        val out = ArrayList<Piece>()
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        iterator.setText(text)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            var s = start
            var e = end
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (e > s) out.add(Piece(text.substring(s, e), s, e))
            start = end
            end = iterator.next()
        }
        return out
    }

    /**
     * Elige frases según el modo de toque:
     * la frase tocada ([Prefs.TAP_SENTENCE]), desde ella hasta el final ([Prefs.TAP_FROM]) o todas.
     * [boundsOf] da el rectángulo en pantalla de un rango de caracteres (o null si no se sabe).
     */
    fun pickForTap(
        pieces: List<Piece>, tappedIndex: Int, mode: String, fallback: Rect, boundsOf: (Int, Int) -> Rect?,
    ): List<Segment> {
        val chosen = when (mode) {
            Prefs.TAP_SENTENCE -> listOf(pieces[tappedIndex])
            Prefs.TAP_FROM -> pieces.subList(tappedIndex, pieces.size)
            else -> pieces
        }
        return chosen.map { Segment(it.text, boundsOf(it.start, it.end) ?: fallback) }
    }
}
