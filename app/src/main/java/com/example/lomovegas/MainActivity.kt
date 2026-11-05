package com.example.lomovegas

import android.content.Intent
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private val Bg = Color(0xFF1E1E1E)
private val Panel = Color(0xFF2B2B2B)
private val VideoClip = Color(0xFF2E6F95)
private val AudioClip = Color(0xFF3C8D5A)
private val SubClip = Color(0xFF8E5BB5)
private val Purple = Color(0xFFAB47BC)

class MainActivity : ComponentActivity() {
    private val state = EditorState()
    override fun onStop() {
        super.onStop()
        ProjectStore.autosave(this, state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { EditorScreen(state) } }
    }
}

fun fmt(ms: Long) = "%d:%02d.%d".format(ms / 60000, ms / 1000 % 60, ms % 1000 / 100)
fun fadeLabel(ms: Long) = if (ms == 0L) "corte" else "${ms / 1000.0} s"

fun clipTag(c: Clip): String {
    val sb = StringBuilder()
    if (c.fadeInMs > 0) sb.append("◢")
    if (c.fadeOutMs > 0) sb.append("◣")
    if (c.speed != 1f) sb.append(" x${c.speed}")
    if (c.rotation != 0) sb.append(" ↻${c.rotation}")
    if (c.zoom == 1) sb.append(" 🔍+") else if (c.zoom == 2) sb.append(" 🔍−")
    if (c.fitMode != 0) sb.append(" ▭")
    if (c.vfx != 0) sb.append(" 🎬")
    if (c.afx != 0) sb.append(" 🎚")
    return sb.toString()
}

// ---------------------------------------------------------------- Botones
data class MenuEntry(val label: String, val action: () -> Unit)

/** Botón de icono simple. */
@Composable
fun IconBtn(icon: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp, 40.dp).alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 18.sp, color = Color.White)
    }
}

/** Botón que abre un desplegable al pulsar o al mantener pulsado. Una entrada "-" es un separador. */
@Composable
fun MenuBtn(icon: String, items: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp, 40.dp).combinedClickable(onClick = { open = true }, onLongClick = { open = true }),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 18.sp, color = Color.White)
            Text("▾", fontSize = 9.sp, color = Color.LightGray, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { e ->
                if (e.label == "-") HorizontalDivider()
                else if (e.label.startsWith("#"))
                    Text(e.label.drop(1), fontSize = 11.sp, color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                else DropdownMenuItem(text = { Text(e.label, fontSize = 14.sp) }, onClick = { open = false; e.action() })
            }
        }
    }
}

/** Botón que alterna un modo al tocar y abre un desplegable al mantener pulsado. */
@Composable
fun ToggleBtn(icon: String, active: Boolean, onClick: () -> Unit, items: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp, 40.dp)
                .background(if (active) Purple else Color.Transparent, RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onClick, onLongClick = { open = true }),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 18.sp, color = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { e ->
                DropdownMenuItem(text = { Text(e.label, fontSize = 14.sp) }, onClick = { open = false; e.action() })
            }
        }
    }
}

