package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceToolsSensitivePathTest {
    @Test
    fun agentReadRejectsSymlinkAliasToSensitiveFile() = withWorkspace { root, tools ->
        val secretFile = File(root, ".env").apply { writeText("PRIVATE_CONFIG=development") }
        if (!createAlias(root, "config-alias.txt", secretFile)) return@withWorkspace

        expectRejected { tools.readFileForAgent("config-alias.txt") }
    }

    @Test
    fun agentWriteRejectsSymlinkAliasAndPreservesSensitiveTarget() = withWorkspace { root, tools ->
        val secretFile = File(root, ".env").apply { writeText("PRIVATE_CONFIG=development") }
        if (!createAlias(root, "config-alias.txt", secretFile)) return@withWorkspace

        expectRejected { tools.writeFile("config-alias.txt", "replacement") }
        assertEquals("PRIVATE_CONFIG=development", secretFile.readText())
    }

    @Test
    fun batchValidatesSensitiveAliasesBeforeChangingAnyFile() = withWorkspace { root, tools ->
        val secretFile = File(root, ".env").apply { writeText("PRIVATE_CONFIG=development") }
        val notes = File(root, "notes.txt").apply { writeText("before") }
        if (!createAlias(root, "config-alias.txt", secretFile)) return@withWorkspace

        expectRejected {
            tools.writeFilesAtomically(
                listOf(
                    AgentWriteChange("notes.txt", "after"),
                    AgentWriteChange("config-alias.txt", "replacement")
                )
            )
        }

        assertEquals("before", notes.readText())
        assertEquals("PRIVATE_CONFIG=development", secretFile.readText())
    }

    @Test
    fun searchDoesNotReturnSensitiveFileThroughSymlinkAlias() = withWorkspace { root, tools ->
        val secretFile = File(root, ".env").apply { writeText("PRIVATE_CONFIG=development") }
        if (!createAlias(root, "config-alias.txt", secretFile)) return@withWorkspace

        val result = tools.searchText("PRIVATE_CONFIG")
        assertTrue(result.contains("Совпадений не найдено"))
        assertFalse(result.contains("PRIVATE_CONFIG=development"))
    }

    @Test
    fun previewRejectsSensitiveSymlinkAlias() = withWorkspace { root, tools ->
        val secretFile = File(root, ".env").apply { writeText("PRIVATE_CONFIG=development") }
        if (!createAlias(root, "config-alias.txt", secretFile)) return@withWorkspace

        expectRejected { tools.previewAgentWrite("config-alias.txt", "replacement") }
    }

    private fun withWorkspace(block: (File, WorkspaceTools) -> Unit) {
        val temp = Files.createTempDirectory("orbitforge-sensitive-test").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            block(root, WorkspaceTools(root))
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun createAlias(root: File, alias: String, target: File): Boolean = try {
        Files.createSymbolicLink(File(root, alias).toPath(), target.toPath())
        true
    } catch (_: Exception) {
        // Symlink creation can be unavailable in restricted environments.
        false
    }

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            fail("Sensitive symlink aliases must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
