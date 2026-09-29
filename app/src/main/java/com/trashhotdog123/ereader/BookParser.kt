package com.trashhotdog123.ereader

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Html
import java.io.File
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

object BookParser {
    fun parse(file: File, format: BookFormat): ParsedDocument = when (format) {
        BookFormat.PDF -> parsePdf(file)
        BookFormat.EPUB -> parseEpub(file)
        BookFormat.DOCX -> parseDocx(file)
        BookFormat.HTML -> paginate(normalizeHtml(file.readText(Charsets.UTF_8)))
        BookFormat.RTF -> paginate(stripRtf(file.readText(Charsets.UTF_8)))
        BookFormat.TXT -> paginate(file.readText(Charsets.UTF_8))
        BookFormat.UNKNOWN -> error("Unsupported file format")
    }

    private fun parsePdf(file: File): ParsedDocument {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val count = renderer.pageCount
        renderer.close(); fd.close()
        val words = 450
        return ParsedDocument(List(count) { "PDF page ${it + 1}" }, List(count) { words }, count * words, true)
    }

    private fun parseEpub(file: File): ParsedDocument {
        ZipFile(file).use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: error("EPUB container missing")
            val container = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
            val root = xml(container).getElementsByTagName("rootfile").item(0)
                ?: error("EPUB package metadata missing")
            val opfPath = root.attributes.getNamedItem("full-path")?.nodeValue
                ?: error("EPUB package path missing")
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB package file missing")
            val opf = xml(zip.getInputStream(opfEntry).bufferedReader().use { it.readText() })
            val manifest = opf.getElementsByTagName("item")
            val hrefById = mutableMapOf<String, String>()
            for (i in 0 until manifest.length) {
                val node = manifest.item(i)
                val id = node.attributes.getNamedItem("id")?.nodeValue ?: continue
                val href = node.attributes.getNamedItem("href")?.nodeValue ?: continue
                hrefById[id] = href
            }
            val base = opfPath.substringBeforeLast('/', "")
            val spine = opf.getElementsByTagName("itemref")
            val chunks = mutableListOf<String>()
            for (i in 0 until spine.length) {
                val idref = spine.item(i).attributes.getNamedItem("idref")?.nodeValue ?: continue
                val href = hrefById[idref] ?: continue
                val cleanHref = URLDecoder.decode(href.substringBefore('#').substringBefore('?'), "UTF-8")
                val entryPath = if (base.isBlank()) cleanHref else "$base/$cleanHref"
                val entry = zip.getEntry(entryPath) ?: zip.entries().asSequence().firstOrNull { it.name.equals(entryPath, true) }
                if (entry != null) {
                    val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val text = normalizeHtml(html)
                    if (text.isNotBlank()) chunks += text
                }
            }
            return paginate(chunks.joinToString("\n\n"))
        }
    }

    private fun parseDocx(file: File): ParsedDocument {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: error("DOCX document.xml missing")
            val xmlText = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val document = xml(xmlText)
            val paragraphs = document.getElementsByTagName("w:p")
            val out = StringBuilder()
            for (i in 0 until paragraphs.length) {
                val ts = paragraphs.item(i).childNodes
                val textNodes = (0 until ts.length).mapNotNull { index ->
                    val node = ts.item(index)
                    if (node.nodeName == "w:r") node.childNodes.asSequence().firstOrNull { it.nodeName == "w:t" }?.textContent else null
                }
                textNodes.forEach { out.append(it) }
                out.append("\n\n")
            }
            return paginate(out.toString())
        }
    }

    private fun xml(value: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(value)))
    }

    private fun normalizeHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u00A0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private fun stripRtf(value: String): String =
        value.replace(Regex("\\'[0-9a-fA-F]{2}"), "")
            .replace(Regex("\\[a-z]+-?\\d* ?"), "")
            .replace(Regex("[{}]"), "")
            .trim()

    private fun paginate(text: String): ParsedDocument {
        val cleaned = text.replace(Regex("\\n\\s*\\n"), "\n\n").trim()
        if (cleaned.isBlank()) return ParsedDocument(listOf(""), listOf(0), 0, false)
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val pageSize = 450
        val pages = words.chunked(pageSize).map { it.joinToString(" ") }
        val counts = pages.map { it.split(Regex("\\s+")).count { w -> w.isNotBlank() } }
        return ParsedDocument(pages, counts, words.size, false)
    }
}
