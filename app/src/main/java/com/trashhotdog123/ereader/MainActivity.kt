package com.trashhotdog123.ereader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private enum class Tab { SHELF, VAULT, STATS, SETTINGS }

class MainActivity : ComponentActivity() {
    private var volumeHandler: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EReaderThemeAndApp(registerVolume = { volumeHandler = it })
        }
    }

    override fun onKeyDown(code: Int, event: KeyEvent?): Boolean {
        if (volumeHandler != null) {
            if (code == KeyEvent.KEYCODE_VOLUME_DOWN) { volumeHandler?.invoke(1); return true }
            if (code == KeyEvent.KEYCODE_VOLUME_UP) { volumeHandler?.invoke(-1); return true }
        }
        return super.onKeyDown(code, event)
    }

    override fun onDestroy() {
        volumeHandler = null
        super.onDestroy()
    }
}

@Composable
private fun EReaderThemeAndApp(registerVolume: (((Int) -> Unit)?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { LibraryStore(context) }
    var books by remember { mutableStateOf(store.loadBooks()) }
    var settings by remember { mutableStateOf(store.settings()) }
    var stats by remember { mutableStateOf(store.stats()) }
    var tab by remember { mutableStateOf(Tab.SHELF) }
    var genre by remember { mutableStateOf("All") }
    var openBook by remember { mutableStateOf<Book?>(null) }
    var editBook by remember { mutableStateOf<Book?>(null) }
    var testWpm by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var vaultUnlocked by remember { mutableStateOf(false) }

    fun reload() {
        books = store.loadBooks()
        settings = store.settings()
        stats = store.stats()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        context.lifecycleScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                runCatching { importBook(context, store, uri) }
            }
            withContext(Dispatchers.Main) {
                importing = false
                reload()
                Toast.makeText(context, "Imported " + uris.size + " book(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    MaterialTheme(colorScheme = appColors(settings.theme)) {
        if (openBook != null) {
            ReaderUi(
                book = openBook!!,
                settings = settings,
                registerVolume = registerVolume,
                onBack = { openBook = null; reload() },
                onProgress = { id, page, offset, ratio, wpm -> store.updateProgress(id, page, offset, ratio, wpm) },
                onSession = { mins, pages -> store.recordSession(mins, pages); reload() },
                onBookUpdate = { updated -> store.upsert(updated); reload(); openBook = updated },
                onSettings = { next -> settings = next; store.saveSettings(next) }
            )
            return@MaterialTheme
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == Tab.SHELF, { tab = Tab.SHELF }, { Icon(Icons.Outlined.Home, null) }, label = { Text("Shelf") })
                    NavigationBarItem(tab == Tab.VAULT, { tab = Tab.VAULT }, { Icon(Icons.Outlined.Lock, null) }, label = { Text("Vault") })
                    NavigationBarItem(tab == Tab.STATS, { tab = Tab.STATS }, { Icon(Icons.Outlined.BarChart, null) }, label = { Text("Stats") })
                    NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("Settings") })
                }
            }
        ) { pad ->
            when (tab) {
                Tab.SHELF -> ShelfUi(
                    Modifier.padding(pad), books.filter { !it.inVault }, books, genre, { genre = it }, importing,
                    { launcher.launch("*/*") }, { openBook = it }, { editBook = it },
                    { book -> deleteBook(book, store); reload() },
                    { book -> toggleVault(context, book, store) { reload() } }
                )
                Tab.VAULT -> VaultUi(
                    Modifier.padding(pad), books.filter { it.inVault }, vaultUnlocked, { vaultUnlocked = true },
                    { openBook = it }, { editBook = it }, { book -> deleteBook(book, store); reload() },
                    { book -> toggleVault(context, book, store) { reload() } }
                )
                Tab.STATS -> StatsUi(Modifier.padding(pad), stats, settings.defaultWpm, books, { testWpm = true })
                Tab.SETTINGS -> SettingsUi(
                    Modifier.padding(pad), settings,
                    { next -> settings = next; store.saveSettings(next) },
                    { testWpm = true },
                    { FocusController.openAccessSettings(context) },
                    { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    FocusController.hasAccess(context)
                )
            }
        }
    }

    editBook?.let { book ->
        EditBookDialog(book, { editBook = null }) {
            store.upsert(it); editBook = null; reload()
        }
    }

    if (testWpm) {
        WpmTestDialog(
            onDismiss = { testWpm = false },
            onSave = { store.setWpm(it); testWpm = false; reload() }
        )
    }
}

private fun importBook(context: Context, store: LibraryStore, uri: Uri): Book {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else "Imported book"
    } ?: "Imported book"

    val format = BookFormat.fromName(name)
    require(format != BookFormat.UNKNOWN) { "Unsupported format" }
    val id = java.util.UUID.randomUUID().toString()
    val ext = name.substringAfterLast('.', "bin")
    val file = File(context.filesDir, "books/" + id + "." + ext)
    file.parentFile?.mkdirs()

    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Cannot read file" }
        FileOutputStream(file).use { output -> input.copyTo(output, 64 * 1024) }
    }

    runCatching { BookParser.parse(file, format) }.getOrElse {
        file.delete()
        throw it
    }

    return Book(
        id = id,
        fileName = name,
        filePath = file.absolutePath,
        format = format,
        title = name.substringBeforeLast('.').ifBlank { "Untitled" }
    ).also { store.upsert(it) }
}

private fun deleteBook(book: Book, store: LibraryStore) {
    runCatching { File(book.filePath).delete() }
    store.remove(book.id)
}

private fun toggleVault(context: Context, book: Book, store: LibraryStore, onDone: () -> Unit) {
    context.lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            if (book.inVault) {
                val restored = File(book.filePath.removeSuffix(".vault"))
                VaultCrypto.decrypt(File(book.filePath), restored)
                File(book.filePath).delete()
                store.upsert(book.copy(filePath = restored.absolutePath, inVault = false))
            } else {
                val source = File(book.filePath)
                val vault = File(source.absolutePath + ".vault")
                VaultCrypto.encrypt(source, vault)
                source.delete()
                store.upsert(book.copy(filePath = vault.absolutePath, inVault = true))
            }
        }.onFailure {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Vault error: " + (it.message ?: "unknown error"), Toast.LENGTH_LONG).show()
            }
        }.onSuccess {
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}

private fun appColors(theme: String): androidx.compose.material3.ColorScheme = when (theme) {
    "Night" -> darkColorScheme(
        background = androidx.compose.ui.graphics.Color(0xFF171512),
        surface = androidx.compose.ui.graphics.Color(0xFF201D18),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2B261F),
        primary = androidx.compose.ui.graphics.Color(0xFFE8B36A)
    )
    "Sepia" -> lightColorScheme(
        background = androidx.compose.ui.graphics.Color(0xFFF0E4C9),
        surface = androidx.compose.ui.graphics.Color(0xFFF6EBD4),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8D7B8),
        primary = androidx.compose.ui.graphics.Color(0xFF7D5A32)
    )
    else -> lightColorScheme(
        background = androidx.compose.ui.graphics.Color(0xFFF8F2E8),
        surface = androidx.compose.ui.graphics.Color(0xFFFBF7EF),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDE6D9),
        primary = androidx.compose.ui.graphics.Color(0xFF805C36)
    )
}
