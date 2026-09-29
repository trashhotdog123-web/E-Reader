package com.trashhotdog123.ereader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Book(val title:String,val author:String,val genre:String,val progress:Int,val minutes:Int)

class MainActivity : ComponentActivity() {
    private var pageMove: ((Int)->Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ReaderApp { pageMove = it } } }
    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { pageMove?.invoke(1); return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { pageMove?.invoke(-1); return true }
        return super.onKeyDown(keyCode,event)
    }
}

@Composable fun ReaderApp(register:((Int)->Unit)->Unit) {
    var tab by remember { mutableStateOf("Shelf") }
    var dark by remember { mutableStateOf(false) }
    var book by remember { mutableStateOf<Book?>(null) }
    val context = LocalContext.current
    val books = remember { mutableStateListOf(Book("The Great Gatsby","F. Scott Fitzgerald","Classics",38,94),Book("My Notes","You","Study",62,31)) }
    MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
        if(book!=null) ReaderScreen(book!!,dark,{book=null},register)
        else Scaffold(bottomBar={ NavigationBar { listOf("Shelf","Vault","Stats","Settings").forEach { t -> NavigationBarItem(selected=tab==t,onClick={tab=t},icon={},label={Text(t)}) } } }) { pad ->
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item { Text("E-Reader",fontSize=30.sp); Text("Read quietly. Read anywhere.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                if(tab=="Shelf") { item { Button(onClick={}) { Text("＋ Import EPUB / PDF / DOCX") } }; items(books) { b -> BookCard(b){book=b} } }
                if(tab=="Vault") item { Text("Private Vault",fontSize=24.sp); Text("Books hidden from your normal shelf.") }
                if(tab=="Stats") item { Text("Your reading",fontSize=24.sp); Spacer(Modifier.height(12.dp)); Text("Today  •  25 min"); Text("This week  •  3h 42m"); Text("Average  •  238 WPM") }
                if(tab=="Settings") item { Text("Reading settings",fontSize=24.sp); Row(verticalAlignment=Alignment.CenterVertically){Text("Dark mode");Spacer(Modifier.weight(1f));Switch(dark,{dark=it})}; Text("Volume buttons: previous / next page"); Text("Triple power-button launch needs an Android/system shortcut or accessibility integration."); Button(onClick={context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("Accessibility settings")}; Text("Focus Mode is controlled by Android/Digital Wellbeing; the app can guide you there but cannot silently enable it on every device.") }
            }
        }
    }
}

@Composable fun BookCard(b:Book,onClick:()->Unit) { Card(Modifier.fillMaxWidth().clickable{onClick()}) { Column(Modifier.padding(18.dp)){Text(b.title,fontSize=21.sp);Text(b.author);Text(b.genre,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(10.dp));LinearProgressIndicator({b.progress/100f},Modifier.fillMaxWidth());Text("${b.progress}%  •  ${b.minutes} min left",fontSize=13.sp)}} }

@Composable fun ReaderScreen(b:Book,dark:Boolean,back:()->Unit,register:((Int)->Unit)->Unit) {
    var page by remember { mutableIntStateOf(b.progress) }
    var mode by remember { mutableStateOf("Scroll") }
    var auto by remember { mutableStateOf(false) }
    val paragraphs=listOf("CHAPTER ONE","In my younger and more vulnerable years my father gave me some advice that I've been turning over in my mind ever since.","Whenever you feel like criticizing anyone, just remember that all the people in this world haven't had the advantages that you've had.","Reading should feel calm, deliberate, and uninterrupted.")
    LaunchedEffect(Unit) { register { delta -> page=(page+delta).coerceIn(0,100) } }
    Scaffold(topBar={TopAppBar(title={Text(b.title)},navigationIcon={TextButton(back){Text("‹")}})},bottomBar={Column(Modifier.padding(horizontal=18.dp,vertical=8.dp)){Text("While reading  •  $page%  •  ${((100-page)*b.minutes/100).coerceAtLeast(1)} min left",fontSize=13.sp);LinearProgressIndicator({page/100f},Modifier.fillMaxWidth());Row{TextButton({mode=if(mode=="Scroll")"Flip" else "Scroll"}){Text(mode)};Spacer(Modifier.weight(1f));TextButton({auto=!auto}){Text(if(auto)"Auto ✓" else "Auto")}}}}) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(if(dark)Color(0xFF171614) else Color(0xFFF6F1E8)).padding(24.dp)) {
            Text("Page ${page.coerceAtLeast(1)} / 100",fontSize=12.sp,color=Color.Gray);Spacer(Modifier.height(18.dp))
            LazyColumn { items(paragraphs) { Text(it,fontSize=19.sp,lineHeight=31.sp,fontFamily=FontFamily.Serif,modifier=Modifier.padding(bottom=22.dp)) } }
        }
    }
}
