package com.apps.seleccionarpronunciar

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Reconocimiento de texto (OCR) sobre una captura de pantalla, en el propio teléfono y sin internet.
 * Sirve para PDF, imágenes y apps que dibujan su texto sin exponerlo a accesibilidad.
 */
class OcrReader {

    /** Una línea reconocida; [block] agrupa las líneas de un mismo párrafo. */
    class Line(val text: String, val box: Rect, val block: Int)

    private var recognizer: TextRecognizer? = null

    /** Reconoce el texto de [bitmap] (coordenadas de pantalla). El resultado llega en el hilo principal. */
    fun recognize(bitmap: Bitmap, callback: (List<Line>?) -> Unit) {
        val client = recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .also { recognizer = it }
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = ArrayList<Line>()
                result.textBlocks.forEachIndexed { blockIndex, block ->
                    for (line in block.lines) {
                        val box = line.boundingBox ?: continue
                        val text = line.text.trim()
                        if (text.isNotEmpty()) lines.add(Line(text, box, blockIndex))
                    }
                }
                callback(lines)
            }
            .addOnFailureListener { callback(null) }
            .addOnCompleteListener { bitmap.recycle() }
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }

    companion object {
        /** El párrafo bajo el dedo (o el más cercano si está muy cerca). */
        fun textAt(lines: List<Line>, x: Int, y: Int, tolerancePx: Int): List<Segment> {
            val line = lines.firstOrNull { it.box.contains(x, y) }
                ?: lines.minByOrNull { ScreenTextReader.distance(it.box, x, y) }
                    ?.takeIf { ScreenTextReader.distance(it.box, x, y) <= tolerancePx }
                ?: return emptyList()
            return group(lines.filter { it.block == line.block })
        }

        /** Las líneas dentro de [area] (y, si se indica, dentro de alguna de [regions]), por párrafos. */
        fun textIn(lines: List<Line>, area: Rect, regions: List<Rect>? = null): List<Segment> =
            group(lines.filter { line ->
                ScreenTextReader.selected(line.box, area) &&
                    (regions == null || regions.any { it.contains(line.box.centerX(), line.box.centerY()) })
            })

        /** Une las líneas consecutivas de un mismo bloque en un solo fragmento. */
        private fun group(lines: List<Line>): List<Segment> {
            val out = ArrayList<Segment>()
            var i = 0
            while (i < lines.size) {
                val block = lines[i].block
                val text = StringBuilder()
                val bounds = Rect(lines[i].box)
                while (i < lines.size && lines[i].block == block) {
                    val line = lines[i].text
                    if (text.endsWith("-") && line.firstOrNull()?.isLowerCase() == true) {
                        text.setLength(text.length - 1) // palabra partida con guion al final de línea
                    } else if (text.isNotEmpty()) {
                        text.append(' ')
                    }
                    text.append(line)
                    bounds.union(lines[i].box)
                    i++
                }
                out.add(Segment(text.toString(), bounds))
            }
            return out
        }

        /** Mezcla dos listas en orden de lectura (por su posición vertical), conservando el orden de cada una. */
        fun merge(a: List<Segment>, b: List<Segment>): List<Segment> {
            val out = ArrayList<Segment>(a.size + b.size)
            var i = 0
            var j = 0
            while (i < a.size || j < b.size) {
                val takeA = j >= b.size || (i < a.size && (a[i].bounds?.top ?: 0) <= (b[j].bounds?.top ?: 0))
                out.add(if (takeA) a[i++] else b[j++])
            }
            return out
        }
    }
}
