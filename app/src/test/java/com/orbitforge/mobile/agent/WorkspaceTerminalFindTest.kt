package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTerminalFindTest {
    @Test
    fun traversalOmitsSensitivePathsAndSymlinkAliases() {
        val root = Files.createTempDirectory("orbitforge-find").toFile()
        try {
            File(root, "README.md").writeText("safe")
            File(root, ".env").writeText("secret")
            File(root, ".ssh").mkdirs()
            File(root, ".ssh/id_rsa").writeText("secret")
            val aliasCreated = runCatching {
                Files.createSymbolicLink(File(root, "alias.txt").toPath(), File(root, ".env").toPath())
            }.isSuccess

            val output = WorkspaceTerminalFind.list(root, root)

            assertTrue(output.contains("."))
            assertTrue(output.contains("README.md"))
            assertTrue(!output.contains(".env"))
            assertTrue(!output.contains(".ssh"))
            if (aliasCreated) assertTrue(!output.contains("alias.txt"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun traversalRejectsSensitiveStartingDirectory() {
        val root = Files.createTempDirectory("orbitforge-find").toFile()
        try {
            val ssh = File(root, ".ssh").apply { mkdirs() }
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceTerminalFind.list(root, ssh)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun traversalBoundsResultCountAndOutput() {
        val root = Files.createTempDirectory("orbitforge-find").toFile()
        try {
            repeat(8) {
                File(root, "long-file-name-$it-xxxxxxxxxxxxxxxxxxxx.txt").writeText("safe")
            }
            val result = WorkspaceTerminalFind.list(
                rootDirectory = root,
                startDirectory = root,
                maxResults = 3,
                maxOutputChars = 60
            )

            assertTrue(result.length <= 60)
            assertTrue(result.contains("сокращён"))
        } finally {
            root.deleteRecursively()
        }
    }
}
