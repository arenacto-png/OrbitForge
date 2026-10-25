package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WorkspacePathPolicyTest {
    @Test
    fun allowsWorkspaceRelativePath() {
        val temp = Files.createTempDirectory("orbitforge-path-test").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            val policy = WorkspacePathPolicy(root)
            assertEquals(File(root, "src/Main.kt").canonicalFile, policy.resolve("src/Main.kt"))
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun rejectsParentTraversal() {
        val temp = Files.createTempDirectory("orbitforge-path-test").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            val policy = WorkspacePathPolicy(root)
            try {
                policy.resolve("../outside.txt")
                fail("Parent traversal must be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun rejectsAbsolutePaths() {
        val temp = Files.createTempDirectory("orbitforge-path-test").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            val policy = WorkspacePathPolicy(root)
            try {
                policy.resolve(File(temp, "outside.txt").absolutePath)
                fail("Absolute paths must be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        } finally {
            temp.deleteRecursively()
        }
    }
    @Test
    fun rejectsChildPathThroughSymlinkedDirectory() {
        val temp = Files.createTempDirectory("orbitforge-path-test").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            val outside = File(temp, "outside").apply { mkdirs() }
            val link = File(root, "external")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (_: Exception) {
                // Symlinks may be unavailable in a restricted test environment.
                return
            }

            val policy = WorkspacePathPolicy(root)
            try {
                policy.resolve("external/new-file.txt")
                fail("A not-yet-created child under an escaping symlink must be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun allowsSymlinkThatResolvesInsideWorkspace() {
        val temp = Files.createTempDirectory("orbitforge-path-test").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            val internal = File(root, "real-dir").apply { mkdirs() }
            val link = File(root, "internal-link")
            try {
                Files.createSymbolicLink(link.toPath(), internal.toPath())
            } catch (_: Exception) {
                // Symlinks may be unavailable in a restricted test environment.
                return
            }

            val policy = WorkspacePathPolicy(root)
            assertEquals(File(internal, "new-file.txt").canonicalFile, policy.resolve("internal-link/new-file.txt"))
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkEscape() {
        val temp = Files.createTempDirectory("orbitforge-path-test").toFile()
        try {
            val root = File(temp, "workspace").apply { mkdirs() }
            val outside = File(temp, "outside.txt").apply { writeText("private") }
            val link = File(root, "escape.txt")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (_: Exception) {
                // Symlinks may be unavailable in a restricted test environment.
                return
            }
            val policy = WorkspacePathPolicy(root)
            try {
                policy.resolve("escape.txt")
                fail("Symlink escapes must be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        } finally {
            temp.deleteRecursively()
        }
    }

}
