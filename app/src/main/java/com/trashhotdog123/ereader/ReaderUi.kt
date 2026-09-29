package com.trashhotdog123.ereader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderUi(
    book: Book,
    settings: ReaderSettings,
    registerVolume: (((Int) -> Unit)?) -> Unit,
    onBack: () -> Unit,
    onProgress: (String, Int, Int, Float, Int) -> Unit,
    onSession: (Int, Int) -> Unit,
    onBookUpdate: (Book) -> Unit,
    onSettings: (ReaderSettings) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val telemetry = remember(book.id) { ReadingTelemetry(max(book.calibratedWpm, settings.defaultWpm)) }

    var readable by remember(book.filePath) { mutableStateOf<File?>(null) }
    var document by remember(book.filePath) { mutableStateOf<ParsedDocument?>(null) }
    var error by remember(book.filePath) { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(book.progressPage) }
    var wpm by remember { mutableIntStateOf(max(book.calibratedWpm, settings.defaultWpm)) }
    var auto by remember { mutableStateOf(false) }
    var toolsPage by remember { mutableIntStateOf(0) }
    var toolsText by remember { mutableStateOf<String?>(null) }
    var noteOpen by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var sessionPages by remember { mutableIntStateOf(0) }
    val sessionStart = remember { SystemClock.elapsedRealtime() }

    LaunchedEffect(book.filePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = if (book.inVault) {
                    val temp = File(context.cacheDir, "reader-" + book.id + "." + book.format.name.lowercase())
                    VaultCrypto.decrypt(File(book.filePath), temp)
                    temp
                } else {
                    File(book.filePath)
                }
                readable = file
                document = BookParser.parse(file, book.format)
                page = page.coerceIn(0, max(0, document!!.pages.lastIndex))
                telemetry.reset(page)
            }.onFailure {
                error = it.message ?: "Unable to open this book"
            }
        }
    }

    val focusWasStarted = remember(book.id) {
        if (settings.focusOnStart) FocusController.start(context) else false
    }

    DisposableEffect(book.id) {
        registerVolume { delta ->
            val doc = document
            if (doc != null && settings.mode == "Flip") {
                page = (page + delta).coerceIn(0, max(0, doc.pages.lastIndex))
            } else if (doc != null) {
                scope.launch {
                    val target = (listState.firstVisibleItemIndex + delta)
                        .coerceIn(0, max(0, doc.pages.lastIndex))
                    listState.animateScrollToItem(target)
                }
            }
        }
        onDispose {
            registerVolume(null)
            val mins = ((SystemClock.elapsedRealtime() - sessionStart) / 60000L).toInt()
            onSession(mins, sessionPages)
            if (focusWasStarted) FocusController.stop(context)
            readable?.let { if (book.inVault) it.delete() }
        }
    }

    LaunchedEffect(document, settings.mode) {
        val doc = document ?: return@LaunchedEffect
        if (settings.mode == "Scroll") {
            listState.scrollToItem(book.progressPage.coerceIn(0, doc.pages.lastIndex))
        }
    }

    LaunchedEffect(document, settings.mode, listState) {
        val doc = document ?: return@LaunchedEffect
        if (settings.mode != "Scroll") return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(250)
            .collect { index ->
                page = index.coerceIn(0, doc.pages.lastIndex)
                telemetry.moveTo(page, wordsPerPage(doc, page))
                wpm = telemetry.currentWpm()
                sessionPages += 1
                onProgress(book.id, page, 0, progressRatio(doc, page), wpm)
            }
    }

    LaunchedEffect(document, settings.mode, page) {
        val doc = document ?: return@LaunchedEffect
        if (settings.mode != "Flip") return@LaunchedEffect
        telemetry.moveTo(page, wordsPerPage(doc, page))
        wpm = telemetry.currentWpm()
        sessionPages += 1
        onProgress(book.id, page, 0, progressRatio(doc, page), wpm)
    }

    LaunchedEffect(auto, settings.mode, settings.autoTurnSeconds, page, document) {
        val doc = document ?: return@LaunchedEffect
        if (!auto || settings.autoTurnSeconds <= 0) return@LaunchedEffect
        delay(settings.autoTurnSeconds * 1000L)
        if (settings.mode == "Flip") {
            if (page < doc.pages.lastIndex) page += 1
        } else {
            val next = (listState.firstVisibleItemIndex + 1).coerceAtMost(doc.pages.lastIndex)
            listState.animateScrollToItem(next)
        }
    }

    val doc = document
    val bg = readerBackground(settings.theme)
    val fg = readerForeground(settings.theme)
    val font = when (settings.font) {
        "Sans" -> FontFamily.SansSerif
        "Mono" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            SmallTopAppBar(
                title = {
                    Column {
                        Text(book.title, maxLines = 1, fontSize = 18.sp)
                        Text(book.author, maxLines = 1, fontSize = 11.sp, color = fg.copy(alpha = .55f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        val marked = book.annotations.any { it.type == "bookmark" && it.page == page }
                        val next = book.annotations.filterNot { it.type == "bookmark" && it.page == page }.toMutableList()
                        if (!marked) next += AnnotationData(text = "", page = page, type = "bookmark")
                        onBookUpdate(book.copy(annotations = next))
                    }) {
                        Icon(Icons.Outlined.Bookmark, "Bookmark")
                    }
                    IconButton(onClick = { auto = !auto }) {
                        Icon(if (auto) Icons.Outlined.Pause else Icons.Outlined.Timer, "Auto page turn")
                    }
                    IconButton(onClick = {
                        onSettings(settings.copy(mode = if (settings.mode == "Scroll") "Flip" else "Scroll"))
                    }) {
                        Icon(Icons.Outlined.SwapVert, "Change reading mode")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = bg)
            )
        },
        bottomBar = {
            if (doc != null) {
                val ratio = progressRatio(doc, page)
                val mins = ceil(remainingWords(doc, page) / max(1, wpm).toDouble()).toInt().coerceAtLeast(1)
                Surface(color = bg, shadowElevation = 5.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("While reading", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                (ratio * 100f).roundToInt().toString() + "%  •  " +
                                    mins + " min left  •  " + wpm + " WPM",
                                fontSize = 12.sp, color = fg.copy(alpha = .62f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { ratio }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val bookmarked = book.annotations.any { it.type == "bookmark" && it.page == page }
                            Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.MenuBook, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Page " + (page + 1) + "/" + doc.pages.size, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            Text(if (settings.mode == "Flip") "FLIP" else "SCROLL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(9.dp))
                            Text(if (auto) "AUTO " + settings.autoTurnSeconds + "s" else "AUTO OFF",
                                fontSize = 10.sp, color = fg.copy(alpha = .5f))
                        }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                error != null -> Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Could not open this book", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(error!!, color = fg.copy(alpha = .65f))
                }

                doc == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                settings.mode == "Flip" -> {
                    PaperFlip(targetPage = page) { shown ->
                        ReaderPage(
                            book = book,
                            text = if (doc.isPdf) null else doc.pages[shown],
                            pdf = if (doc.isPdf) readable else null,
                            pdfPage = shown,
                            page = shown,
                            font = font,
                            size = settings.fontSize,
                            spacing = settings.lineSpacing,
                            fg = fg,
                            onLongPress = { toolsText = it; toolsPage = shown }
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(doc.pages, key = { index, _ -> "reader-page-" + index }) { index, content ->
                            ReaderPage(
                                book = book,
                                text = if (doc.isPdf) null else content,
                                pdf = if (doc.isPdf) readable else null,
                                pdfPage = index,
                                page = index,
                                font = font,
                                size = settings.fontSize,
                                spacing = settings.lineSpacing,
                                fg = fg,
                                onLongPress = { toolsText = it; toolsPage = index }
                            )
                        }
                    }
                }
            }
        }
    }

    toolsText?.let { passage ->
        ReadingTools(
            passage = passage,
            highlighted = book.annotations.any { it.type == "highlight" && it.page == toolsPage && it.text == passage },
            onDismiss = { toolsText = null },
            onHighlight = {
                val list = book.annotations.filterNot {
                    it.type == "highlight" && it.page == toolsPage && it.text == passage
                }.toMutableList()
                if (book.annotations.none { it.type == "highlight" && it.page == toolsPage && it.text == passage }) {
                    list += AnnotationData(text = passage, page = toolsPage, type = "highlight")
                }
                onBookUpdate(book.copy(annotations = list))
                toolsText = null
            },
            onNote = { noteText = ""; noteOpen = true },
            onDefine = { dictionaryLookup(context, firstWord(passage)) },
            onCharacter = {
                val names = candidateNames(passage)
                if (names.isEmpty()) {
                    Toast.makeText(context, "No obvious name found in this page.", Toast.LENGTH_SHORT).show()
                } else {
                    val name = names.first()
                    val summary = characterRecall(name, passage)
                    val old = book.characters.filterNot { it.name.equals(name, true) }
                    onBookUpdate(book.copy(characters = old + CharacterCard(name, summary, toolsPage)))
                    Toast.makeText(context, "Saved " + name + " to Character Memory", Toast.LENGTH_SHORT).show()
                }
                toolsText = null
            }
        )
    }

    if (noteOpen) {
        AlertDialog(
            onDismissRequest = { noteOpen = false },
            title = { Text("Add note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Your note") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onBookUpdate(book.copy(
                        annotations = book.annotations + AnnotationData(
                            text = toolsText.orEmpty(), page = toolsPage, note = noteText, type = "note"
                        )
                    ))
                    noteOpen = false
                    toolsText = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { noteOpen = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PaperFlip(targetPage: Int, content: @Composable (Int) -> Unit) {
    var shown by remember { mutableIntStateOf(targetPage) }
    val angle = remember { Animatable(0f) }

    LaunchedEffect(targetPage) {
        if (targetPage == shown) return@LaunchedEffect
        angle.snapTo(0f)
        angle.animateTo(90f, tween(150))
        shown = targetPage
        angle.animateTo(0f, tween(180))
    }

    Box(
        Modifier.fillMaxSize().graphicsLayer {
            rotationY = angle.value
            cameraDistance = 18f * density
        }
    ) {
        content(shown)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderPage(
    book: Book,
    text: String?,
    pdf: File?,
    pdfPage: Int,
    page: Int,
    font: FontFamily,
    size: Float,
    spacing: Float,
    fg: Color,
    onLongPress: (String) -> Unit
) {
    val highlighted = book.annotations.any { it.type == "highlight" && it.page == page }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(17.dp),
        tonalElevation = 1.dp,
        color = if (highlighted) Color(0x22F0B34A) else Color.Transparent
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 15.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text((page + 1).toString(), fontSize = 10.sp, color = fg.copy(alpha = .45f))
                Spacer(Modifier.weight(1f))
                Text("E-READER", fontSize = 8.sp, letterSpacing = 2.sp, color = fg.copy(alpha = .3f))
            }
            Spacer(Modifier.height(13.dp))

            if (text != null) {
                val passage = text.trim()
                Text(
                    text = passage.ifBlank { " " },
                    color = fg,
                    fontFamily = font,
                    fontSize = size.sp,
                    lineHeight = (size * spacing).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(passage) }
                        )
                        .padding(5.dp)
                )
            } else if (pdf != null) {
                PdfPageImage(pdf, pdfPage, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PdfPageImage(file: File, page: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, file.absolutePath, page) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val pdfPage = renderer.openPage(page)
                val width = 1400
                val height = (width.toFloat() * pdfPage.height / pdfPage.width).roundToInt().coerceAtLeast(400)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pdfPage.close()
                renderer.close()
                fd.close()
                bmp
            }.getOrNull()
        }
    }

    if (bitmap == null) {
        Box(modifier.height(460.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF page " + (page + 1),
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}

@Composable
private fun ReadingTools(
    passage: String,
    highlighted: Boolean,
    onDismiss: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onDefine: () -> Unit,
    onCharacter: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reading tools") },
        text = {
            Column {
                Text(passage.take(320) + if (passage.length > 320) "…" else "")
                Spacer(Modifier.height(10.dp))
                Text("Quick tools stay local except when you choose an online dictionary.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDefine) { Text("Define") }
                TextButton(onClick = onCharacter) { Text("Character") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onHighlight) { Text(if (highlighted) "Unhighlight" else "Highlight") }
                TextButton(onClick = onNote) { Text("Note") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

private fun wordsPerPage(doc: ParsedDocument, page: Int): Int =
    doc.pageWords.getOrNull(page.coerceIn(0, doc.pageWords.lastIndex))?.coerceAtLeast(1) ?: 450

private fun progressRatio(doc: ParsedDocument, page: Int): Float {
    if (doc.totalWords <= 0) return 0f
    val i = page.coerceIn(0, doc.pages.lastIndex)
    val consumed = doc.pageWords.take(i).sum()
    return (consumed / doc.totalWords.toFloat()).coerceIn(0f, 1f)
}

private fun remainingWords(doc: ParsedDocument, page: Int): Int {
    if (doc.totalWords <= 0) return 0
    val i = page.coerceIn(0, doc.pages.lastIndex)
    return (doc.totalWords - doc.pageWords.take(i).sum()).coerceAtLeast(0)
}

private fun readerBackground(theme: String) = when (theme) {
    "Night" -> Color(0xFF171512)
    "Sepia" -> Color(0xFFF0E4C9)
    else -> Color(0xFFF8F2E8)
}

private fun readerForeground(theme: String) = when (theme) {
    "Night" -> Color(0xFFE9E0D2)
    else -> Color(0xFF3E362C)
}
