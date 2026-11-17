package com.apps.estudioaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MarkdownParserTest {

    private val source = javaClass.getResource("/apuntes.md")!!.readText()
    private val doc = DocBuilder.build(MarkdownParser.parse(source), "archivo", Locale("es"))

    @Test
    fun indiceSaleDeLosTitulos() {
        assertEquals("Apuntes de regresión espacial", doc.title)
        assertEquals(
            listOf("Apuntes de regresión espacial", "1. Autocorrelación espacial", "1.1 Índice de Moran", "Conclusiones"),
            doc.sections.map { it.title },
        )
        assertEquals(listOf(1, 2, 3, 2), doc.sections.map { it.level })
        // El título se lee como primera frase de su apartado.
        for (s in doc.sections) assertTrue(doc.sentences[s.start].startsWith(s.title))
    }

    @Test
    fun seQuitaLaSintaxis() {
        val all = doc.sentences.joinToString("\n")
        println(all)
        for (symbol in listOf("**", "`", "](", "http", "|", "---", "titulo:", "print(", "[x]", "#")) {
            assertFalse("no debería quedar «$symbol»", all.contains(symbol))
        }
        assertTrue(all.contains("autocorrelación espacial y el índice de Moran"))
        assertTrue(all.contains("Ver la guía y enlace."))
        assertTrue(all.contains("Primera idea con código."))
        assertTrue(all.contains("Segunda idea ya revisada."))
        assertTrue(all.contains("1. Paso uno."))
        assertTrue(all.contains("I > 0, Agrupamiento."))
        assertTrue(all.contains("Bloque de código omitido."))
        assertTrue(all.contains("Cita: lo cercano se parece más que lo lejano."))
        // Los guiones bajos dentro de palabras no son cursivas.
        assertTrue(all.contains("pesos_espaciales"))
    }

    @Test
    fun lasAbreviaturasNoCortanLaFrase() {
        assertTrue(doc.sentences.any { it == "El Dr. Pérez la describe en la Fig. 2 del capítulo." })
        // Un salto de línea simple no corta el párrafo.
        assertTrue(doc.sentences.any { it == "Sigue en la misma línea del párrafo." })
        // La numeración de una lista no queda como frase suelta.
        assertTrue(doc.sentences.any { it == "1. Paso uno." })
        // Las frases muy cortas se unen a su vecina.
        assertEquals(
            listOf("Buenos días. Hoy vamos a ver el tema completo.", "Luego seguimos con otra idea igual de larga. Fin."),
            Sentences.split("Buenos días. Hoy vamos a ver el tema completo. Luego seguimos con otra idea igual de larga. Fin.", Locale("es")),
        )
    }

    @Test
    fun sinTitulosSeCreanPartes() {
        val text = (1..80).joinToString("\n\n") { "Esta es la frase número $it del texto." }
        val plain = DocBuilder.build(MarkdownParser.parse(text), "notas", Locale("es"))
        assertEquals("notas", plain.title)
        assertEquals(80, plain.sentences.size)
        assertEquals(listOf("Parte 1", "Parte 2", "Parte 3"), plain.sections.map { it.title })
        assertFalse(plain.sections[0].titleSpoken)
        assertEquals(1, plain.sectionAt(45))
        assertEquals(29, plain.sectionEnd(0))
    }
}
