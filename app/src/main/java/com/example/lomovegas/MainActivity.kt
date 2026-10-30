package com.example.lomovegas

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private val Bg = Color(0xFF1E1E1E)
private val Panel = Color(0xFF2B2B2B)
private val VideoClip = Color(0xFF2E6F95)
private val AudioClip = Color(0xFF3C8D5A)
private val SubClip = Color(0xFF8E5BB5)

class MainActivity : ComponentActivity() {
    private val state = EditorState()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { EditorScreen(state) } }
    }
}

fun fmt(ms: Long) = "%d:%02d.%d".format(ms / 60000, ms / 1000 % 60, ms % 1000 / 100)
fun fadeLabel(ms: Long) = if (ms == 0L) "corte" else "${ms / 1000.0}s"

@Composable
fun EditorScreen(s: EditorState) {
    val ctx = LocalContext.current
    val engine = remember { PreviewEngine(ctx, s) }
    DisposableEffect(Unit) { onDispose { engine.release() } }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Subtitle?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            s.importMedia(ctx, uri)
        }
    }

    fun export(h: Int) {
        s.playing = false
        progress = 0
        Exporter.export(ctx, s.tracks, s.subtitles, h,
            onProgress = { progress = it },
            onDone = { progress = null; message = "Exportado en:\n$it" },
            onError = { progress = null; message = "Error: $it" })
    }

    // Reloj de reproducción
    LaunchedEffect(s.playing) {
        if (!s.playing) { engine.pauseAll(); return@LaunchedEffect }
        if (s.playheadMs >= s.totalMs()) s.playheadMs = 0
        var last = SystemClock.elapsedRealtime()
        while (s.playing) {
            delay(40)
            val now = SystemClock.elapsedRealtime()
            s.playheadMs += now - last; last = now
            if (s.playheadMs >= s.totalMs()) { s.playheadMs = s.totalMs(); s.playing = false }
            engine.sync(s.playheadMs, s.playing)
        }
    }
    // Sincronizar al mover el cursor o editar con la reproducción en pausa
    LaunchedEffect(s.playheadMs, s.tracks, s.subtitles) {
        if (!s.playing) engine.sync(s.playheadMs, false)
    }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState())) {
            ToolBtn("Importar") { picker.launch(arrayOf("video/*", "audio/*")) }
            ToolBtn("Exportar 720p") { export(720) }
            ToolBtn("Exportar 1080p") { export(1080) }
        }
        progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }

        Monitor(s, engine)

        Row(Modifier.fillMaxWidth().background(Panel), verticalAlignment = Alignment.CenterVertically) {
            ToolBtn("Inicio") { s.playing = false; s.playheadMs = 0 }
            ToolBtn(if (s.playing) "Pausa" else "▶ Play") { s.playing = !s.playing }
            Text("${fmt(s.playheadMs)} / ${fmt(s.totalMs())}", color = Color.White, fontSize = 12.sp)
        }

        TimelinePanel(s, Modifier.weight(1f)) { editing = it }

        val c = s.selectedClip()
        Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState())) {
            ToolBtn("Dividir") { s.split() }
            ToolBtn("Eliminar") { s.delete() }
            ToolBtn("Ini −") { s.trimStart(-500) }
            ToolBtn("Ini +") { s.trimStart(500) }
            ToolBtn("Fin −") { s.trimEnd(-500) }
            ToolBtn("Fin +") { s.trimEnd(500) }
            ToolBtn("Fund. ent: ${fadeLabel(c?.fadeInMs ?: 0)}") { s.cycleFade(true) }
            ToolBtn("Fund. sal: ${fadeLabel(c?.fadeOutMs ?: 0)}") { s.cycleFade(false) }
        }
        Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState())) {
            ToolBtn("+ Pista V") { s.addTrack(Kind.VIDEO) }
            ToolBtn("+ Pista A") { s.addTrack(Kind.AUDIO) }
            ToolBtn("− Pista") { s.removeTrack() }
            ToolBtn("Pista on/off") { s.toggleTrack() }
            ToolBtn("+ Subtítulo") { editing = s.addSubtitle() }
            ToolBtn("Editar sub.") { s.selectedSub()?.let { editing = it } }
            ToolBtn("Zoom −") { s.zoom = (s.zoom * 0.7f).coerceAtLeast(0.005f) }
            ToolBtn("Zoom +") { s.zoom = (s.zoom * 1.4f).coerceAtMost(0.4f) }
        }
    }

    editing?.let { sub ->
        SubtitleDialog(sub, onSave = { s.updateSubtitle(it); editing = null }, onDismiss = { editing = null })
    }
    message?.let {
        AlertDialog(onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            text = { Text(it) })
    }
}

