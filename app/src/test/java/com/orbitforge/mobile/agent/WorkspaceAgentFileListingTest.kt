package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceAgentFileListingTest {
    @Test
    fun omitsSensitiveNamesAndCanonicalAliases() {
        val root = Files.createTempDirectory("orbitforge-agent-list").toFile()
        val outsideDirectory = Files.createTempDirectory("orbitforge-agent-outside").toFile()
        val outside = File(outsideDirectory, "outside.txt")
        try {
            File(root, "README.md").writeText("safe")
            File(root, ".env").writeText("secret")
            File(root, "local.properties").writeText("secret")
            File(root, ".ssh").mkdirs()
            File(root, ".ssh/id_rsa").writeText("secret")
            outside.writeText("outside")

            val sensitiveAliasCreated = runCatching {
                Files.createSymbolicLink(File(root, "safe-name.txt").toPath(), File(root, ".env").toPath())
            }.isSuccess
            val outsideAliasCreated = runCatching {
                Files.createSymbolicLink(File(root, "outside-name.txt").toPath(), outside.toPath())
            }.isSuccess

            val paths = WorkspaceAgentFileListing.list(root, root).map { it.path }.toSet()

            assertTrue(paths.contains("README.md"))
            assertFalse(paths.contains(".env"))
            assertFalse(paths.contains("local.properties"))
            assertFalse(paths.contains(".ssh"))
            assertFalse(paths.contains(".ssh/id_rsa"))
            if (sensitiveAliasCreated) assertFalse(paths.contains("safe-name.txt"))
            if (outsideAliasCreated) assertFalse(paths.contains("outside-name.txt"))
        } finally {
            root.deleteRecursively()
            outsideDirectory.deleteRecursively()
        }
    }

    @Test
    fun refusesToListSensitiveDirectory() {
        val root = Files.createTempDirectory("orbitforge-agent-list").toFile()
        try {
            val ssh = File(root, ".ssh").apply { mkdirs() }
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceAgentFileListing.list(root, ssh)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun listingHonorsTheEntryLimit() {
        val root = Files.createTempDirectory("orbitforge-agent-list").toFile()
        try {
            repeat(5) { File(root, "file-$it.txt").writeText("safe") }
            assertEquals(2, WorkspaceAgentFileListing.list(root, root, maxEntries = 2).size)
        } finally {
            root.deleteRecursively()
        }
    }
}
