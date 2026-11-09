package com.apps.seleccionarpronunciar

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.hypot

/** Extrae el texto visible de la pantalla a partir del árbol de accesibilidad. */
object ScreenTextReader {

    private const val MAX_DEPTH = 80
    private const val MAX_CHAR_QUERY = 5000

    /**
     * Resultado de explorar un área: el texto encontrado y las zonas grandes sin texto accesible
     * (visores de PDF, imágenes, lienzos…) donde conviene usar reconocimiento de texto (OCR).
     */
    class Scan(val segments: List<Segment>, val blankRegions: List<Rect>)

    /** Un elemento con texto. [raw] es el texto sin recortar (los índices de caracteres se refieren a él). */
    private class Item(val text: String, val raw: String, val bounds: Rect, val node: AccessibilityNodeInfo?) {
        fun whole() = Segment(text, bounds)
    }

    private class WindowRoot(val layer: Int, val bounds: Rect, val root: AccessibilityNodeInfo)

    private class Collected(val items: MutableList<Item> = ArrayList(), val blanks: MutableList<Rect> = ArrayList())

    /** Ventanas legibles, de la más alta (encima) a la más baja. */
    private fun readableWindows(service: AccessibilityService): List<WindowRoot> {
        val result = ArrayList<WindowRoot>()
        val windows = try {
            service.windows
        } catch (e: RuntimeException) {
            emptyList<AccessibilityWindowInfo>()
        }
        for (w in windows) {
            if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val root = w.root ?: continue
            val bounds = Rect().also { w.getBoundsInScreen(it) }
            result.add(WindowRoot(w.layer, bounds, root))
        }
        if (result.isEmpty()) {
            val root = service.rootInActiveWindow
            if (root != null) {
                val bounds = Rect().also { root.getBoundsInScreen(it) }
                result.add(WindowRoot(0, bounds, root))
            }
        }
        result.sortByDescending { it.layer }
        return result
    }

    private fun collect(window: WindowRoot, minBlankArea: Long): Collected {
        val out = Collected()
        collect(window.root, window.bounds, out, minBlankArea, 0)
        return out
    }

