package com.trashhotdog123.ereader

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun StatsUi(
    modifier: Modifier,
    stats: ReaderStats,
    wpm: Int,
    books: List<Book>,
    onWpmTest: () -> Unit
) {
    val progress = books.filter { !it.inVault }.maxOfOrNull { it.progressRatio } ?: 0f
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Your reading", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            Text("Small feedback loops, no pressure.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatMini("Streak", stats.streakDays.toString(), Modifier.weight(1f))
                StatMini("WPM", wpm.toString(), Modifier.weight(1f))
                StatMini("Pages", stats.pagesRead.toString(), Modifier.weight(1f))
            }
        }
        item {
            SettingsCard("Reading habit") {
                Text(stats.totalMinutes.toString() + " minutes logged")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (stats.totalMinutes % 30) / 30f },
                    Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(5.dp))
                Text((stats.totalMinutes % 30).toString() + "/30 minute daily goal",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("Shelf progress") {
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(5.dp))
                Text((progress * 100f).roundToInt().toString() + "% deepest current progress",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedButton(onClick = onWpmTest, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Speed, null)
                Spacer(Modifier.width(8.dp))
                Text("Calibrate my WPM")
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SettingsUi(
    modifier: Modifier,
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    onWpmTest: () -> Unit,
    onFocusAccess: () -> Unit,
    onAccessibility: () -> Unit,
    focusHasAccess: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Reading settings", fontSize = 29.sp, fontWeight = FontWeight.SemiBold) }

        item {
            SettingsCard("Page color") {
                ChoiceRow(listOf("Cream", "Sepia", "Night"), settings.theme) {
                    onChange(settings.copy(theme = it))
                }
            }
        }

        item {
            SettingsCard("Font & size") {
                ChoiceRow(listOf("Serif", "Sans", "Mono"), settings.font) {
                    onChange(settings.copy(font = it))
                }
                Spacer(Modifier.height(8.dp))
                Text("Size " + settings.fontSize.roundToInt().toString() + "sp")
                Slider(
                    value = settings.fontSize,
                    onValueChange = { onChange(settings.copy(fontSize = it)) },
                    valueRange = 16f..30f
                )
                Text("Use a larger size and wider spacing for long sessions.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsCard("Reading mode") {
                ChoiceRow(listOf("Scroll", "Flip"), settings.mode) {
                    onChange(settings.copy(mode = it))
                }
                Text("Flip gives discrete pages with an animated transition. Scroll gives continuous reading.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsCard("Auto page turn") {
                val enabled = settings.autoTurnSeconds > 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (enabled) settings.autoTurnSeconds.toString() + " seconds" else "Off")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            onChange(settings.copy(autoTurnSeconds = if (it) 10 else 0))
                        }
                    )
                }
                Slider(
                    value = settings.autoTurnSeconds.toFloat(),
                    onValueChange = {
                        val seconds = (it / 5f).roundToInt() * 5
                        onChange(settings.copy(autoTurnSeconds = seconds))
                    },
                    valueRange = 0f..60f,
                    steps = 11
                )
                Text("The reader still lets you pause auto-turn anytime.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsCard("Reading Focus") {
                Text(
                    "Uses Android's interruption-control API when permission is granted. It is a reading-focused DND mode, not a universal Digital Wellbeing Focus Mode switch.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (focusHasAccess) "Permission granted" else "Permission needed", Modifier.weight(1f))
                    OutlinedButton(onClick = onFocusAccess) { Text("System settings") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Start Focus when I begin reading", Modifier.weight(1f))
                    Switch(
                        checked = settings.focusOnStart,
                        onCheckedChange = { onChange(settings.copy(focusOnStart = it)) }
                    )
                }
            }
        }

        item {
            SettingsCard("Physical controls & quick launch") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VolumeUp, null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Volume buttons", fontWeight = FontWeight.SemiBold)
                        Text("Down = next page • Up = previous page while reading",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Android accessibility settings")
                }
                Text(
                    "The project also includes a Quick Read home-screen shortcut and Quick Settings tile. Android does not expose a universal third-party 3× power-button launch hook.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("WPM & comfort") {
                OutlinedButton(onClick = onWpmTest, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Speed, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run WPM test  •  current " + settings.defaultWpm)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtle haptics", Modifier.weight(1f))
                    Switch(
                        checked = settings.haptics,
                        onCheckedChange = { onChange(settings.copy(haptics = it)) }
                    )
                }
            }
        }

        item {
            SettingsCard("Supported offline formats") {
                Text("EPUB • PDF • DOCX • TXT • HTML • RTF")
                Text(
                    "EPUB/DOCX/TXT/HTML/RTF are reflowed into reader pages. PDFs use Android's native PDF renderer. Imported books are copied into app-private storage.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("Optional dictionary") {
                Text("Long-press a passage and choose Define. The app first checks Android text-processing apps, then offers a Merriam-Webster web definition if none is installed.")
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.merriam-webster.com/"))
                    runCatching { context.startActivity(intent) }
                }) { Text("Open Merriam-Webster") }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach {
            FilterChip(
                selected = it == selected,
                onClick = { onSelect(it) },
                label = { Text(it) }
            )
        }
    }
}
