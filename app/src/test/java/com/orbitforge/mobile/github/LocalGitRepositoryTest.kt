package com.orbitforge.mobile.github

import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalGitRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val localGit = LocalGitRepository()

    @Test
    fun initializesWorkspaceAtExactRootAndShowsCleanStatus() {
        val root = temporaryFolder.newFolder("workspace")

        assertEquals("main", localGit.initialize(root))
        assertTrue(localGit.isInitialized(root))
        val status = localGit.status(root)
        assertEquals("main", status.branch)
        assertTrue(status.isClean)
    }

    @Test
    fun doesNotDiscoverAParentRepository() {
        val parent = temporaryFolder.newFolder("parent-repository")
        localGit.initialize(parent)
        val child = File(parent, "child").apply { mkdirs() }

        assertFalse(localGit.isInitialized(child))
        assertThrows(IllegalArgumentException::class.java) { localGit.status(child) }
    }

    @Test
    fun rejectsWorkspaceSymlinksBeforeDiffOrCommit() {
        val root = initializedWorkspace()
        val outside = temporaryFolder.newFile("outside.txt").apply { writeText("outside\n", Charsets.UTF_8) }
        java.nio.file.Files.createSymbolicLink(File(root, "link.txt").toPath(), outside.toPath())

        assertThrows(IllegalArgumentException::class.java) { localGit.prepareCommit(root) }
    }

    @Test
    fun previewsUntrackedFileAndCreatesReviewedLocalCommit() {
        val root = initializedWorkspace()
        File(root, "README.md").writeText("# OrbitForge\n", Charsets.UTF_8)

        val plan = localGit.prepareCommit(root)
        assertEquals(listOf("README.md"), plan.paths)
        assertTrue(plan.diff.contains("README.md"))
        assertTrue(plan.diff.contains("+# OrbitForge"))

        val result = localGit.commitReviewed(
            root,
            plan,
            "Add README",
            "Test Author",
            "test@example.com"
        )

        assertTrue(result.commitSha.matches(Regex("[0-9a-f]{40}")))
        assertEquals("main", result.branch)
        assertEquals(1, result.changedFiles)
        assertTrue(localGit.status(root).isClean)

        Git.open(root).use { git ->
            val commit = git.log().call().iterator().next()
            assertEquals("Add README", commit.fullMessage)
            assertEquals("Test Author", commit.authorIdent.name)
            assertEquals("test@example.com", commit.authorIdent.emailAddress)
        }
    }

    @Test
    fun statusSeparatesIndexAndWorkingTreeChanges() {
        val root = initializedWorkspace()
        val file = File(root, "note.txt")
        file.writeText("staged text\n", Charsets.UTF_8)
        Git.open(root).use { git -> git.add().addFilepattern("note.txt").call() }
        file.writeText("working tree text\n", Charsets.UTF_8)

        val entry = localGit.status(root).entries.single()
        assertEquals("note.txt", entry.path)
        assertEquals("A", entry.indexStatus)
        assertEquals("M", entry.workTreeStatus)

        val plan = localGit.prepareCommit(root)
        assertTrue(plan.diff.contains("+staged text"))
        assertTrue(plan.diff.contains("+working tree text"))
    }

    @Test
    fun refusesCommitWhenFilesChangeAfterPreview() {
        val root = initializedWorkspace()
        val file = File(root, "change.txt")
        file.writeText("before\n", Charsets.UTF_8)
        val plan = localGit.prepareCommit(root)
        file.writeText("after\n", Charsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            localGit.commitReviewed(root, plan, "Commit", "Test Author", "test@example.com")
        }
        Git.open(root).use { git -> assertTrue(git.repository.resolve("HEAD") == null) }
    }

    @Test
    fun refusesSensitivePathsAndSecretLikeContents() {
        val root = initializedWorkspace()
        File(root, ".env").writeText("APP_MODE=test\n", Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) { localGit.prepareCommit(root) }

        File(root, ".env").delete()
        File(root, "config.txt").writeText(
            "api_key = \"123456789012345678901234\"\n",
            Charsets.UTF_8
        )
        assertThrows(IllegalArgumentException::class.java) { localGit.prepareCommit(root) }
    }

    @Test
    fun refusesBinaryAndOversizedChangedFiles() {
        val root = initializedWorkspace()
        File(root, "binary.dat").writeBytes(byteArrayOf(0, 1, 2, 3))
        assertThrows(IllegalArgumentException::class.java) { localGit.prepareCommit(root) }

        File(root, "binary.dat").delete()
        File(root, "large.txt").writeBytes(ByteArray(LocalGitRepository.MAX_FILE_BYTES + 1) { 'a'.code.toByte() })
        assertThrows(IllegalArgumentException::class.java) { localGit.prepareCommit(root) }
    }

    @Test
    fun rejectsEmptyCommitAndInvalidAuthorMetadata() {
        val root = initializedWorkspace()
        val emptyPlan = localGit.prepareCommit(root)
        assertTrue(emptyPlan.paths.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            localGit.commitReviewed(root, emptyPlan, "Empty", "Test Author", "test@example.com")
        }

        File(root, "file.txt").writeText("content\n", Charsets.UTF_8)
        val plan = localGit.prepareCommit(root)
        assertThrows(IllegalArgumentException::class.java) {
            localGit.commitReviewed(root, plan, "Commit", "Test Author", "not-an-email")
        }
    }

    @Test
    fun stagesTrackedDeletionAndLeavesWorkspaceStateUntouchedByCommit() {
        val root = initializedWorkspace()
        val file = File(root, "remove.txt")
        file.writeText("to be removed\n", Charsets.UTF_8)
        val firstPlan = localGit.prepareCommit(root)
        localGit.commitReviewed(root, firstPlan, "Add file", "Test Author", "test@example.com")
        assertTrue(file.exists())

        file.delete()
        val deletionPlan = localGit.prepareCommit(root)
        assertEquals(listOf("remove.txt"), deletionPlan.paths)
        assertTrue(deletionPlan.diff.contains("remove.txt"))
        localGit.commitReviewed(root, deletionPlan, "Remove file", "Test Author", "test@example.com")

        assertFalse(file.exists())
        assertTrue(localGit.status(root).isClean)
    }

    @Test
    fun refusesDeletionOfSensitiveTrackedPath() {
        val root = initializedWorkspace()
        seedCommit(root, ".env", "APP_MODE=test\n")
        assertTrue(File(root, ".env").delete())

        assertThrows(IllegalArgumentException::class.java) { localGit.prepareCommit(root) }
    }

    @Test
    fun redactsSecretLikeContentFromTrackedBaseDiff() {
        val root = initializedWorkspace()
        val secret = "123456789012345678901234"
        seedCommit(root, "config.txt", "api_key = \"$secret\"\n")
        File(root, "config.txt").writeText("api_key = \"removed\"\n", Charsets.UTF_8)

        val plan = localGit.prepareCommit(root)

        assertFalse(plan.diff.contains(secret))
        assertTrue(plan.diff.contains("скрыт"))
    }

    private fun seedCommit(root: File, path: String, content: String) {
        File(root, path).writeText(content, Charsets.UTF_8)
        Git.open(root).use { git ->
            git.add().addFilepattern(path).call()
            git.commit()
                .setMessage("Seed fixture")
                .setAuthor("Test Author", "test@example.com")
                .setCommitter("Test Author", "test@example.com")
                .call()
        }
    }

    private fun initializedWorkspace(): File =
        temporaryFolder.newFolder("repo-" + System.nanoTime()).apply { localGit.initialize(this) }
}
