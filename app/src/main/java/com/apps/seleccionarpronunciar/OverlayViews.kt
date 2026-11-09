package com.apps.seleccionarpronunciar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Capa a pantalla completa que recoge un toque (leer un elemento) o un arrastre
 * (leer todo lo que quede dentro). Con [fullRows] el arrastre selecciona renglones completos
 * (todo el ancho entre donde empieza y termina el dedo), más fácil con una mano.
 * Trabaja en coordenadas de pantalla.
 */
@SuppressLint("ViewConstructor")
class SelectionView(
    context: Context,
    private val fullRows: Boolean,
    private val onTap: (x: Int, y: Int) -> Unit,
    private val onArea: (area: Rect) -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val dimPaint = Paint().apply { color = Color.argb(40, 0, 0, 0) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 26, 115, 232) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(26, 115, 232)
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }

    private var startX = 0f
    private var startY = 0f
    private var curX = 0f
    private var curY = 0f
    private var dragging = false
    private val location = IntArray(2)
    private val drawRect = RectF()

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        if (!dragging) return
        getLocationOnScreen(location)
        val r = drawRect.apply {
            set(min(startX, curX), min(startY, curY), max(startX, curX), max(startY, curY))
            offset(-location[0].toFloat(), -location[1].toFloat())
            if (fullRows) {
                left = 0f
                right = width.toFloat()
            }
        }
        val radius = 8 * density
        canvas.drawRoundRect(r, radius, radius, fillPaint)
        canvas.drawRoundRect(r, radius, radius, strokePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX; startY = event.rawY
                curX = startX; curY = startY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                curX = event.rawX; curY = event.rawY
                if (!dragging && maxOf(abs(curX - startX), abs(curY - startY)) > touchSlop) {
                    dragging = true
                }
                if (dragging) invalidate()
            }
            MotionEvent.ACTION_UP -> {
                curX = event.rawX; curY = event.rawY
                if (dragging) {
                    val area = Rect(
                        min(startX, curX).toInt(), min(startY, curY).toInt(),
                        max(startX, curX).toInt(), max(startY, curY).toInt(),
                    )
                    if (fullRows) {
                        getLocationOnScreen(location)
                        area.left = location[0]
                        area.right = location[0] + width
                        // Un deslizamiento horizontal sobre un solo renglón selecciona ese renglón.
                        if (area.height() < touchSlop * 2) area.inset(0, area.height() / 2 - touchSlop)
                    }
                    onArea(area)
                } else {
                    onTap(startX.toInt(), startY.toInt())
                }
                dragging = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
            }
        }
        return true
    }
}

/** Capa no táctil que resalta el elemento que se está leyendo. */
class HighlightView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 255, 214, 0) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 179, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }
    private val location = IntArray(2)
    private val drawRect = RectF()
    private var target: Rect? = null

    fun setTarget(bounds: Rect?) {
        if (bounds == target) return
        target = bounds?.let { Rect(it) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val t = target ?: return
        getLocationOnScreen(location)
        val pad = 4 * density
        val r = drawRect.apply {
            set(t)
            offset(-location[0].toFloat(), -location[1].toFloat())
            inset(-pad, -pad)
        }
        val radius = 6 * density
        canvas.drawRoundRect(r, radius, radius, fillPaint)
        canvas.drawRoundRect(r, radius, radius, strokePaint)
    }
}
