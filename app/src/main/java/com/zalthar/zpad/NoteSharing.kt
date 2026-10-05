package com.zalthar.zpad

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ShareKind { Text, File, Image }

/** Render long notes into bounded pages rather than allocating a giant bitmap. */
suspend fun Activity.shareNote(title: String, text: String, kind: ShareKind, font: String, background: Int, foreground: Int) {
    if (kind == ShareKind.Text) {
        // The words themselves, ready to paste into a chat or email.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Untitled" })
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share note as text"))
        return
    }
    val image = kind == ShareKind.Image
    val uris = withContext(Dispatchers.IO) {
        val root = File(cacheDir, "shared-notes").apply { mkdirs() }
        // Unique names keep previously granted attachments intact.
        root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000 }?.forEach { it.deleteRecursively() }
        val directory = File(root, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
        val safeTitle = title.ifBlank { "Untitled" }.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60)
        val files = if (!image) listOf(File(directory, "$safeTitle.txt").apply { writeText(text) }) else {
            val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = foreground
                textSize = 30f
                typeface = when (font) { "Serif" -> Typeface.SERIF; "Sans serif" -> Typeface.SANS_SERIF; else -> Typeface.MONOSPACE }
            }
            val layout = StaticLayout.Builder.obtain(text.ifEmpty { " " }, 0, text.length.coerceAtLeast(1), paint, 984)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(8f, 1f).setIncludePad(false).build()
            val result = mutableListOf<File>()
            var startLine = 0
            while (startLine < layout.lineCount) {
                val top = layout.getLineTop(startLine)
                var endLine = startLine + 1
                while (endLine < layout.lineCount && layout.getLineBottom(endLine) - top <= 1280) endLine++
                val bodyHeight = layout.getLineBottom(endLine - 1) - top
                val bitmap = Bitmap.createBitmap(1080, bodyHeight + 180, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(background)
                    val heading = TextPaint(paint).apply { typeface = Typeface.create(typeface, Typeface.BOLD); textSize = 34f }
                    val headingText = android.text.TextUtils.ellipsize(title.ifBlank { "Untitled" }, heading, 984f, android.text.TextUtils.TruncateAt.END).toString()
                    canvas.drawText(headingText, 48f, 64f, heading)
                    canvas.save()
                    canvas.clipRect(48, 100, 1032, 100 + bodyHeight)
                    canvas.translate(48f, 100f - top)
                    layout.draw(canvas)
                    canvas.restore()
                    paint.textSize = 20f
                    canvas.drawText("Zpad · ${result.size + 1}", 48f, (bodyHeight + 150).toFloat(), paint)
                    paint.textSize = 30f
                    val file = File(directory, "$safeTitle-${result.size + 1}.png")
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    result.add(file)
                } finally { bitmap.recycle() }
                startLine = endLine
            }
            result
        }
        ArrayList(files.map { FileProvider.getUriForFile(this@shareNote, "$packageName.files", it) })
    }
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (image) "image/png" else "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Untitled" })
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        clipData = android.content.ClipData.newUri(contentResolver, "Note", uris.first()).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, if (image) "Share note as image" else "Share note as file"))
}
