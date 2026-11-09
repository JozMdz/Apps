package com.apps.seleccionarpronunciar

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.hypot

/** Extrae el texto visible de la pantalla a partir del árbol de accesibilidad. */
object ScreenTextReader {

    private const val MAX_DEPTH = 80

    /**
     * Resultado de explorar un área: el texto encontrado y las zonas grandes sin texto accesible
     * (visores de PDF, imágenes, lienzos…) donde conviene usar reconocimiento de texto (OCR).
     */
    class Scan(val segments: List<Segment>, val blankRegions: List<Rect>)

    private class WindowRoot(val layer: Int, val bounds: Rect, val root: AccessibilityNodeInfo)

    private class Collected(val segments: MutableList<Segment> = ArrayList(), val blanks: MutableList<Rect> = ArrayList())

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

        val text = if (node.isPassword) null else node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            out.segments.add(Segment(text, bounds))
            return
        }

        val before = out.segments.size
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, clip, out, minBlankArea, depth + 1)
        }
        if (out.segments.size != before) return

        // Si ningún hijo aportó texto, usamos la descripción (p. ej. botones con icono o imágenes).
        val description = node.contentDescription?.toString()?.trim()
        if (!description.isNullOrEmpty()) {
            out.segments.add(Segment(description, bounds))
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

    /** Texto del elemento tocado en (x, y), coordenadas de pantalla. */
    fun textAt(service: AccessibilityService, x: Int, y: Int, tolerancePx: Int): List<Segment> {
        for (window in readableWindows(service)) {
            if (!window.bounds.contains(x, y)) continue
            val items = collect(window, Long.MAX_VALUE).segments
            val hit = items.filter { it.bounds!!.contains(x, y) }
                .minByOrNull { it.bounds!!.width().toLong() * it.bounds.height() }
            if (hit != null) return listOf(hit)

            // Nada justo debajo del dedo: elegimos el elemento más cercano si está muy cerca.
            val nearest = items.minByOrNull { distance(it.bounds!!, x, y) }
            if (nearest != null && distance(nearest.bounds!!, x, y) <= tolerancePx) return listOf(nearest)
            return emptyList()
        }
        return emptyList()
    }

    /**
     * Texto de todos los elementos dentro de [area], en orden de lectura, y zonas grandes sin texto
     * (de al menos [minBlankArea] px²) que se solapan con el área.
     */
    fun scan(service: AccessibilityService, area: Rect, minBlankArea: Long): Scan {
        val windows = readableWindows(service)
        val higherWindows = ArrayList<Rect>()
        val perWindow = ArrayList<Pair<Rect, List<Segment>>>()
        val blanks = ArrayList<Rect>()

        for (window in windows) { // de arriba (encima) hacia abajo
            if (Rect.intersects(window.bounds, area)) {
                val collected = collect(window, minBlankArea)
                fun visible(r: Rect) = higherWindows.none { it.contains(r.centerX(), r.centerY()) }
                val items = collected.segments.filter { selected(it.bounds!!, area) && visible(it.bounds) }
                if (items.isNotEmpty()) perWindow.add(window.bounds to items)
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
