package com.trashhotdog123.ereader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("ereader_store", Context.MODE_PRIVATE)

    fun loadBooks(): List<Book> = runCatching {
        val array = JSONArray(prefs.getString("books", "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) add(bookFromJson(array.getJSONObject(i)))
        }
    }.getOrDefault(emptyList())

    fun saveBooks(books: List<Book>) {
        val array = JSONArray()
        books.forEach { array.put(bookToJson(it)) }
        prefs.edit().putString("books", array.toString()).apply()
    }

    fun upsert(book: Book) {
        val books = loadBooks().toMutableList()
        val index = books.indexOfFirst { it.id == book.id }
        if (index >= 0) books[index] = book else books.add(0, book)
        saveBooks(books)
    }

    fun remove(id: String) = saveBooks(loadBooks().filterNot { it.id == id })

    fun get(id: String): Book? = loadBooks().firstOrNull { it.id == id }

    fun settings(): ReaderSettings {
        val o = runCatching { JSONObject(prefs.getString("settings", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return ReaderSettings(
            theme = o.optString("theme", "Cream"),
            font = o.optString("font", "Serif"),
            fontSize = o.optDouble("fontSize", 19.0).toFloat(),
            lineSpacing = o.optDouble("lineSpacing", 1.55).toFloat(),
            mode = o.optString("mode", "Scroll"),
            autoTurnSeconds = o.optInt("autoTurnSeconds", 0),
            focusOnStart = o.optBoolean("focusOnStart", false),
            haptics = o.optBoolean("haptics", true),
            defaultWpm = o.optInt("defaultWpm", 220)
        )
    }

    fun saveSettings(value: ReaderSettings) {
        val o = JSONObject()
            .put("theme", value.theme)
            .put("font", value.font)
            .put("fontSize", value.fontSize)
            .put("lineSpacing", value.lineSpacing)
            .put("mode", value.mode)
            .put("autoTurnSeconds", value.autoTurnSeconds)
            .put("focusOnStart", value.focusOnStart)
            .put("haptics", value.haptics)
            .put("defaultWpm", value.defaultWpm)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    fun stats(): ReaderStats {
        val o = runCatching { JSONObject(prefs.getString("stats", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return ReaderStats(
            totalMinutes = o.optInt("totalMinutes", 0),
            pagesRead = o.optInt("pagesRead", 0),
            streakDays = o.optInt("streakDays", 0),
            lastReadDate = o.optString("lastReadDate", "")
        )
    }

    fun recordSession(minutes: Int, pages: Int) {
        val old = stats()
        val today = LocalDate.now().toString()
        val streak = when {
            old.lastReadDate == today -> maxOf(1, old.streakDays)
            old.lastReadDate.isBlank() -> 1
            runCatching { LocalDate.parse(old.lastReadDate).plusDays(1) == LocalDate.parse(today) }.getOrDefault(false) ->
                old.streakDays + 1
            else -> 1
        }
        val value = ReaderStats(
            totalMinutes = old.totalMinutes + minutes.coerceAtLeast(0),
            pagesRead = old.pagesRead + pages.coerceAtLeast(0),
            streakDays = streak,
            lastReadDate = today
        )
        val o = JSONObject()
            .put("totalMinutes", value.totalMinutes)
            .put("pagesRead", value.pagesRead)
            .put("streakDays", value.streakDays)
            .put("lastReadDate", value.lastReadDate)
        prefs.edit().putString("stats", o.toString()).apply()
    }

    fun updateProgress(id: String, page: Int, offset: Int, ratio: Float, wpm: Int) {
        val book = get(id) ?: return
        upsert(book.copy(progressPage = page, progressOffset = offset, progressRatio = ratio, calibratedWpm = wpm))
    }

    fun setWpm(wpm: Int) {
        val safe = wpm.coerceIn(80, 1200)
        saveSettings(settings().copy(defaultWpm = safe))
        saveBooks(loadBooks().map { it.copy(calibratedWpm = safe) })
    }

    private fun annotationToJson(a: AnnotationData) = JSONObject()
        .put("id", a.id).put("text", a.text).put("page", a.page).put("note", a.note)
        .put("type", a.type).put("createdAt", a.createdAt)

    private fun annotationFromJson(o: JSONObject) = AnnotationData(
        id = o.optString("id"), text = o.optString("text"), page = o.optInt("page"),
        note = o.optString("note"), type = o.optString("type", "highlight"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun characterToJson(c: CharacterCard) = JSONObject()
        .put("name", c.name).put("summary", c.summary).put("page", c.page)

    private fun characterFromJson(o: JSONObject) = CharacterCard(
        name = o.optString("name"), summary = o.optString("summary"), page = o.optInt("page")
    )

    private fun bookToJson(b: Book): JSONObject {
        val annotations = JSONArray()
        b.annotations.forEach { annotations.put(annotationToJson(it)) }
        val characters = JSONArray()
        b.characters.forEach { characters.put(characterToJson(it)) }
        return JSONObject()
            .put("id", b.id).put("fileName", b.fileName).put("filePath", b.filePath)
            .put("format", b.format.name).put("title", b.title).put("author", b.author)
            .put("synopsis", b.synopsis).put("genre", b.genre)
            .put("progressPage", b.progressPage).put("progressOffset", b.progressOffset)
            .put("progressRatio", b.progressRatio).put("calibratedWpm", b.calibratedWpm)
            .put("inVault", b.inVault).put("annotations", annotations).put("characters", characters)
    }

    private fun bookFromJson(o: JSONObject): Book {
        val annotationsJson = o.optJSONArray("annotations") ?: JSONArray()
        val charsJson = o.optJSONArray("characters") ?: JSONArray()
        val annotations = buildList {
            for (i in 0 until annotationsJson.length()) add(annotationFromJson(annotationsJson.getJSONObject(i)))
        }
        val characters = buildList {
            for (i in 0 until charsJson.length()) add(characterFromJson(charsJson.getJSONObject(i)))
        }
        return Book(
            id = o.optString("id"),
            fileName = o.optString("fileName"),
            filePath = o.optString("filePath"),
            format = runCatching { BookFormat.valueOf(o.optString("format")) }.getOrDefault(BookFormat.UNKNOWN),
            title = o.optString("title", o.optString("fileName", "Untitled")),
            author = o.optString("author", "Unknown author"),
            synopsis = o.optString("synopsis"),
            genre = o.optString("genre", "Unsorted"),
            progressPage = o.optInt("progressPage"),
            progressOffset = o.optInt("progressOffset"),
            progressRatio = o.optDouble("progressRatio", 0.0).toFloat(),
            calibratedWpm = o.optInt("calibratedWpm", settings().defaultWpm),
            inVault = o.optBoolean("inVault", false),
            annotations = annotations, characters = characters
        )
    }
}
