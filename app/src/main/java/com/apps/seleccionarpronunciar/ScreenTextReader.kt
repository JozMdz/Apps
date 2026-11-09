package com.apps.seleccionarpronunciar

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.hypot

/** Extrae el texto visible de la pantalla a partir del árbol de accesibilidad. */
object ScreenTextReader {

    private const val MAX_DEPTH = 80

    private class WindowRoot(val layer: Int, val bounds: Rect, val root: AccessibilityNodeInfo)

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

    private fun collect(window: WindowRoot): List<Segment> {
        val out = ArrayList<Segment>()
        collect(window.root, window.bounds, out, 0)
        return out
    }

    private fun collect(node: AccessibilityNodeInfo, clip: Rect, out: MutableList<Segment>, depth: Int) {
        if (depth > MAX_DEPTH || !node.isVisibleToUser) return
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (!bounds.intersect(clip)) return

        val text = if (node.isPassword) null else node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            out.add(Segment(text, bounds))
            return
        }

        val before = out.size
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, clip, out, depth + 1)
        }

        // Si ningún hijo aportó texto, usamos la descripción (p. ej. botones con icono o imágenes).
        if (out.size == before) {
            val description = node.contentDescription?.toString()?.trim()
            if (!description.isNullOrEmpty()) out.add(Segment(description, bounds))
        }
    }

    /** Texto del elemento tocado en (x, y), coordenadas de pantalla. */
    fun textAt(service: AccessibilityService, x: Int, y: Int, tolerancePx: Int): List<Segment> {
        for (window in readableWindows(service)) {
            if (!window.bounds.contains(x, y)) continue
            val items = collect(window)
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

    /** Texto de todos los elementos dentro del recuadro [area], en orden de lectura. */
    fun textIn(service: AccessibilityService, area: Rect): List<Segment> {
        val windows = readableWindows(service)
        val higherWindows = ArrayList<Rect>()
        val perWindow = ArrayList<Pair<Rect, List<Segment>>>()

        for (window in windows) { // de arriba (encima) hacia abajo
            if (Rect.intersects(window.bounds, area)) {
                val items = collect(window).filter { seg ->
                    val b = seg.bounds!!
                    selected(b, area) && higherWindows.none { it.contains(b.centerX(), b.centerY()) }
                }
                if (items.isNotEmpty()) perWindow.add(window.bounds to items)
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
        return result
    }

    /** Todo el texto visible en la pantalla. */
    fun allText(service: AccessibilityService): List<Segment> {
        val screen = Rect()
        for (window in readableWindows(service)) screen.union(window.bounds)
        return if (screen.isEmpty) emptyList() else textIn(service, screen)
    }

    private fun selected(item: Rect, area: Rect): Boolean {
        val overlap = Rect()
        if (!overlap.setIntersect(item, area)) return false
        val overlapArea = overlap.width().toLong() * overlap.height()
        val itemArea = (item.width().toLong() * item.height()).coerceAtLeast(1)
        val areaArea = (area.width().toLong() * area.height()).coerceAtLeast(1)
        // Incluido si el recuadro cubre buena parte del elemento, o el elemento ocupa buena parte del recuadro
        // (por ejemplo, un recuadro pequeño dentro de un párrafo largo).
        return overlapArea * 10 >= itemArea * 3 || overlapArea * 10 >= areaArea * 3
    }

    private fun distance(r: Rect, x: Int, y: Int): Double {
        val dx = maxOf(r.left - x, 0, x - r.right)
        val dy = maxOf(r.top - y, 0, y - r.bottom)
        return hypot(dx.toDouble(), dy.toDouble())
    }
}
