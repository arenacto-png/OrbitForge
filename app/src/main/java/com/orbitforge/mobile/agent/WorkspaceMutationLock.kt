package com.orbitforge.mobile.agent

/**
 * Serializes app-controlled mutations of the private workspace.
 *
 * The lock is process-local and reentrant so a multi-file patch can hold it across
 * validation, writes, and rollback while the ordinary single-file writer safely
 * acquires the same lock again.
 */
internal object WorkspaceMutationLock {
    private val monitor = Any()

    fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
