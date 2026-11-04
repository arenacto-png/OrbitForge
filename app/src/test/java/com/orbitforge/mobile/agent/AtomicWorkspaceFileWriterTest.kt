package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicWorkspaceFileWriterTest {
    @Test
    fun atomicallyReplacesExistingUtf8FileAndRemovesTemporaryArtifact() {
        val directory = Files.createTempDirectory("orbitforge-atomic-write").toFile()
        try {
            val target = File(directory, "sample.kt").apply { writeText("old content") }
            val replacement = "Новый текст — OrbitForge\n"

            AtomicWorkspaceFileWriter.write(target, replacement)

            assertEquals(replacement, target.readText(Charsets.UTF_8))
            assertEquals(listOf("sample.kt"), directory.listFiles().orEmpty().map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun createsTargetInExistingNestedParent() {
        val directory = Files.createTempDirectory("orbitforge-atomic-write").toFile()
        try {
            val target = File(directory, "src/main/Example.kt")
            target.parentFile?.mkdirs()

            AtomicWorkspaceFileWriter.write(target, "class Example")

            assertTrue(target.isFile)
            assertEquals("class Example", target.readText(Charsets.UTF_8))
            assertFalse(directory.walkTopDown().any { it.name.endsWith(".tmp") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun refusesToReplaceDirectory() {
        val directory = Files.createTempDirectory("orbitforge-atomic-write").toFile()
        try {
            val target = File(directory, "target-dir").apply { mkdirs() }
            val before = target.listFiles().orEmpty().toList()
            try {
                AtomicWorkspaceFileWriter.write(target, "not a directory")
                throw AssertionError("Expected directory replacement to fail")
            } catch (_: java.io.IOException) {
                // Expected: a file write must not replace a directory.
            }
            assertEquals(before, target.listFiles().orEmpty().toList())
            assertEquals(listOf("target-dir"), directory.listFiles().orEmpty().map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }
}
