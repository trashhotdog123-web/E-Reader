package com.trashhotdog123.ereader

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

fun dictionaryLookup(context: Context, word: String) {
    val clean = word.trim().trim(',', '.', ':', ';', '!', '?', '(', ')', '[', ']', '"', '\'')
    if (clean.isBlank()) return

    val process = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, clean)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }

    if (context.packageManager.queryIntentActivities(process, 0).isNotEmpty()) {
        context.startActivity(Intent.createChooser(process, "Define " + clean))
    } else {
        val encoded = URLEncoder.encode(clean, "UTF-8")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.merriam-webster.com/dictionary/" + encoded)))
    }
}

fun firstWord(text: String): String =
    text.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

fun candidateNames(text: String): List<String> =
    Regex("\\b[A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,})?\\b")
        .findAll(text)
        .map { it.value }
        .distinct()
        .take(8)
        .toList()

fun characterRecall(name: String, pageText: String): String {
    val sentences = pageText.split(Regex("(?<=[.!?])\\s+"))
        .filter { it.contains(name, ignoreCase = true) }
        .take(2)

    return if (sentences.isEmpty()) {
        "No nearby description was found for " + name +
            ". Save a note the next time this character is described."
    } else {
        "From this passage: " + sentences.joinToString(" ")
    }
}
