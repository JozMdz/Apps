package com.apps.estudioaudio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowTextToSpeech
import java.io.File
import java.util.Locale

/** Recorre el uso completo: importar, ver la biblioteca, abrir el reproductor y moverse por el índice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @After
    fun tearDown() {
        Player.close()
    }

    /** El motor de voz simulado no avisa solo de que está listo. */
    private fun voiceReady() {
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).runOneTask()
    }

    private fun stepUntil(limit: Int = 200, done: () -> Boolean) {
        var steps = 0
        while (!done() && steps++ < limit) shadowOf(Looper.getMainLooper()).runOneTask()
    }

    private fun resourceFile(name: String): File {
        val file = File(context.cacheDir, name)
        javaClass.getResourceAsStream("/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    private fun importFile(name: String): Doc {
        var result: Importer.Result? = null
        Importer.importUri(context, Uri.fromFile(resourceFile(name))) { result = it }
        val deadline = System.currentTimeMillis() + 20_000
        while (result == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            idle()
        }
        val ok = result as? Importer.Result.Ok
        assertNotNull("no se importó $name: ${(result as? Importer.Result.Failed)?.let { context.getString(it.messageRes) }}", ok)
        return ok!!.doc
    }

    private fun screenshot(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/capturas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun importarYEscuchar() {
        Locale.setDefault(Locale("es", "MX"))
        val pdf = importFile("guia.pdf")
        val md = importFile("apuntes.md")
        assertEquals("Guía de estudio del sistema solar", pdf.title)
        assertEquals("Apuntes de regresión espacial", md.title)
        // Volver a importar el mismo archivo no lo duplica.
        importFile("guia.pdf")
        assertEquals(2, Library.entries(context).size)

        // --- Biblioteca
        val library = Robolectric.buildActivity(LibraryActivity::class.java).setup().get()
        idle()
        val list = library.findViewById<ListView>(R.id.list)
        assertEquals(2, list.adapter.count)
        assertEquals(View.GONE, library.findViewById<View>(R.id.empty).visibility)
        screenshot(library.window.decorView, "1-biblioteca")
        val pdfRow = (0 until list.adapter.count).first { (list.adapter.getItem(it) as Library.Entry).id == pdf.id }
        list.performItemClick(list.adapter.getView(pdfRow, null, list), pdfRow, pdfRow.toLong())
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(PlayerActivity::class.java.name, started.component?.className)
        assertEquals(pdf.id, started.getStringExtra(PlayerActivity.EXTRA_DOC_ID))

        // --- Reproductor: empieza a leer solo y avanza frase por frase
        val player = Robolectric.buildActivity(PlayerActivity::class.java, started).setup().get()
        assertEquals(pdf.id, Player.doc?.id)
        assertEquals(Player.State.PLAYING, Player.state)
        val serviceIntent = shadowOf(context as Application).nextStartedService
        assertEquals(PlaybackService::class.java.name, serviceIntent.component?.className)
        val service = Robolectric.buildService(PlaybackService::class.java, serviceIntent).create().startCommand(0, 1).get()
        voiceReady()
        // La voz simulada termina cada frase enseguida: se avanza tarea por tarea.
        stepUntil { Player.index >= 2 }
        assertTrue("debe avanzar al terminar cada frase (va en ${Player.index})", Player.index >= 2)
        assertEquals(Player.State.PLAYING, Player.state)

        // Pausa: se queda donde iba y lo recuerda.
        player.findViewById<View>(R.id.play_pause).performClick()
        assertEquals(Player.State.PAUSED, Player.state)
        val pausedAt = Player.index
        idle()
        assertEquals(pausedAt, Player.index)
        assertEquals(pausedAt, Library.position(context, pdf.id))

        // Controles de apartado y de frase (en pausa solo mueven la posición).
        val sections = player.findViewById<ListView>(R.id.sections)
        assertEquals(pdf.sections.size, sections.adapter.count)
        val second = pdf.sections.indexOfFirst { it.title == "2. Gigantes gaseosos, lunas y anillos" }
        Player.jumpToSection(second)
        Player.pause()
        idle()
        val base = pdf.sections[second].start
        assertEquals(base, Player.index)
        assertEquals("2. Gigantes gaseosos, lunas y anillos", player.findViewById<TextView>(R.id.now_section).text.toString())
        player.findViewById<View>(R.id.next_sentence).performClick()
        assertEquals(base + 1, Player.index)
        player.findViewById<View>(R.id.next_section).performClick()
        assertEquals(pdf.sections[second + 1].start, Player.index)
        player.findViewById<View>(R.id.previous_section).performClick()
        assertEquals(base, Player.index)
        // Desde un punto intermedio, el título que se muestra sigue siendo el del apartado.
        val mark = pdf.sections.indexOfFirst { it.mark }
        Player.jumpToSection(mark)
        Player.pause()
        assertEquals(pdf.sections[mark].start, Player.index)
        assertEquals("1. El Sol y los planetas interiores", player.findViewById<TextView>(R.id.now_section).text.toString())
        assertEquals(Player.index, Library.position(context, pdf.id))
        player.findViewById<Button>(R.id.speed).performClick()
        assertEquals("1.25x", player.findViewById<Button>(R.id.speed).text.toString().replace(',', '.'))
        idle()
        screenshot(player.window.decorView, "2-reproductor")

        // Vista de texto (tipo letra de canción): tocar una frase salta a ella y lee desde ahí.
        val text = player.findViewById<ListView>(R.id.text)
        assertEquals(View.VISIBLE, text.visibility)
        assertEquals(pdf.sentences.size, text.adapter.count)
        val target = pdf.sections[second].start + 2
        text.performItemClick(text.adapter.getView(target, null, text), target, target.toLong())
        assertEquals(Player.State.PLAYING, Player.state)
        assertEquals(target, Player.index)
        Player.pause()
        idle()
        assertEquals(1f, text.adapter.getView(target, null, text).alpha, 0.01f)
        assertEquals(0.5f, text.adapter.getView(target + 1, null, text).alpha, 0.01f)
        screenshot(player.window.decorView, "2b-texto")
        player.findViewById<View>(R.id.tab_index).performClick()
        assertEquals(View.GONE, text.visibility)
        assertEquals(View.VISIBLE, sections.visibility)
        idle()
        screenshot(player.window.decorView, "2c-indice")

        // --- Repetir: un toque repite el fragmento actual; otro, el apartado; otro, apaga.
        player.findViewById<View>(R.id.tab_text).performClick()
        val loopButton = player.findViewById<Button>(R.id.loop)
        Player.jumpToSection(mark)
        Player.pause()
        loopButton.performClick()
        assertEquals(Player.Loop.FRAGMENT, Player.loop)
        assertEquals(pdf.sections[mark].start, Player.loopStart)
        assertEquals(pdf.sections[mark + 1].start - 1, Player.loopEnd)
        assertEquals("Repitiendo: fragmento", loopButton.text.toString())
        // Al terminar la última frase del fragmento vuelve a su inicio en vez de seguir.
        Player.jumpToSentence(Player.loopEnd)
        stepUntil { Player.index != Player.loopEnd }
        assertEquals(Player.loopStart, Player.index)
        assertEquals(Player.State.PLAYING, Player.state)
        Player.pause()
        idle()
        screenshot(player.window.decorView, "2d-repetir")
        loopButton.performClick()
        assertEquals(Player.Loop.SECTION, Player.loop)
        val first = pdf.sections.indexOfFirst { it.title == "1. El Sol y los planetas interiores" }
        assertEquals(pdf.sections[first].start, Player.loopStart)
        assertEquals(pdf.sections[second].start - 1, Player.loopEnd)
        // Moverse a otro apartado: la repetición sigue a la nueva posición.
        Player.jumpToSection(second)
        Player.pause()
        assertEquals(pdf.sections[second].start, Player.loopStart)
        loopButton.performClick()
        assertEquals(Player.Loop.OFF, Player.loop)
        assertEquals("Repetir", loopButton.text.toString())
        // Tramo a la medida: mantener pulsada la frase inicial y luego la final.
        text.onItemLongClickListener.onItemLongClick(text, null, 12, 12)
        assertEquals(Player.Loop.OFF, Player.loop)
        text.onItemLongClickListener.onItemLongClick(text, null, 10, 10)
        assertEquals(Player.Loop.CUSTOM, Player.loop)
        assertEquals(10, Player.loopStart)
        assertEquals(12, Player.loopEnd)
        assertEquals(10, Player.index)
        stepUntil { Player.index == 12 }
        stepUntil { Player.index != 12 }
        assertEquals(10, Player.index)
        Player.pause()
        idle()
        // Salir del tramo elegido lo apaga.
        Player.jumpToSection(second)
        Player.pause()
        assertEquals(Player.Loop.OFF, Player.loop)

        // Tocar un apartado del índice salta a él y lee.
        sections.performItemClick(sections.adapter.getView(1, null, sections), 1, 1)
        assertEquals(Player.State.PLAYING, Player.state)
        assertEquals(pdf.sections[1].start, Player.index)

        // Al llegar al final se detiene y vuelve al principio.
        idle()
        assertEquals(Player.State.PAUSED, Player.state)
        assertEquals(0, Library.position(context, pdf.id))

        // --- La biblioteca muestra el avance y "Continuar"
        Player.jumpToSection(pdf.sections.lastIndex)
        Player.pause()
        val library2 = Robolectric.buildActivity(LibraryActivity::class.java).setup().get()
        idle()
        val continueButton = library2.findViewById<Button>(R.id.continue_button)
        assertEquals(View.VISIBLE, continueButton.visibility)
        assertTrue(continueButton.text.toString().contains("Guía de estudio del sistema solar"))
        screenshot(library2.window.decorView, "3-biblioteca-con-avance")

        // Cerrar libera todo y el servicio se detiene.
        Player.close()
        idle()
        assertNull(Player.doc)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(player.isFinishing)
    }

    @Test
    fun compartirTextoDesdeOtraApp() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "# Notas rápidas\n\nPrimera idea importante del día. Segunda idea importante del día.")
        val library = Robolectric.buildActivity(LibraryActivity::class.java, intent).setup().get()
        val deadline = System.currentTimeMillis() + 10_000
        while (Library.entries(context).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            idle()
        }
        idle()
        assertEquals(listOf("Notas rápidas"), Library.entries(context).map { it.title })
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(PlayerActivity::class.java.name, started.component?.className)
        assertTrue(!library.isFinishing)
    }

    @Test
    fun bibliotecaVacia() {
        val library = Robolectric.buildActivity(LibraryActivity::class.java).setup().get()
        assertEquals(View.VISIBLE, library.findViewById<View>(R.id.empty).visibility)
        assertEquals(View.GONE, library.findViewById<View>(R.id.continue_button).visibility)
        screenshot(library.window.decorView, "0-biblioteca-vacia")
    }
}
