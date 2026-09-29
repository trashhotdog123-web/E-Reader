package com.trashhotdog123.ereader

import java.util.UUID

enum class BookFormat { PDF, EPUB, DOCX, TXT, HTML, RTF, UNKNOWN;
    companion object {
        fun fromName(name: String): BookFormat = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> PDF
            "epub" -> EPUB
            "docx" -> DOCX
            "txt" -> TXT
            "html", "htm" -> HTML
            "rtf" -> RTF
            else -> UNKNOWN
        }
    }
}

data class AnnotationData(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val page: Int,
    val note: String = "",
    val type: String = "highlight",
    val createdAt: Long = System.currentTimeMillis()
)

data class CharacterCard(
    val name: String,
    val summary: String,
    val page: Int
)

data class Book(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val format: BookFormat,
    val title: String,
    val author: String = "Unknown author",
    val synopsis: String = "",
    val genre: String = "Unsorted",
    val progressPage: Int = 0,
    val progressOffset: Int = 0,
    val progressRatio: Float = 0f,
    val calibratedWpm: Int = 220,
    val inVault: Boolean = false,
    val annotations: List<AnnotationData> = emptyList(),
    val characters: List<CharacterCard> = emptyList()
)

data class ReaderSettings(
    val theme: String = "Cream",
    val font: String = "Serif",
    val fontSize: Float = 19f,
    val lineSpacing: Float = 1.55f,
    val mode: String = "Scroll",
    val autoTurnSeconds: Int = 0,
    val focusOnStart: Boolean = false,
    val haptics: Boolean = true,
    val defaultWpm: Int = 220
)

data class ReaderStats(
    val totalMinutes: Int = 0,
    val pagesRead: Int = 0,
    val streakDays: Int = 0,
    val lastReadDate: String = ""
)

data class ParsedDocument(
    val pages: List<String>,
    val pageWords: List<Int>,
    val totalWords: Int,
    val isPdf: Boolean
)
