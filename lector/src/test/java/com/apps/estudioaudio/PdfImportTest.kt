package com.apps.estudioaudio

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfImportTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun load(): Doc {
        val file = File.createTempFile("guia", ".pdf")
        javaClass.getResourceAsStream("/guia.pdf")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val pdf = PdfExtractor.extract(context, file)
        for (l in pdf.lines) println("p${l.page} ${"%.1f".format(l.size)} ${if (l.bold) "B" else " "} ${if (l.paragraphStart) "¶" else " "} ${l.text}")
        println("outline=${pdf.outline}")
        return DocBuilder.build(PdfStructure.toBlocks(pdf.lines, pdf.outline), "guia de estudio", Locale("es"))
    }

    @Test
    fun pdfConTitulosYPieDePagina() {
        val doc = load()
        println("---- ${doc.title}")
        for (s in doc.sections) println("${"  ".repeat(s.level - 1)}[${s.start}] ${s.title}")
        doc.sentences.forEachIndexed { i, s -> println("$i: $s") }

        assertEquals("Guía de estudio del sistema solar", doc.title)
        assertEquals(
            listOf(
                "Guía de estudio del sistema solar",
                "1. El Sol y los planetas interiores",
                "2. Gigantes gaseosos, lunas y anillos",
                "3. Repaso y cierre",
            ),
            doc.sections.map { it.title },
        )
        assertEquals(listOf(1, 2, 2, 2), doc.sections.map { it.level })
        val all = doc.sentences.joinToString("\n")
        assertFalse("el pie de página no se lee", all.contains("Página"))
        // Los renglones del párrafo se unen en frases completas.
        assertTrue(doc.sentences.any { it == "Buenos días. El sistema solar se formó hace unos 4600 millones de años a partir de una nube de gas y polvo." })
        assertTrue(doc.sentences.any { it == "Nota: Mapa. Fig. 1" })
        // Un párrafo que sigue en la página siguiente... aquí cada página empieza párrafo nuevo.
        assertTrue(doc.sentences.any { it.startsWith("Así que el objetivo de este apartado") })
    }
}
