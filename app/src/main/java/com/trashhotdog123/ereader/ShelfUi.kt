package com.trashhotdog123.ereader

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ShelfUi(
    modifier: Modifier,
    books: List<Book>,
    allBooks: List<Book>,
    selectedGenre: String,
    onGenre: (String) -> Unit,
    importing: Boolean,
    onImport: () -> Unit,
    onOpen: (Book) -> Unit,
    onEdit: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onVault: (Book) -> Unit
) {
    val genres = listOf("All") + allBooks.map { it.genre.ifBlank { "Unsorted" } }.distinct().sorted()
    val visible = if (selectedGenre == "All") books else books.filter { it.genre.ifBlank { "Unsorted" } == selectedGenre }
    val continueBook = books.firstOrNull { it.progressRatio > 0f }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("E-Reader", fontSize = 31.sp, fontWeight = FontWeight.SemiBold)
                    Text("Read quietly. Keep your place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Offline-ready shelf", fontWeight = FontWeight.SemiBold)
                        Text("Imported books are copied into the app so reading does not depend on Wi‑Fi or data.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Icon(Icons.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
                }
            }
        }

        continueBook?.let { book ->
            item {
                Text("Continue reading", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                ContinueCard(book, onOpen)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Books", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (importing) "Importing…" else "Import")
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { value ->
                    FilterChip(
                        selected = value == selectedGenre,
                        onClick = { onGenre(value) },
                        label = { Text(value) }
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item { EmptyShelf(onImport) }
        } else {
            items(visible, key = { it.id }) { book ->
                BookCard(book, onOpen, onEdit, onDelete, onVault)
            }
        }
    }
}

@Composable
fun ContinueCard(book: Book, onOpen: (Book) -> Unit) {
    Card(
        onClick = { onOpen(book) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(74.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { book.progressRatio.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text((book.progressRatio * 100f).roundToInt().toString() + "%  •  Continue where you left off",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun BookCard(
    book: Book,
    onOpen: (Book) -> Unit,
    onEdit: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onVault: (Book) -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { onOpen(book) },
            onLongClick = { menu = true }
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.size(70.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(book.genre.ifBlank { "Unsorted" }, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(progress = { book.progressRatio.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text((book.progressRatio * 100f).roundToInt().toString() + "%  •  " + book.format.name,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit details") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onEdit(book) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (book.inVault) "Remove from Vault" else "Move to Vault") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        onClick = { menu = false; onVault(book) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete(book) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookCover(book: Book, modifier: Modifier) {
    val initials = book.title.trim().split(Regex("\\s+")).take(2)
        .joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
    Box(
        modifier = modifier.clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Text(initials.ifBlank { "BOOK" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(book.genre.take(12), fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun EmptyShelf(onImport: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.LibraryBooks, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text("No books here yet", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("Import EPUB, PDF, DOCX, TXT, HTML or RTF.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onImport) { Text("Add your first book") }
        }
    }
}

@Composable
fun VaultUi(
    modifier: Modifier,
    books: List<Book>,
    unlocked: Boolean,
    onUnlock: () -> Unit,
    onOpen: (Book) -> Unit,
    onEdit: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onVault: (Book) -> Unit
) {
    if (!unlocked) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(Modifier.size(90.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Private Vault", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Vault files are encrypted with a device-bound Android Keystore key.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onUnlock) { Text("Unlock vault") }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Private Vault", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Text(books.size.toString() + " hidden book(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (books.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nothing hidden yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Use a book menu to move a book here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(books, key = { it.id }) { book ->
                BookCard(book, onOpen, onEdit, onDelete, onVault)
            }
        }
    }
}
