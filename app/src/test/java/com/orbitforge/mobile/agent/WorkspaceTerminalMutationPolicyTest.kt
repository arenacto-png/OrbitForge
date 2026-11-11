package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTerminalMutationPolicyTest {
    @Test
    fun rejectsCreatingSensitiveDirectoriesBeforeMutation() = withWorkspace { root ->
        val target = resolve(root, ".ssh")

        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalMutationPolicy.createDirectory(root, ".ssh", target)
        }
        assertFalse(File(root, ".ssh").exists())
    }

    @Test
    fun rejectsCreatingSensitiveFilesBeforeMutation() = withWorkspace { root ->
        val target = resolve(root, ".env")

        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalMutationPolicy.createEmptyFile(root, ".env", target)
        }
        assertFalse(File(root, ".env").exists())
    }

    @Test
    fun rejectsTouchThroughAliasToSensitiveFileAndPreservesTarget() = withWorkspace { root ->
        val secret = File(root, ".env").apply { writeText("keep-this-value") }
        val alias = File(root, "ordinary-name.txt")
        val linked = runCatching {
            Files.createSymbolicLink(alias.toPath(), secret.toPath())
        }.isSuccess
        if (!linked) return@withWorkspace

        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalMutationPolicy.createEmptyFile(
                root,
                "ordinary-name.txt",
                resolve(root, "ordinary-name.txt")
            )
        }
        assertEquals("keep-this-value", secret.readText())
    }

    @Test
    fun allowsOrdinaryDirectoryAndEmptyFileCreation() = withWorkspace { root ->
        val directory = WorkspaceTerminalMutationPolicy.createDirectory(
            root,
            "src/new",
            resolve(root, "src/new")
        )
        val file = WorkspaceTerminalMutationPolicy.createEmptyFile(
            root,
            "src/new/empty.txt",
            resolve(root, "src/new/empty.txt")
        )

        assertTrue(directory.isDirectory)
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
    }

    @Test
    fun rejectsTargetsOutsideWorkspace() = withWorkspace { root ->
        val outside = File(root.parentFile, "outside-dir")

        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalMutationPolicy.createDirectory(root, "alias", outside)
        }
        assertFalse(outside.exists())
    }

    private fun resolve(root: File, path: String): File = WorkspacePathPolicy(root).resolve(path)

    private fun withWorkspace(block: (File) -> Unit) {
        val parent = Files.createTempDirectory("orbitforge-terminal-mutation").toFile()
        try {
            val root = File(parent, "workspace").apply { mkdirs() }
            block(root)
        } finally {
            parent.deleteRecursively()
        }
    }
}
