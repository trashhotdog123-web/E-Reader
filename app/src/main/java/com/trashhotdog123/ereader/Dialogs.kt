package com.trashhotdog123.ereader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditBookDialog(book: Book, onDismiss: () -> Unit, onSave: (Book) -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var genre by remember { mutableStateOf(book.genre) }
    var synopsis by remember { mutableStateOf(book.synopsis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit book details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Book name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre / shelf") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(synopsis, { synopsis = it }, label = { Text("Synopsis") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(book.copy(
                    title = title.trim().ifBlank { "Untitled" },
                    author = author.trim().ifBlank { "Unknown author" },
                    genre = genre.trim().ifBlank { "Unsorted" },
                    synopsis = synopsis.trim()
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
