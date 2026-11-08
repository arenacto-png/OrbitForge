package com.orbitforge.mobile.agent

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceExportTransactionTest {
    @Test
    fun successfulReplacementDeletesBackupAfterPromotingCompleteCopy() {
        val directory = FakeDirectory()
        directory.put("notes.txt", "old")

        val result = WorkspaceExportTransaction.write(directory, "notes.txt", "text/plain") { output ->
            output.write("new content".toByteArray(Charsets.UTF_8))
            11L
        }

        assertTrue(result.copied)
        assertTrue(result.replacedExisting)
        assertEquals(11L, result.bytes)
        assertEquals("new content", directory.text("notes.txt"))
        assertEquals(setOf("notes.txt"), directory.entries.keys)
        assertNull(result.warning)
    }

    @Test
    fun failedStagingLeavesExistingDestinationUntouched() {
        val directory = FakeDirectory()
        directory.put("notes.txt", "original")

        val result = WorkspaceExportTransaction.write(directory, "notes.txt", "text/plain") { output ->
            output.write("partial".toByteArray(Charsets.UTF_8))
            throw IOException("simulated write failure")
        }

        assertFalse(result.copied)
        assertEquals("original", directory.text("notes.txt"))
        assertEquals(setOf("notes.txt"), directory.entries.keys)
        assertNotNull(result.warning)
    }

    @Test
    fun failedPromotionRestoresPreviousDestination() {
        val directory = FakeDirectory()
        directory.put("notes.txt", "original")
        directory.renameAllowed = { from, to ->
            !(from.startsWith(".orbitforge-export-") && to == "notes.txt")
        }

        val result = WorkspaceExportTransaction.write(directory, "notes.txt", "text/plain") { output ->
            output.write("new".toByteArray(Charsets.UTF_8))
            3L
        }

        assertFalse(result.copied)
        assertEquals("original", directory.text("notes.txt"))
        assertEquals(setOf("notes.txt"), directory.entries.keys)
        assertTrue(result.warning.orEmpty().contains("restored"))
    }

    @Test
    fun failedPromotionAndRestoreRetainRecoverableBackup() {
        val directory = FakeDirectory()
        directory.put("notes.txt", "original")
        directory.renameAllowed = { from, to ->
            !(to == "notes.txt" && (from.startsWith(".orbitforge-export-") || from.startsWith(".orbitforge-backup-")))
        }

        val result = WorkspaceExportTransaction.write(directory, "notes.txt", "text/plain") { output ->
            output.write("new".toByteArray(Charsets.UTF_8))
            3L
        }

        assertFalse(result.copied)
        assertEquals("original", directory.entries.values.single { it.name.startsWith(".orbitforge-backup-") }.text())
        assertTrue(result.warning.orEmpty().contains(".orbitforge-backup-"))
    }

    @Test
    fun newFileIsOnlyPublishedAfterStagingCompletes() {
        val directory = FakeDirectory()

        val result = WorkspaceExportTransaction.write(directory, "fresh.txt", "text/plain") { output ->
            output.write("fresh".toByteArray(Charsets.UTF_8))
            5L
        }

        assertTrue(result.copied)
        assertFalse(result.replacedExisting)
        assertEquals("fresh", directory.text("fresh.txt"))
        assertEquals(setOf("fresh.txt"), directory.entries.keys)
    }

    @Test
    fun existingDirectoryIsNotReplaced() {
        val directory = FakeDirectory()
        directory.putDirectory("notes.txt")

        val result = WorkspaceExportTransaction.write(directory, "notes.txt", "text/plain") { output ->
            output.write("not allowed".toByteArray(Charsets.UTF_8))
            11L
        }

        assertFalse(result.copied)
        assertEquals(setOf("notes.txt"), directory.entries.keys)
    }

    private class FakeDirectory : WorkspaceExportDirectory {
        val entries = linkedMapOf<String, FakeDocument>()
        var renameAllowed: (String, String) -> Boolean = { _, _ -> true }

        override fun findFile(name: String): WorkspaceExportDocument? = entries[name]

        override fun createFile(mimeType: String, name: String): WorkspaceExportDocument? {
            if (entries.containsKey(name)) return null
            return FakeDocument(this, name).also { entries[name] = it }
        }

        fun put(name: String, text: String) {
            val document = FakeDocument(this, name, text.toByteArray(Charsets.UTF_8))
            entries[name] = document
        }

        fun putDirectory(name: String) {
            val document = FakeDocument(this, name, isDirectory = true)
            entries[name] = document
        }

        fun text(name: String): String = entries.getValue(name).text()

        private fun remove(document: FakeDocument): Boolean {
            if (entries[document.name] !== document) return false
            entries.remove(document.name)
            return true
        }

        private fun rename(document: FakeDocument, newName: String): Boolean {
            if (!renameAllowed(document.name, newName) || entries.containsKey(newName)) return false
            if (!remove(document)) return false
            document.name = newName
            entries[newName] = document
            return true
        }

        inner class FakeDocument(
            private val parent: FakeDirectory,
            var name: String,
            var bytes: ByteArray = ByteArray(0),
            override val isDirectory: Boolean = false
        ) : WorkspaceExportDocument {
            override val isFile: Boolean get() = !isDirectory

            fun text(): String = String(bytes, Charsets.UTF_8)

            override fun delete(): Boolean = parent.remove(this)

            override fun renameTo(displayName: String): Boolean = parent.rename(this, displayName)

            override fun openOutputStream(): OutputStream = object : ByteArrayOutputStream() {
                override fun close() {
                    bytes = toByteArray()
                    super.close()
                }
            }
        }
    }
}
