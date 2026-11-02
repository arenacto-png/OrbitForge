package com.orbitforge.mobile.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceSyncPlannerTest {
    private val head = "1111111111111111111111111111111111111111"
    private val tree = "2222222222222222222222222222222222222222"

    @Test
    fun gitBlobHashMatchesKnownEmptyBlob() {
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", GitBlobHash.sha1(ByteArray(0)))
    }

    @Test
    fun classifiesAddedModifiedAndUnchangedFiles() {
        val added = "new file\n"
        val modified = "new value\n"
        val unchanged = "same\n"
        val oldModified = "old value\n"
        val plan = WorkspaceSyncPlanner.create(
            repositoryFullName = "owner/repo",
            branch = "main",
            expectedHeadSha = head,
            baseTreeSha = tree,
            candidates = listOf(
                WorkspaceSyncCandidate("new.txt", added, GitBlobHash.sha1(added)),
                WorkspaceSyncCandidate("src/App.kt", modified, GitBlobHash.sha1(modified)),
                WorkspaceSyncCandidate("same.txt", unchanged, GitBlobHash.sha1(unchanged))
            ),
            remoteBlobShas = mapOf(
                "src/App.kt" to GitBlobHash.sha1(oldModified),
                "same.txt" to GitBlobHash.sha1(unchanged)
            ),
            remoteModes = mapOf("src/App.kt" to "100755"),
            remoteContents = mapOf("src/App.kt" to oldModified),
            unsupportedRemotePaths = emptySet(),
            skippedFiles = 2
        )

        assertEquals(1, plan.addedFiles)
        assertEquals(1, plan.modifiedFiles)
        assertEquals(1, plan.unchangedFiles)
        assertEquals(2, plan.skippedFiles)
        assertEquals("100755", plan.changes.first { it.path == "src/App.kt" }.mode)
        assertTrue(plan.preview.contains("new.txt"))
        assertTrue(plan.preview.contains("- old value"))
        assertTrue(plan.preview.contains("+ new value"))
        assertFalse(plan.changes.any { it.path == "same.txt" })
    }

    @Test
    fun rejectsDuplicateLocalPaths() {
        val content = "same"
        val candidate = WorkspaceSyncCandidate("file.txt", content, GitBlobHash.sha1(content))
        try {
            WorkspaceSyncPlanner.create(
                "owner/repo", "main", head, tree,
                listOf(candidate, candidate), emptyMap(), emptyMap(), emptyMap(), emptySet(), 0
            )
            fail("Duplicate paths must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        for (path in listOf("../outside.txt", "/absolute.txt", "a/../b.txt", "a\\b.txt")) {
            try {
                WorkspaceSyncPlanner.validateRelativePath(path)
                fail("Unsafe path must be rejected: " + path)
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun requiresRemoteContentForModifiedFile() {
        val content = "new"
        val oldSha = GitBlobHash.sha1("old")
        try {
            WorkspaceSyncPlanner.create(
                "owner/repo", "main", head, tree,
                listOf(WorkspaceSyncCandidate("file.txt", content, GitBlobHash.sha1(content))),
                mapOf("file.txt" to oldSha),
                mapOf("file.txt" to "100644"),
                emptyMap(),
                emptySet(),
                0
            )
            fail("A modified file without its remote diff must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun refusesUnsupportedRemoteObjects() {
        val content = "local"
        try {
            WorkspaceSyncPlanner.create(
                "owner/repo", "main", head, tree,
                listOf(WorkspaceSyncCandidate("link.txt", content, GitBlobHash.sha1(content))),
                emptyMap(), emptyMap(), emptyMap(), setOf("link.txt"), 0
            )
            fail("A remote symlink or submodule path must not be overwritten")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun rejectsDiffThatCannotBeShownCompletely() {
        val content = (1..60).joinToString("\n") { "line-" + it }
        try {
            WorkspaceSyncPlanner.create(
                "owner/repo", "main", head, tree,
                listOf(WorkspaceSyncCandidate("large.txt", content, GitBlobHash.sha1(content))),
                emptyMap(), emptyMap(), emptyMap(), emptySet(), 0
            )
            fail("Truncated previews must not be approved")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun noChangesProduceNoCommitEntries() {
        val content = "same"
        val plan = WorkspaceSyncPlanner.create(
            "owner/repo", "main", head, tree,
            listOf(WorkspaceSyncCandidate("same.txt", content, GitBlobHash.sha1(content))),
            mapOf("same.txt" to GitBlobHash.sha1(content)),
            mapOf("same.txt" to "100644"),
            emptyMap(),
            emptySet(),
            0
        )
        assertTrue(plan.changes.isEmpty())
        assertEquals("Нет изменений для отправки.", plan.preview)
    }
}
