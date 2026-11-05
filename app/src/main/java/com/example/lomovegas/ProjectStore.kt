package com.example.lomovegas

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

data class ProjectInfo(val name: String, val modified: Long)

/** Guarda y carga proyectos como archivos .json en el almacenamiento interno de la app. */
object ProjectStore {
    const val AUTOSAVE = "_autoguardado"

    private fun dir(ctx: Context): File = File(ctx.filesDir, "projects").also { it.mkdirs() }
    private fun file(ctx: Context, name: String) = File(dir(ctx), "$name.json")

    fun safeName(n: String): String = n.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(60)

    fun exists(ctx: Context, name: String): Boolean {
        val n = safeName(name)
        return n.isNotEmpty() && file(ctx, n).exists()
    }

    fun save(ctx: Context, name: String, json: JSONObject) {
        val f = file(ctx, name)
        val tmp = File(f.parentFile, "$name.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            if (!tmp.renameTo(f)) throw java.io.IOException("No se pudo escribir el archivo")
        }
    }

    fun load(ctx: Context, name: String): JSONObject? = try {
        JSONObject(file(ctx, name).readText())
    } catch (_: Exception) { null }

    fun delete(ctx: Context, name: String) { file(ctx, name).delete() }

    fun list(ctx: Context): List<ProjectInfo> =
        (dir(ctx).listFiles { f -> f.extension == "json" } ?: emptyArray())
            .map { ProjectInfo(it.nameWithoutExtension, it.lastModified()) }
            .sortedByDescending { it.modified }

    /** Copia de seguridad automática de la sesión actual (si hay algo que guardar). */
    fun autosave(ctx: Context, s: EditorState) {
        if (s.tracks.none { it.clips.isNotEmpty() } && s.subtitles.isEmpty()) return
        try { save(ctx, AUTOSAVE, s.toJson(true)) } catch (_: Exception) {}
    }

    /** Comprueba que el archivo multimedia sigue accesible. */
    fun canRead(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) { false }
}
