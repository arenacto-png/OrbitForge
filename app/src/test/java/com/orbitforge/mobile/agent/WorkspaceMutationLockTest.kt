package com.orbitforge.mobile.agent

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMutationLockTest {
    @Test
    fun lockIsReentrant() {
        val result = WorkspaceMutationLock.withLock {
            WorkspaceMutationLock.withLock { "nested" }
        }
        assertEquals("nested", result)
    }

    @Test
    fun atomicWriterWaitsUntilMultiFileMutationReleasesTheLock() {
        val directory = Files.createTempDirectory("orbitforge-mutation-lock").toFile()
        val target = File(directory, "sample.txt").apply { writeText("before") }
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        var worker: Thread? = null
        try {
            WorkspaceMutationLock.withLock {
                worker = Thread {
                    try {
                        AtomicWorkspaceFileWriter.write(target, "after")
                    } catch (error: Throwable) {
                        failure.set(error)
                    } finally {
                        completed.countDown()
                    }
                }.apply { start() }
                awaitBlocked(worker!!)
                assertEquals("before", target.readText(Charsets.UTF_8))
                assertFalse(completed.count == 0L)
            }
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertEquals("after", target.readText(Charsets.UTF_8))
        } finally {
            worker?.join(1_000)
            directory.deleteRecursively()
        }
    }

    @Test
    fun terminalFileCreationWaitsUntilMultiFileMutationReleasesTheLock() {
        val directory = Files.createTempDirectory("orbitforge-terminal-lock").toFile()
        val target = File(directory, "created.txt")
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        var worker: Thread? = null
        try {
            WorkspaceMutationLock.withLock {
                worker = Thread {
                    try {
                        WorkspaceTerminalMutationPolicy.createEmptyFile(directory, "created.txt", target)
                    } catch (error: Throwable) {
                        failure.set(error)
                    } finally {
                        completed.countDown()
                    }
                }.apply { start() }
                awaitBlocked(worker!!)
                assertFalse(target.exists())
                assertFalse(completed.count == 0L)
            }
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertTrue(target.isFile)
        } finally {
            worker?.join(1_000)
            directory.deleteRecursively()
        }
    }

    private fun awaitBlocked(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (thread.state != Thread.State.BLOCKED && thread.isAlive && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertEquals(Thread.State.BLOCKED, thread.state)
    }
}