// ---------------------------------------------------------------- Pantalla principal
@Composable
fun EditorScreen(s: EditorState) {
    val ctx = LocalContext.current
    val engine = remember { PreviewEngine(ctx, s) }
    DisposableEffect(Unit) { onDispose { engine.release() } }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Subtitle?>(null) }

    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1800); showSplash = false }
    val splashAlpha by animateFloatAsState(if (showSplash) 1f else 0f, tween(600), label = "splash")

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

    // Subtitulado automático
    val scope = rememberCoroutineScope()
    var autoProgress by remember { mutableStateOf<Float?>(null) }
    val cancelFlag = remember { AtomicBoolean(false) }

    // Mantener la pantalla encendida mientras se exporta o se analiza el audio
    val view = LocalView.current
    val busy = progress != null || autoProgress != null
    SideEffect { view.keepScreenOn = busy }

    // Proyectos: guardar / abrir
    var dlg by remember { mutableStateOf<String?>(null) }
    var nameField by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf(emptyList<ProjectInfo>()) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var savedJson by remember { mutableStateOf(s.toJson(false).toString()) }
    var dirty by remember { mutableStateOf(false) }

    fun doSave(name: String) {
        val n = ProjectStore.safeName(name)
        if (n.isEmpty() || n == ProjectStore.AUTOSAVE) { message = "Escribe un nombre válido."; return }
        try {
            ProjectStore.save(ctx, n, s.toJson(true))
            s.projectName = n
            savedJson = s.toJson(false).toString()
            dirty = false
            message = "Proyecto guardado: $n"
        } catch (e: Exception) {
            message = "No se pudo guardar: ${e.message}"
        }
    }

    fun doOpen(name: String) {
        val o = ProjectStore.load(ctx, name)
        if (o == null) { message = "No se pudo abrir el proyecto."; return }
        ProjectStore.autosave(ctx, s)   // copia de seguridad de lo que había abierto
        val missing = try {
            s.loadJson(o) { uri -> ProjectStore.canRead(ctx, uri) }
        } catch (e: Exception) {
            message = "El proyecto está dañado: ${e.message}"
            return
        }
        s.projectName = if (name == ProjectStore.AUTOSAVE) null else name
        savedJson = s.toJson(false).toString()
        dirty = false
        message = if (missing.isEmpty()) "Proyecto abierto."
        else "Faltan ${missing.size} archivo(s) que ya no están en el móvil y se han quitado de la línea de tiempo: ${missing.joinToString(", ")}."
    }

    fun doNew() {
        ProjectStore.autosave(ctx, s)
        s.newProject()
        savedJson = s.toJson(false).toString()
        dirty = false
    }

    // Historial: cada pausa de 0,4 s tras un cambio queda como un paso de deshacer
    LaunchedEffect(s.tracks, s.subtitles) {
        delay(400)
        s.commitIfChanged()
    }

    // Autoguardado 3 s después del último cambio y marca de "cambios sin guardar"
    LaunchedEffect(s.tracks, s.subtitles) {
        delay(3000)
        dirty = s.toJson(false).toString() != savedJson
        ProjectStore.autosave(ctx, s)
    }

    fun autoSub(cut: Boolean) {
        val clip = s.selectedClip()
        if (clip == null || clip.isImage || !clip.hasAudio) {
            message = "Selecciona un clip de vídeo o de audio con sonido."
            return
        }
        s.playing = false
        cancelFlag.set(false)
        autoProgress = 0f
        scope.launch(Dispatchers.Default) {
            val res = Transcriber.run(ctx, clip, { p -> autoProgress = p }, { cancelFlag.get() })
            withContext(Dispatchers.Main) {
                autoProgress = null
                message = when {
                    cancelFlag.get() -> "Cancelado."
                    res.error != null -> res.error
                    else -> s.applyAutoSubtitles(clip.id, res.words, cut)
                }
            }
        }
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
    LaunchedEffect(s.playheadMs, s.tracks, s.subtitles) {
        if (!s.playing) engine.sync(s.playheadMs, false)
    }

    val c = s.selectedClip()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding()) {
            // Barra superior
            Row(Modifier.fillMaxWidth().background(Panel), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lomo Vegas" + (s.projectName?.let { " · $it" } ?: "") + (if (dirty) " •" else ""),
                    color = Purple, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp, end = 6.dp))
                IconBtn("↩", enabled = s.undoCount > 0) { s.undo() }
                IconBtn("↪", enabled = s.redoCount > 0) { s.redo() }
                MenuBtn("📁", listOf(
                    MenuEntry("Nuevo proyecto") { dlg = "new" },
                    MenuEntry("Guardar") {
                        val n = s.projectName
                        if (n != null) doSave(n) else { nameField = ""; dlg = "saveas" }
                    },
                    MenuEntry("Guardar como…") { nameField = s.projectName ?: ""; dlg = "saveas" },
                    MenuEntry("Abrir proyecto…") { projects = ProjectStore.list(ctx); dlg = "open" },
                    MenuEntry("-") {},
                    MenuEntry("📤 Exportar 720p (HD)") { export(720) },
                    MenuEntry("📤 Exportar 1080p (Full HD)") { export(1080) }
                ))
                IconBtn("📂") { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }
            }
            progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }

            Monitor(s, engine)

            // Transporte
            Row(Modifier.fillMaxWidth().background(Panel), verticalAlignment = Alignment.CenterVertically) {
                IconBtn("⏮") { s.playing = false; s.playheadMs = 0 }
                IconBtn(if (s.playing) "⏸" else "▶") { s.playing = !s.playing }
                Text("${fmt(s.playheadMs)} / ${fmt(s.totalMs())}", color = Color.White, fontSize = 12.sp)
                if (s.cutMode) Text("   ✂ Toca un clip para cortarlo", color = Purple, fontSize = 12.sp)
            }

            TimelinePanel(s, Modifier.weight(1f)) { editing = it }

            // Barra de herramientas
            Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState())) {
                ToggleBtn("✂", s.cutMode, { s.cutMode = !s.cutMode }, listOf(
                    MenuEntry(if (s.cutMode) "Salir del modo cortar" else "Modo cortar (tocar para cortar)") { s.cutMode = !s.cutMode },
                    MenuEntry("Dividir en el cursor") { s.split() }
                ))
                IconBtn("🗑") { s.delete() }
                MenuBtn("↔", listOf(
                    MenuEntry("Inicio −0,5 s") { s.trimStart(-500) },
                    MenuEntry("Inicio +0,5 s") { s.trimStart(500) },
                    MenuEntry("Fin −0,5 s") { s.trimEnd(-500) },
                    MenuEntry("Fin +0,5 s") { s.trimEnd(500) }
                ))
                MenuBtn("🌗", listOf(
                    MenuEntry("Fundido de entrada: ${fadeLabel(c?.fadeInMs ?: 0)}") { s.cycleFade(true) },
                    MenuEntry("Fundido de salida: ${fadeLabel(c?.fadeOutMs ?: 0)}") { s.cycleFade(false) }
                ))
                MenuBtn("✨", listOf(
                    MenuEntry("⏩ Acelerar x2") { s.setSpeed(2f) },
                    MenuEntry("⏩ Acelerar x4") { s.setSpeed(4f) },
                    MenuEntry("🐢 Ralentizar x0,5") { s.setSpeed(0.5f) },
                    MenuEntry("🐢 Ralentizar x0,25") { s.setSpeed(0.25f) },
                    MenuEntry("▶ Velocidad normal") { s.setSpeed(1f) },
                    MenuEntry("-") {},
                    MenuEntry("↻ Girar 90°") { s.rotate90() },
                    MenuEntry("-") {},
                    MenuEntry("▭ Vertical a horizontal (barras)") { s.setFit(1) },
                    MenuEntry("▭ Vertical a horizontal (rellenar)") { s.setFit(2) },
                    MenuEntry("Quitar conversión") { s.setFit(0) },
                    MenuEntry("-") {},
                    MenuEntry("🔍 Zoom in") { s.setZoom(1) },
                    MenuEntry("🔍 Zoom out") { s.setZoom(2) },
                    MenuEntry("Quitar zoom") { s.setZoom(0) }
                ))
                MenuBtn("🎚", buildList {
                    add(MenuEntry("#🎬 Efectos de vídeo") {})
                    VIDEO_PRESETS.forEach { (id, name) ->
                        add(MenuEntry((if (c?.vfx == id) "✓ " else "") + name) { s.setVfx(id) })
                    }
                    add(MenuEntry("-") {})
                    add(MenuEntry("#🔊 Efectos de audio") {})
                    AUDIO_PRESETS.forEach { (id, name) ->
                        add(MenuEntry((if (c?.afx == id) "✓ " else "") + name) { s.setAfx(id) })
                    }
                })
                MenuBtn("💬", listOf(
                    MenuEntry("🎙 Subtitular automáticamente") { autoSub(false) },
                    MenuEntry("🎙✂ Subtitular y recortar silencios") { autoSub(true) },
                    MenuEntry("Borrar subtítulos automáticos") { s.deleteAutoSubs() },
                    MenuEntry("-") {},
                    MenuEntry("Añadir subtítulo") { editing = s.addSubtitle() },
                    MenuEntry("Editar subtítulo seleccionado") { s.selectedSub()?.let { editing = it } }
                ))
                MenuBtn("☰", listOf(
                    MenuEntry("+ Pista de vídeo") { s.addTrack(Kind.VIDEO) },
                    MenuEntry("+ Pista de audio") { s.addTrack(Kind.AUDIO) },
                    MenuEntry("− Quitar pista seleccionada") { s.removeTrack() }
                ))
                MenuBtn("🔎", listOf(
                    MenuEntry("Acercar (también: pellizca con dos dedos)") { s.zoomBy(1.5f) },
                    MenuEntry("Alejar") { s.zoomBy(1f / 1.5f) },
                    MenuEntry("Ver todo el proyecto") { s.fitTick++ }
                ))
            }
        }

        // Introducción
        if (splashAlpha > 0f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = splashAlpha }.background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                Text("Lomo Vegas", color = Purple, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    autoProgress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Subtitulado automático") },
            text = {
                Column {
                    Text("Analizando el audio… ${(p * 100).toInt()} %\nPuede tardar unos minutos.", fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { cancelFlag.set(true) }) { Text("Cancelar") } }
        )
    }
    editing?.let { sub ->
        SubtitleDialog(sub, onSave = { s.updateSubtitle(it); editing = null }, onDismiss = { editing = null })
    }
    if (dlg == "saveas") {
        val exists = ProjectStore.exists(ctx, nameField)
        AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("Guardar proyecto") },
            text = {
                Column {
                    OutlinedTextField(nameField, { nameField = it }, label = { Text("Nombre") }, singleLine = true)
                    if (exists) Text("Ya existe un proyecto con ese nombre; se sobrescribirá.",
                        fontSize = 12.sp, color = Color(0xFFFFB74D), modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ProjectStore.safeName(nameField).isNotEmpty()) { doSave(nameField); dlg = null }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { dlg = null }) { Text("Cancelar") } }
        )
    }
    if (dlg == "open") {
        val fmtDate = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("Abrir proyecto") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (projects.isEmpty()) Text("Aún no hay proyectos guardados.")
                    projects.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { dlg = null; doOpen(p.name) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(if (p.name == ProjectStore.AUTOSAVE) "⟳ Autoguardado (última sesión)" else p.name, fontSize = 15.sp)
                                Text(fmtDate.format(Date(p.modified)), fontSize = 11.sp, color = Color.Gray)
                            }
                            if (p.name != ProjectStore.AUTOSAVE) SmallBtn("🗑") { deleteTarget = p.name }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dlg = null }) { Text("Cerrar") } }
        )
    }
    if (dlg == "new") {
        AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("Nuevo proyecto") },
            text = { Text("Se cerrará el proyecto actual. Se guardará una copia de seguridad en «Autoguardado», pero conviene guardarlo antes con un nombre. ¿Continuar?") },
            confirmButton = { TextButton(onClick = { dlg = null; doNew() }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { dlg = null }) { Text("Cancelar") } }
        )
    }
    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Borrar proyecto") },
            text = { Text("¿Borrar «$name»? No se puede deshacer. Tus vídeos y audios originales no se borran.") },
            confirmButton = {
                TextButton(onClick = {
                    ProjectStore.delete(ctx, name)
                    projects = ProjectStore.list(ctx)
                    if (s.projectName == name) s.projectName = null
                    deleteTarget = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
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
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // Monitor 16:9 adaptado al ancho de la pantalla (vertical) o al 45 % del alto (horizontal)
    val cfg = LocalConfiguration.current
    val monitorH = if (cfg.screenWidthDp < cfg.screenHeightDp)
        (cfg.screenWidthDp * 9f / 16f).dp.coerceAtMost(260.dp)
    else (cfg.screenHeightDp * 0.45f).dp

    Box(Modifier.fillMaxWidth().height(monitorH).background(Color.Black).onSizeChanged { size = it }) {
        s.tracks.filter { it.kind == Kind.VIDEO }.forEach { tr ->
            key(tr.id) {
                val pv = engine.view[tr.id]
                val clip = pv?.clip
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also { engine.attach(tr.id, it) } },
                    modifier = Modifier.fillMaxSize(),
                    update = { tv ->
                        val vs = engine.vsize[tr.id]
                        if (clip != null && pv != null && !clip.isImage) {
                            tv.alpha = pv.alpha
                            if (tv.tag != clip.vfx) {
                                tv.tag = clip.vfx
                                val cm = previewColorMatrix(clip.vfx)
                                if (cm != null) {
                                    val paint = Paint()
                                    paint.colorFilter = ColorMatrixColorFilter(cm)
                                    tv.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                                } else tv.setLayerType(View.LAYER_TYPE_NONE, null)
                            }
                            if (vs != null && tv.width > 0 && tv.height > 0) {
                                tv.setTransform(
                                    fitMatrixTexture(
                                        tv.width.toFloat(), tv.height.toFloat(), vs.first, vs.second,
                                        clip.rotation, clip.fitMode == 2, zoomScale(clip, pv.localMs)
                                    )
                                )
                            }
                        } else tv.alpha = 0f
                    }
                )
                if (clip != null && pv != null && clip.isImage) {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.MATRIX } },
                        modifier = Modifier.fillMaxSize(),
                        update = { iv ->
                            val bmp = engine.bitmapFor(clip.uri)
                            if (bmp != null) {
                                val tagKey = Pair(clip.uri, clip.vfx)
                                if (iv.tag != tagKey) {
                                    iv.setImageBitmap(bmp)
                                    iv.colorFilter = previewColorMatrix(clip.vfx)?.let { ColorMatrixColorFilter(it) }
                                    iv.tag = tagKey
                                }
                                iv.alpha = pv.alpha
                                if (iv.width > 0 && iv.height > 0) {
                                    iv.imageMatrix = fitMatrixImage(
                                        iv.width.toFloat(), iv.height.toFloat(),
                                        bmp.width.toFloat(), bmp.height.toFloat(),
                                        clip.rotation, clip.fitMode == 2, zoomScale(clip, pv.localMs)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        val unseen = engine.view.values.any { pv ->
            val k = pv.clip
            k != null && ((k.vfx != 0 && !vfxPreviewable(k.vfx)) || !afxPreviewable(k.afx))
        }
        if (unseen) {
            Text("Algunos efectos solo se aprecian al exportar", fontSize = 10.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).background(Color(0x99000000)).padding(4.dp))
        }

        if (size.width > 0 && size.height > 0) {
            engine.activeSubs.forEach { sub ->
                val bmp = remember(sub, size) { SubtitleRenderer.render(sub, size.width, size.height).asImageBitmap() }
                Box(
                    Modifier.fillMaxSize().padding(4.dp),
                    contentAlignment = when (sub.position) {
                        0 -> Alignment.BottomCenter
                        1 -> Alignment.Center
                        else -> Alignment.TopCenter
                    }
                ) {
                    Image(
                        bitmap = bmp, contentDescription = null,
                        modifier = Modifier.size(with(density) { bmp.width.toDp() }, with(density) { bmp.height.toDp() })
                    )
                }
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
    val hasAuto = s.subtitles.any { it.track == 1 }
    val totalH = (20 + 44 * (rows.size + 1 + (if (hasAuto) 1 else 0))).dp
    var viewW by remember { mutableIntStateOf(0) }
    var pendingScroll by remember { mutableStateOf<Int?>(null) }

    // Zoom con dos dedos: mantiene bajo los dedos el punto de la línea de tiempo que se está mirando
    val onPinch: (Float, Float) -> Unit = { f, cx ->
        val old = s.zoom
        s.zoomBy(f)
        if (old > 0f) {
            val baseScroll = pendingScroll ?: hScroll.value
            pendingScroll = ((baseScroll + cx) * (s.zoom / old) - cx).toInt().coerceAtLeast(0)
        }
    }
    val pinch by rememberUpdatedState(onPinch)

    LaunchedEffect(s.zoom) {
        val ps = pendingScroll
        if (ps != null) {
            withFrameNanos { }
            hScroll.scrollTo(ps)
            pendingScroll = null
        }
    }
    LaunchedEffect(s.fitTick) {
        if (s.fitTick > 0 && viewW > 0) {
            val len = maxOf(s.totalMs(), 1000L) + 1000L
            s.zoom = ((viewW / density) / len).coerceIn(0.005f, 1f)
            hScroll.scrollTo(0)
        }
    }

    LaunchedEffect(s.playheadMs) {
        if (s.playing) {
            val x = (s.playheadMs * s.zoom * density).toInt()
            if (x > hScroll.value + viewW - 60 || x < hScroll.value) hScroll.scrollTo(maxOf(0, x - 80))
        }
    }

    Row(modifier.fillMaxWidth().verticalScroll(vScroll)) {
        Column(Modifier.width(76.dp)) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.height(44.dp).fillMaxWidth().padding(1.dp).background(Color(0xFF3A2F45)), contentAlignment = Alignment.CenterStart) {
                Text("S1", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            }
            if (hasAuto) {
                Box(Modifier.height(44.dp).fillMaxWidth().padding(1.dp).background(Color(0xFF2F4545)), contentAlignment = Alignment.CenterStart) {
                    Text("S2 auto", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            rows.forEach { tr -> TrackHeader(s, tr) }
        }
        Box(
            Modifier.weight(1f).onSizeChanged { viewW = it.width }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val a = pressed[0]
                                val b = pressed[1]
                                val dist = (a.position - b.position).getDistance()
                                val prev = (a.previousPosition - b.previousPosition).getDistance()
                                if (prev > 0f && dist > 0f) {
                                    pinch(dist / prev, (a.position.x + b.position.x) / 2f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .horizontalScroll(hScroll)
        ) {
            Column(Modifier.width(contentW)) {
                Ruler(s, total, contentW, hScroll.value, viewW)
                SubRow(s, 0, onEdit)
                if (hasAuto) SubRow(s, 1, onEdit)
                rows.forEach { tr -> TrackRow(s, tr) }
            }
            Box(Modifier.offset(x = (s.playheadMs * s.zoom).dp).width(2.dp).height(totalH).background(Color.Red))
        }
    }
}

@Composable
fun SmallBtn(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(26.dp, 30.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 14.sp, color = Color.White)
    }
}

/** Cabecera de pista: nombre, ocultar/ver (solo vídeo) y silenciar/activar sonido. */
@Composable
fun TrackHeader(s: EditorState, tr: Track) {
    Row(
        Modifier.height(44.dp).fillMaxWidth().padding(1.dp)
            .background(if (tr.id == s.selectedTrackId) Color(0xFF4A4A4A) else Panel)
            .clickable { s.selectedTrackId = tr.id },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tr.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(22.dp).padding(start = 4.dp))
        if (tr.kind == Kind.VIDEO) SmallBtn(if (tr.hidden) "🚫" else "👁") { s.toggleHidden(tr.id) }
        SmallBtn(if (tr.muted) "🔇" else "🔊") { s.toggleMuted(tr.id) }
    }
}

@Composable
fun Ruler(s: EditorState, total: Long, width: Dp, scrollPx: Int, viewPx: Int) {
    val density = LocalDensity.current.density
    val step = listOf(100L, 200L, 500L, 1000L, 2000L, 5000L, 10000L, 30000L, 60000L, 300000L)
        .firstOrNull { it * s.zoom >= 56 } ?: 300000L
    val fromMs = maxOf(0L, ((scrollPx / density / s.zoom).toLong() / step) * step - step)
    val toMs = minOf(total, ((scrollPx + viewPx) / density / s.zoom).toLong() + step)
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
        var t = fromMs
        while (t <= toMs) {
            val label = if (step < 1000L) "%.1fs".format(t / 1000.0) else "${t / 1000}s"
            Text(label, fontSize = 9.sp, color = Color.LightGray, modifier = Modifier.offset(x = (t * s.zoom).dp + 2.dp))
            Box(Modifier.offset(x = (t * s.zoom).dp, y = 13.dp).width(1.dp).height(7.dp).background(Color.Gray))
            t += step
        }
    }
}

@Composable
fun SubRow(s: EditorState, track: Int, onEdit: (Subtitle) -> Unit) {
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF282230))) {
        s.subtitles.filter { it.track == track }.forEach { sub ->
            Box(
                Modifier.offset(x = (sub.startMs * s.zoom).dp)
                    .width(((sub.endMs - sub.startMs) * s.zoom).dp).fillMaxHeight().padding(1.dp)
                    .background(if (track == 1) Color(0xFF3F7F7F) else SubClip)
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
    val base = if (tr.kind == Kind.VIDEO) VideoClip else AudioClip
    val dim = (tr.kind == Kind.VIDEO && tr.hidden) || tr.muted
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
                    .background(if (dim) Color(0xFF555555) else base)
                    .then(if (c.id == s.selectedClipId) Modifier.border(2.dp, Color.White) else Modifier)
                    .pointerInput(c.id, s.zoom) {
                        detectTapGestures { off ->
                            if (s.cutMode) s.splitAt(c.id, c.startMs + (off.x / density / s.zoom).toLong())
                            else s.selectClip(c.id)
                        }
                    }
                    .pointerInput(c.id, s.zoom) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { s.selectClip(c.id) },
                            onDrag = { ch, d -> ch.consume(); s.dragClip(c.id, (d.x / density / s.zoom).toLong()) })
                    }
            ) {
                Text("${clipTag(c)} ${c.name}", fontSize = 10.sp, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Diálogo de subtítulo
@Composable
fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { c ->
            Box(
                Modifier.size(30.dp).background(Color(c), CircleShape)
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Color.Cyan else Color.Gray, CircleShape)
                    .clickable { onPick(c) }
            )
        }
    }
}

@Composable
fun SubtitleDialog(initial: Subtitle, onSave: (Subtitle) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.text) }
    var start by remember { mutableStateOf("%.1f".format(initial.startMs / 1000.0)) }
    var end by remember { mutableStateOf("%.1f".format(initial.endMs / 1000.0)) }
    var font by remember { mutableStateOf(initial.font) }
    var bold by remember { mutableStateOf(initial.bold) }
    var italic by remember { mutableStateOf(initial.italic) }
    var color by remember { mutableStateOf(initial.color) }
    var size by remember { mutableStateOf(initial.sizePct) }
    var pos by remember { mutableStateOf(initial.position) }
    var bgOn by remember { mutableStateOf(initial.bgOn) }
    var bgColor by remember { mutableStateOf(initial.bgColor) }
    var bgOpacity by remember { mutableStateOf(initial.bgOpacity) }
    var outlineOn by remember { mutableStateOf(initial.outlineOn) }
    var outlineColor by remember { mutableStateOf(initial.outlineColor) }
    var shadowOn by remember { mutableStateOf(initial.shadowOn) }
    var shadowColor by remember { mutableStateOf(initial.shadowColor) }
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
                    italic = italic, color = color, sizePct = size, position = pos,
                    bgOn = bgOn, bgColor = bgColor, bgOpacity = bgOpacity,
                    outlineOn = outlineOn, outlineColor = outlineColor,
                    shadowOn = shadowOn, shadowColor = shadowColor))
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
                        FilterChip(selected = font == id, onClick = { font = id }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Negrita", fontWeight = FontWeight.Bold) })
                    FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("Cursiva", fontStyle = FontStyle.Italic) })
                }
                Text("Color del texto", fontSize = 12.sp)
                ColorRow(color) { color = it }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = bgOn, onClick = { bgOn = !bgOn }, label = { Text("Fondo") })
                    FilterChip(selected = outlineOn, onClick = { outlineOn = !outlineOn }, label = { Text("Bordeado") })
                    FilterChip(selected = shadowOn, onClick = { shadowOn = !shadowOn }, label = { Text("Sombreado") })
                }
                if (bgOn) {
                    Text("Color del fondo", fontSize = 12.sp)
                    ColorRow(bgColor) { bgColor = it }
                    Text("Opacidad del fondo: $bgOpacity %", fontSize = 12.sp)
                    Slider(bgOpacity.toFloat(), { bgOpacity = it.toInt() }, valueRange = 0f..100f)
                }
                if (outlineOn) {
                    Text("Color del bordeado", fontSize = 12.sp)
                    ColorRow(outlineColor) { outlineColor = it }
                }
                if (shadowOn) {
                    Text("Color del sombreado", fontSize = 12.sp)
                    ColorRow(shadowColor) { shadowColor = it }
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