// ---------------------------------------------------------------- Monitor
@Composable
fun Monitor(s: EditorState, engine: PreviewEngine) {
    Box(Modifier.fillMaxWidth().height(200.dp).background(Color.Black)) {
        s.tracks.filter { it.kind == Kind.VIDEO }.forEach { tr ->
            key(tr.id) {
                AndroidView(
                    factory = { c -> TextureView(c).also { engine.attach(tr.id, it) } },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.alpha = engine.alpha[tr.id] ?: 0f }
                )
            }
        }
        engine.activeSubs.forEach { sub ->
            val style = when {
                sub.bold && sub.italic -> Typeface.BOLD_ITALIC
                sub.bold -> Typeface.BOLD
                sub.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Box(
                Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = when (sub.position) {
                    0 -> Alignment.BottomCenter; 1 -> Alignment.Center; else -> Alignment.TopCenter
                }
            ) {
                Text(
                    sub.text, color = Color(sub.color),
                    fontSize = (200f * sub.sizePct / 100f).sp,
                    fontFamily = FontFamily(Typeface.create(sub.font, style)),
                    modifier = Modifier.background(if (sub.background) Color(0x99000000) else Color.Transparent)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Línea de tiempo
@Composable
fun TimelinePanel(s: EditorState, modifier: Modifier, onEdit: (Subtitle) -> Unit) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val density = LocalDensity.current.density
    val total = maxOf(s.totalMs() + 5000, 15_000L)
    val contentW = (total * s.zoom).dp
    val rows = s.tracks.filter { it.kind == Kind.VIDEO }.reversed() + s.tracks.filter { it.kind == Kind.AUDIO }
    val totalH = (20 + 44 * (rows.size + 1)).dp
    var viewW by remember { mutableIntStateOf(0) }

    LaunchedEffect(s.playheadMs) {
        if (s.playing) {
            val x = (s.playheadMs * s.zoom * density).toInt()
            if (x > hScroll.value + viewW - 60 || x < hScroll.value) hScroll.scrollTo(maxOf(0, x - 80))
        }
    }

    Row(modifier.fillMaxWidth().verticalScroll(vScroll)) {
        Column(Modifier.width(44.dp)) {
            Spacer(Modifier.height(20.dp))
            HeaderBox("S1", Color(0xFF3A2F45), false)
            rows.forEach { tr ->
                Box(Modifier.clickable { s.selectedTrackId = tr.id }) {
                    HeaderBox(tr.name, if (tr.id == s.selectedTrackId) Color(0xFF4A4A4A) else Panel, !tr.enabled)
                }
            }
        }
        Box(Modifier.weight(1f).onSizeChanged { viewW = it.width }.horizontalScroll(hScroll)) {
            Column(Modifier.width(contentW)) {
                Ruler(s, total, contentW)
                SubRow(s, onEdit)
                rows.forEach { tr -> TrackRow(s, tr) }
            }
            Box(Modifier.offset(x = (s.playheadMs * s.zoom).dp).width(2.dp).height(totalH).background(Color.Red))
        }
    }
}

@Composable
fun HeaderBox(name: String, bg: Color, dimmed: Boolean) {
    Box(Modifier.height(44.dp).fillMaxWidth().padding(1.dp).background(bg), contentAlignment = Alignment.Center) {
        Text(name, color = if (dimmed) Color.Gray else Color.White, fontSize = 12.sp)
    }
}

@Composable
fun Ruler(s: EditorState, total: Long, width: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current.density
    val step = listOf(1000L, 2000L, 5000L, 10000L, 30000L, 60000L).firstOrNull { it * s.zoom >= 48 } ?: 60000L
    Box(
        Modifier.width(width).height(20.dp).background(Color(0xFF333333))
            .pointerInput(s.zoom) {
                detectTapGestures { s.playheadMs = (it.x / density / s.zoom).toLong().coerceAtLeast(0) }
            }
            .pointerInput(s.zoom) {
                detectDragGestures { ch, _ ->
                    ch.consume()
                    s.playheadMs = (ch.position.x / density / s.zoom).toLong().coerceIn(0, total)
                }
            }
    ) {
        var t = 0L
        while (t <= total) {
            Text("${t / 1000}s", fontSize = 9.sp, color = Color.LightGray, modifier = Modifier.offset(x = (t * s.zoom).dp))
            t += step
        }
    }
}

@Composable
fun SubRow(s: EditorState, onEdit: (Subtitle) -> Unit) {
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF282230))) {
        s.subtitles.forEach { sub ->
            Box(
                Modifier.offset(x = (sub.startMs * s.zoom).dp)
                    .width(((sub.endMs - sub.startMs) * s.zoom).dp).fillMaxHeight().padding(1.dp)
                    .background(SubClip)
                    .then(if (sub.id == s.selectedSubId) Modifier.border(2.dp, Color.White) else Modifier)
                    .clickable { if (s.selectedSubId == sub.id) onEdit(sub) else s.selectSub(sub.id) }
                    .pointerInput(sub.id, s.zoom) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { s.selectSub(sub.id) },
                            onDrag = { ch, d -> ch.consume(); s.dragSub(sub.id, (d.x / density / s.zoom).toLong()) })
                    }
            ) {
                Text(sub.text, fontSize = 10.sp, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
fun TrackRow(s: EditorState, tr: Track) {
    val density = LocalDensity.current.density
    val color = if (tr.kind == Kind.VIDEO) VideoClip else AudioClip
    Box(
        Modifier.fillMaxWidth().height(44.dp)
            .background(if (tr.id == s.selectedTrackId) Color(0xFF303030) else Color(0xFF252525))
            .pointerInput(s.zoom) {
                detectTapGestures { s.selectedTrackId = tr.id; s.playheadMs = (it.x / density / s.zoom).toLong() }
            }
    ) {
        tr.clips.forEach { c ->
            Box(
                Modifier.offset(x = (c.startMs * s.zoom).dp)
                    .width((c.lengthMs * s.zoom).dp).fillMaxHeight().padding(1.dp)
                    .background(if (tr.enabled) color else Color.DarkGray)
                    .then(if (c.id == s.selectedClipId) Modifier.border(2.dp, Color.White) else Modifier)
                    .clickable { s.selectClip(c.id) }
                    .pointerInput(c.id, s.zoom) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { s.selectClip(c.id) },
                            onDrag = { ch, d -> ch.consume(); s.dragClip(c.id, (d.x / density / s.zoom).toLong()) })
                    }
            ) {
                val fx = (if (c.fadeInMs > 0) "◢" else "") + (if (c.fadeOutMs > 0) "◣" else "")
                Text("$fx ${c.name}", fontSize = 10.sp, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
fun ToolBtn(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(text, fontSize = 12.sp, color = Color.White)
    }
}

// ---------------------------------------------------------------- Diálogo de subtítulo
@Composable
fun SubtitleDialog(initial: Subtitle, onSave: (Subtitle) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.text) }
    var start by remember { mutableStateOf("%.1f".format(initial.startMs / 1000.0)) }
    var end by remember { mutableStateOf("%.1f".format(initial.endMs / 1000.0)) }
    var font by remember { mutableStateOf(initial.font) }
    var bold by remember { mutableStateOf(initial.bold) }
    var italic by remember { mutableStateOf(initial.italic) }
    var bg by remember { mutableStateOf(initial.background) }
    var color by remember { mutableStateOf(initial.color) }
    var size by remember { mutableStateOf(initial.sizePct) }
    var pos by remember { mutableStateOf(initial.position) }
    val colors = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFF4FC3F7, 0xFF8BC34A, 0xFFFF5252, 0xFF000000).map { it.toInt() }
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtítulo") },
        confirmButton = {
            TextButton(onClick = {
                val st = ((start.replace(',', '.').toDoubleOrNull() ?: 0.0) * 1000).toLong().coerceAtLeast(0)
                val en = ((end.replace(',', '.').toDoubleOrNull() ?: 3.0) * 1000).toLong()
                onSave(initial.copy(
                    text = text, startMs = st, endMs = maxOf(en, st + 500), font = font, bold = bold,
                    italic = italic, color = color, sizePct = size, position = pos, background = bg))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Texto") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Inicio (s)") },
                        keyboardOptions = num, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("Fin (s)") },
                        keyboardOptions = num, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text("Fuente", fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FONTS.forEach { (id, label) ->
                        FilterChip(selected = font == id, onClick = { font = id }, label = {
                            Text(label, fontFamily = FontFamily(Typeface.create(id, Typeface.NORMAL)))
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Negrita", fontWeight = FontWeight.Bold) })
                    FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("Cursiva", fontStyle = FontStyle.Italic) })
                    FilterChip(selected = bg, onClick = { bg = !bg }, label = { Text("Fondo") })
                }
                Text("Color", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { c ->
                        Box(Modifier.size(32.dp).background(Color(c), CircleShape)
                            .border(if (c == color) 3.dp else 1.dp, if (c == color) Color.Cyan else Color.Gray, CircleShape)
                            .clickable { color = c })
                    }
                }
                Text("Tamaño: $size %", fontSize = 12.sp)
                Slider(size.toFloat(), { size = it.toInt() }, valueRange = 3f..12f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Abajo", "Centro", "Arriba").forEachIndexed { i, l ->
                        FilterChip(selected = pos == i, onClick = { pos = i }, label = { Text(l) })
                    }
                }
            }
        }
    )
}
