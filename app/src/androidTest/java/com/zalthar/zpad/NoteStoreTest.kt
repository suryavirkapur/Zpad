package com.zalthar.zpad

import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteStoreTest {
    @Test fun migrationKeepsBackupAndDoesNotResurrectDeletedNotes() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "migration-${java.util.UUID.randomUUID()}"
        val root = java.io.File(base.cacheDir, namespace).apply { mkdirs() }
        val context = object : android.content.ContextWrapper(base) {
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$namespace-$name", mode)
        }
        val id = System.nanoTime()
        val old = java.io.File(root, "notes/${id}__Migrated.txt").apply { parentFile!!.mkdirs(); writeText("Existing note") }
        val store = NoteStore(context)
        try {
            assertTrue(store.list().any { it.id == id })
            assertEquals("Existing note", store.read(id).content)
            assertEquals("Existing note", old.readText())
            assertEquals(1, NoteStore(context).list().count { it.id == id })
            store.delete(id)
            assertFalse(NoteStore(context).list().any { it.id == id })
        } finally {
            store.delete(id)
            root.deleteRecursively()
            base.deleteSharedPreferences("$namespace-storage")
            base.deleteSharedPreferences("$namespace-pins")
        }
    }

    @Test fun publicTxtRoundTripRenamePinAndDelete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = NoteStore(context)
        val id = store.save(null, "Storage test", "Hello UTF-8\nمرحبا 🌍")
        try {
            assertEquals("Hello UTF-8\nمرحبا 🌍", store.read(id).content)
            store.pin(id, true)
            store.save(id, "Renamed test", "Updated content")
            assertEquals("Renamed test", store.read(id).title)
            assertEquals("Updated content", store.read(id).content)
            assertTrue(store.read(id).pinned)
            assertEquals(1, store.list().count { it.id == id })
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("${id}__%"), null,
            )!!.use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).endsWith(".txt"))
                assertEquals("Download/Zpad/", cursor.getString(1))
            }
        } finally {
            store.delete(id)
        }
        assertFalse(store.list().any { it.id == id })
    }
}
