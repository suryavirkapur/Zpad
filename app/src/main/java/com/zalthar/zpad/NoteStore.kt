package com.zalthar.zpad

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

data class Note(val id: Long, val title: String, val content: String, val updated: Long, val pinned: Boolean = false)

/** UTF-8 TXT documents in the public Downloads/Zpad folder. */
class NoteStore(context: Context) {
    private val resolver = context.contentResolver
    private val pins = context.getSharedPreferences("pins", Context.MODE_PRIVATE)
    private val storage = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
    private val legacy = File(context.filesDir, "notes")
    private val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val path = "${Environment.DIRECTORY_DOWNLOADS}/Zpad/"
    private val noteOrder = compareByDescending<Note> { it.pinned }.thenByDescending { it.updated }

    private data class Document(val uri: Uri, val name: String, val modified: Long, val mediaId: Long) {
        val id: Long get() = name.substringBefore("__").toLong()
        val title: String get() = URLDecoder.decode(name.removeSuffix(".txt").substringAfter("__", "Untitled"), "UTF-8")
    }

    private fun documents(): List<Document> {
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED)
        return resolver.query(collection, columns,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=0", arrayOf(path), null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (name.endsWith(".txt") && name.substringBefore("__").toLongOrNull() != null) {
                        val mediaId = cursor.getLong(0)
                        add(Document(ContentUris.withAppendedId(collection, mediaId), name, cursor.getLong(2) * 1000, mediaId))
                    }
                }
            }
        } ?: error("Could not access Downloads/Zpad")
    }

    private fun currentDocuments() = documents().groupBy { it.id }.values.map { group -> group.maxBy { it.mediaId } }
    private fun readText(document: Document) = resolver.openInputStream(document.uri)?.bufferedReader()?.use { it.readText() }
        ?: error("Could not read note")
    private fun note(document: Document, text: String = "") = Note(document.id, document.title, text, document.modified, pins.getBoolean(document.id.toString(), false))

    // Copy first. Keep the original private files as a backup; never delete before migration succeeds.
    private fun migrate() {
        if (storage.getBoolean("publicNotesMigrated", false)) return
        val existing = currentDocuments().map { it.id }.toSet()
        legacy.listFiles()?.filter { it.extension == "txt" }?.forEach { file ->
            val id = file.name.substringBefore("__").toLongOrNull() ?: return@forEach
            if (id !in existing) write(file.name, file.readText())
        }
        check(storage.edit().putBoolean("publicNotesMigrated", true).commit())
    }

    /** Publish a completed replacement before removing the old document. */
    private fun write(name: String, text: String): Uri {
        val uri = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "zpad-${java.util.UUID.randomUUID()}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Could not create note")
        try {
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("Could not write note")
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) == 1)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Synchronized fun list(): List<Note> {
        migrate()
        return currentDocuments().map { note(it) }.sortedWith(noteOrder)
    }

    fun search(notes: List<Note>, query: String, checkCancelled: () -> Unit): List<Note> {
        val terms = searchTerms(query)
        if (terms.isEmpty()) return notes
        val documents = currentDocuments().associateBy { it.id }
        return notes.filter { note ->
            checkCancelled()
            val remaining = terms.filterNot { note.title.contains(it, true) }
            remaining.isEmpty() || documents[note.id]?.let { document ->
                resolver.openInputStream(document.uri)?.bufferedReader()?.use { matchesNote(it, remaining, checkCancelled) }
            } == true
        }.sortedWith(compareByDescending<Note> { it.pinned }
            .thenByDescending { note -> terms.all { note.title.contains(it, true) } }.thenByDescending { it.updated })
    }

    @Synchronized fun pin(id: Long, pinned: Boolean) { check(pins.edit().putBoolean(id.toString(), pinned).commit()) }

    @Synchronized fun read(id: Long): Note {
        migrate()
        val document = currentDocuments().first { it.id == id }
        return note(document, readText(document))
    }

    @Synchronized fun save(id: Long?, title: String, content: String): Long {
        migrate()
        val existing = documents()
        var noteId = id ?: System.currentTimeMillis()
        if (id == null) { val ids = existing.map { it.id }.toSet(); while (noteId in ids) noteId++ }
        var filenameTitle = title.take(60)
        var encodedTitle = URLEncoder.encode(filenameTitle, "UTF-8")
        while (encodedTitle.length > 180) {
            filenameTitle = filenameTitle.dropLast(1)
            encodedTitle = URLEncoder.encode(filenameTitle, "UTF-8")
        }
        val old = existing.filter { it.id == noteId }
        // MediaStore disambiguates duplicate filenames, so stage under a unique name.
        val uri = write("${noteId}__${encodedTitle}.txt", content)
        old.forEach { check(resolver.delete(it.uri, null, null) == 1) }
        // Restore the intended name after duplicate-name disambiguation.
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "${noteId}__${encodedTitle}.txt") }, null, null) == 1)
        return noteId
    }

    @Synchronized fun delete(id: Long) {
        migrate()
        documents().filter { it.id == id }.forEach { check(resolver.delete(it.uri, null, null) == 1) }
        pins.edit().remove(id.toString()).apply()
    }
}
