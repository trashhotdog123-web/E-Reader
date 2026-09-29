package com.trashhotdog123.ereader

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun WpmTestDialog(onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val sample = remember {
        """
        Reading speed is personal, and a useful reading tool should adapt to the reader rather than pressure them. For this short calibration, read the passage at the pace that feels natural. Try not to skim, and do not stop to analyze every sentence. The goal is to estimate how quickly you normally move through ordinary prose.

        A good reading session includes small moments of attention. You may slow down for an unfamiliar word, pause at the end of a paragraph, or move backward when a sentence does not make sense. Those corrections are normal. The reader should learn from the steady parts of your session instead of treating every scroll as perfect data.

        When you reach the end, press Finish. The app will use the passage word count and your elapsed time to create a baseline WPM. Later sessions gently adapt that baseline using forward movement while ignoring quick reversals that look like accidental turns.
        """.trimIndent()
    }
    var started by remember { mutableStateOf(false) }
    var startAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(started) {
        while (started) {
            elapsed = SystemClock.elapsedRealtime() - startAt
            delay(500)
        }
    }

    val wordCount = remember(sample) { sample.split(Regex("\\s+")).size }
    val liveWpm = if (elapsed > 0) (wordCount / max(elapsed / 60000.0, 0.02)).roundToInt() else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find your natural WPM") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Read the whole passage normally. Finish when you reach the end.")
                Text("Elapsed: " + (elapsed / 1000L) + "s", fontSize = 13.sp)
                Card {
                    Text(
                        sample,
                        Modifier.padding(17.dp),
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        lineHeight = 29.sp
                    )
                }
                Text(
                    wordCount.toString() + " words",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (!started) {
                Button(onClick = {
                    started = true
                    startAt = SystemClock.elapsedRealtime()
                }) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start test")
                }
            } else {
                Button(onClick = { onSave(liveWpm.coerceIn(80, 1200)) }) {
                    Text("Finish  •  " + liveWpm + " WPM")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
