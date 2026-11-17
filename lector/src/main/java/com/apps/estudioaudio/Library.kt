package com.apps.estudioaudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Documentos guardados en el teléfono y el punto donde se quedó cada uno. */
object Library {

    /** Datos de un documento para mostrarlo en la lista sin cargarlo entero. */
    class Entry(val id: String, val title: String, val added: Long, val total: Int, val sections: Int)

    private const val INDEX = "biblioteca.json"

    private fun dir(context: Context) = File(context.filesDir, "docs").apply { mkdirs() }
    private fun prefs(context: Context) = context.getSharedPreferences("avance", Context.MODE_PRIVATE)

    @Synchronized
    fun entries(context: Context): List<Entry> {
        val file = File(dir(context), INDEX)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(o.getString("id"), o.getString("title"), o.optLong("added"), o.optInt("total"), o.optInt("sections"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeEntries(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject().put("id", e.id).put("title", e.title).put("added", e.added)
                    .put("total", e.total).put("sections", e.sections)
            )
        }
        File(dir(context), INDEX).writeText(array.toString())
    }

    /** Guarda el documento (si ya existía el mismo contenido, conserva su avance) y lo pone primero. */
    @Synchronized
    fun save(context: Context, doc: Doc) {
        val json = JSONObject().put("title", doc.title).put("sentences", JSONArray(doc.sentences))
        val sections = JSONArray()
        for (s in doc.sections) {
            sections.put(JSONObject().put("t", s.title).put("l", s.level).put("s", s.start).put("sp", s.titleSpoken).put("m", s.mark))
        }
        json.put("sections", sections)
        File(dir(context), "${doc.id}.json").writeText(json.toString())

        val entry = Entry(doc.id, doc.title, System.currentTimeMillis(), doc.sentences.size, doc.sections.count { !it.mark })
        writeEntries(context, listOf(entry) + entries(context).filter { it.id != doc.id })
    }

    @Synchronized
    fun load(context: Context, id: String): Doc? {
        val file = File(dir(context), "$id.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val sentencesJson = json.getJSONArray("sentences")
            val sentences = List(sentencesJson.length()) { sentencesJson.getString(it) }
            val sectionsJson = json.getJSONArray("sections")
            val sections = List(sectionsJson.length()) {
                val o = sectionsJson.getJSONObject(it)
                Section(o.getString("t"), o.getInt("l"), o.getInt("s"), o.optBoolean("sp", true), o.optBoolean("m", false))
            }
            Doc(id, json.getString("title"), sentences, sections)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        File(dir(context), "$id.json").delete()
        writeEntries(context, entries(context).filter { it.id != id })
        prefs(context).edit().remove(id).apply()
    }

    /** Frase en la que se quedó el documento. */
    fun position(context: Context, id: String): Int = prefs(context).getInt(id, 0)

    fun setPosition(context: Context, id: String, sentence: Int) =
        prefs(context).edit().putInt(id, sentence).apply()

    /** Último documento que se estuvo escuchando. */
    fun lastDoc(context: Context): String? = prefs(context).getString("_ultimo", null)

    fun setLastDoc(context: Context, id: String) = prefs(context).edit().putString("_ultimo", id).apply()

    fun rate(context: Context): Float = prefs(context).getFloat("_velocidad", 1.0f)

    fun setRate(context: Context, rate: Float) = prefs(context).edit().putFloat("_velocidad", rate).apply()
}