    private fun collect(
        node: AccessibilityNodeInfo, clip: Rect, out: Collected, minBlankArea: Long, depth: Int,
    ) {
        if (depth > MAX_DEPTH || !node.isVisibleToUser) return
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (!bounds.intersect(clip)) return

        val raw = if (node.isPassword) null else node.text?.toString()
        val text = raw?.trim()
        if (!text.isNullOrEmpty()) {
            out.items.add(Item(text, raw, bounds, node))
            return
        }

        val before = out.items.size
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, clip, out, minBlankArea, depth + 1)
        }
        if (out.items.size != before) return

        // Si ningún hijo aportó texto, usamos la descripción (p. ej. botones con icono o imágenes).
        val description = node.contentDescription?.toString()?.trim()
        if (!description.isNullOrEmpty()) {
            out.items.add(Item(description, description, bounds, null))
        } else if (bounds.width().toLong() * bounds.height() >= minBlankArea) {
            out.blanks.add(bounds)
        }
    }

    /** Rectángulo que ocupa la pantalla (unión de las ventanas visibles). */
    fun screenBounds(service: AccessibilityService): Rect {
        val screen = Rect()
        for (window in readableWindows(service)) screen.union(window.bounds)
        if (screen.isEmpty) {
            val m = service.resources.displayMetrics
            screen.set(0, 0, m.widthPixels, m.heightPixels)
        }
        return screen
    }

    /**
     * Texto bajo el dedo en (x, y), según el modo de toque (frase, desde la frase o elemento completo).
     * Devuelve null si el elemento tocado no permite ubicar sus frases (p. ej. una página de PDF entera
     * como un solo texto) y [allowOcr] es true: entonces conviene reconocer el texto de la imagen.
     */
    fun textAt(
        service: AccessibilityService, x: Int, y: Int, tolerancePx: Int, mode: String, allowOcr: Boolean,
    ): List<Segment>? {
        for (window in readableWindows(service)) {
            if (!window.bounds.contains(x, y)) continue
            val items = collect(window, Long.MAX_VALUE).items
            val hit = items.filter { it.bounds.contains(x, y) }
                .minByOrNull { it.bounds.width().toLong() * it.bounds.height() }
                ?: items.minByOrNull { distance(it.bounds, x, y) }
                    ?.takeIf { distance(it.bounds, x, y) <= tolerancePx }
                ?: return emptyList()
            return refineTap(hit, x, y, mode, allowOcr)
        }
        return emptyList()
    }

    private fun refineTap(item: Item, x: Int, y: Int, mode: String, allowOcr: Boolean): List<Segment>? {
        if (mode == Prefs.TAP_ELEMENT) return listOf(item.whole())
        val pieces = TextSplit.sentences(item.raw)
        if (pieces.size <= 1) return listOf(item.whole())
        val boxes = charBoxes(item) ?: return if (allowOcr) null else listOf(item.whole())
        val boundsOf = { s: Int, e: Int -> unionOf(boxes, s, e) }
        val pieceBounds = pieces.map { boundsOf(it.start, it.end) }
        var index = pieceBounds.indexOfFirst { it != null && it.contains(x, y) }
        if (index < 0) {
            index = pieceBounds.indices.minByOrNull { i -> pieceBounds[i]?.let { distance(it, x, y) } ?: Double.MAX_VALUE }
                ?: return listOf(item.whole())
        }
        return TextSplit.pickForTap(pieces, index, mode, item.bounds, boundsOf)
    }

    /**
     * Texto de todos los elementos dentro de [area], en orden de lectura (recortado a las frases que caen
     * dentro cuando el elemento queda a medias), y zonas grandes sin texto (≥ [minBlankArea] px²).
     */
    fun scan(service: AccessibilityService, area: Rect, minBlankArea: Long, allowOcr: Boolean): Scan {
        val windows = readableWindows(service)
        val higherWindows = ArrayList<Rect>()
        val perWindow = ArrayList<Pair<Rect, List<Segment>>>()
        val blanks = ArrayList<Rect>()

        for (window in windows) { // de arriba (encima) hacia abajo
            if (Rect.intersects(window.bounds, area)) {
                val collected = collect(window, minBlankArea)
                fun visible(r: Rect) = higherWindows.none { it.contains(r.centerX(), r.centerY()) }
                val segments = ArrayList<Segment>()
                for (item in collected.items) {
                    if (!selected(item.bounds, area) || !visible(item.bounds)) continue
                    val refined = refineArea(item, area, allowOcr)
                    // No se pudo recortar a lo seleccionado: esa zona se leerá con OCR.
                    if (refined == null) blanks.add(Rect(item.bounds)) else segments.addAll(refined)
                }
                if (segments.isNotEmpty()) perWindow.add(window.bounds to segments)
                collected.blanks.filterTo(blanks) { Rect.intersects(it, area) && visible(it) }
            }
            higherWindows.add(window.bounds)
        }

        // Orden de lectura entre ventanas: de arriba a abajo en la pantalla.
        perWindow.sortWith(compareBy({ it.first.top }, { it.first.left }))
        val result = ArrayList<Segment>()
        for ((_, items) in perWindow) {
            for (item in items) {
                if (result.lastOrNull()?.text != item.text) result.add(item)
            }
        }
        return Scan(result, blanks)
    }

    /**
     * Si el elemento queda entero dentro del área se lee completo; si no, solo sus frases seleccionadas.
     * Devuelve null si no se pueden ubicar sus frases y [allowOcr] es true (se usará OCR en su zona).
     */
    private fun refineArea(item: Item, area: Rect, allowOcr: Boolean): List<Segment>? {
        if (area.contains(item.bounds)) return listOf(item.whole())
        val pieces = TextSplit.sentences(item.raw)
        if (pieces.size <= 1) return listOf(item.whole())
        val boxes = charBoxes(item) ?: return if (allowOcr) null else listOf(item.whole())
        val chosen = pieces.mapNotNull { piece ->
            val b = unionOf(boxes, piece.start, piece.end) ?: return@mapNotNull null
            if (selected(b, area)) Segment(piece.text, b) else null
        }
        return chosen.ifEmpty { listOf(item.whole()) }
    }

    /** Posición en pantalla de cada carácter del elemento (si la app la ofrece). */
    private fun charBoxes(item: Item): Array<RectF?>? {
        val node = item.node ?: return null
        val length = item.raw.length
        if (length == 0 || length > MAX_CHAR_QUERY) return null
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (node.availableExtraData?.contains(key) != true) return null
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, length)
        }
        return try {
            if (!node.refreshWithExtraData(key, args)) return null
            @Suppress("DEPRECATION")
            val array = node.extras.getParcelableArray(key) ?: return null
            Array(array.size) { array[it] as? RectF }
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun unionOf(boxes: Array<RectF?>, start: Int, end: Int): Rect? {
        val r = RectF()
        var any = false
        for (i in start until minOf(end, boxes.size)) {
            val b = boxes[i] ?: continue
            if (b.isEmpty) continue
            if (any) r.union(b) else r.set(b)
            any = true
        }
        if (!any) return null
        return Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
    }

    /**
     * ¿Cuenta un elemento como seleccionado? Sí si el recuadro cubre buena parte del elemento, o si el
     * elemento ocupa buena parte del recuadro (p. ej. un recuadro pequeño dentro de un párrafo largo).
     */
    fun selected(item: Rect, area: Rect): Boolean {
        val overlap = Rect()
        if (!overlap.setIntersect(item, area)) return false
        val overlapArea = overlap.width().toLong() * overlap.height()
        val itemArea = (item.width().toLong() * item.height()).coerceAtLeast(1)
        val areaArea = (area.width().toLong() * area.height()).coerceAtLeast(1)
        return overlapArea * 10 >= itemArea * 3 || overlapArea * 10 >= areaArea * 3
    }

    fun distance(r: Rect, x: Int, y: Int): Double {
        val dx = maxOf(r.left - x, 0, x - r.right)
        val dy = maxOf(r.top - y, 0, y - r.bottom)
        return hypot(dx.toDouble(), dy.toDouble())
    }
}
