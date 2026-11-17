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
            doc.sections.filter { !it.mark }.map { it.title },
        )
        assertEquals(listOf(1, 2, 3, 2), doc.sections.filter { !it.mark }.map { it.level })
        // El título se lee como primera frase de su apartado.
        for (s in doc.sections.filter { !it.mark }) assertTrue(doc.sentences[s.start].startsWith(s.title))
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
        assertEquals(listOf("Parte 1", "Parte 2", "Parte 3"), plain.sections.filter { !it.mark }.map { it.title })
        assertFalse(plain.sections[0].titleSpoken)
        assertEquals("Parte 2", plain.headingAt(45)?.title)
        // En listas de frases cortas los puntos intermedios quedan espaciados, no cada 3 frases.
        val marks = plain.sections.filter { it.mark }
        assertTrue("hay puntos intermedios", marks.isNotEmpty())
        assertTrue("no demasiados: ${marks.size}", marks.size <= 16)
    }

    @Test
    fun apartadosLargosTienenPuntosIntermedios() {
        val paragraph = "Esta idea se explica con calma en varias frases seguidas. " +
            "La segunda frase agrega un detalle necesario para entenderla bien. " +
            "La tercera cierra la idea con un ejemplo concreto y fácil de recordar."
        val text = buildString {
            append("# 1. Primer tema\n\n")
            for (i in 1..4) append("Nota $i del primer tema\n\n$paragraph\n\n")
            append("# 2. Segundo tema\n\nUn apartado corto no necesita puntos intermedios.\n")
        }
        val doc = DocBuilder.build(MarkdownParser.parse(text), "mis apuntes", Locale("es"))
        // Con el nivel más alto usado para los apartados, el nombre del archivo es el título.
        assertEquals("mis apuntes", doc.title)
        assertEquals(listOf("1. Primer tema", "2. Segundo tema"), doc.sections.filter { !it.mark }.map { it.title })
        val marks = doc.sections.filter { it.mark }
        println(doc.sections.joinToString("\n") { "${"  ".repeat(it.level - 1)}[${it.start}] ${it.title}" })
        assertEquals(listOf("Nota 2 del primer tema.", "Nota 3 del primer tema.", "Nota 4 del primer tema."), marks.map { it.title })
        assertTrue(marks.all { it.level == 2 && !it.titleSpoken })
        // El punto intermedio pertenece a su apartado.
        assertEquals("1. Primer tema", doc.headingAt(marks[1].start + 1)?.title)
        assertEquals("2. Segundo tema", doc.headingAt(doc.sentences.lastIndex)?.title)
    }
}
