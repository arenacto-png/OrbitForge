package com.orbitforge.mobile.agent

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces one app-private workspace file through a same-directory temporary file.
 * If atomic rename is unavailable, the operation fails without falling back to truncating writes.
 */
internal object AtomicWorkspaceFileWriter {
    fun write(target: File, content: String) {
        val parent = target.parentFile ?: throw IOException("У файла отсутствует родительская папка.")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Не удалось создать папку для записи.")
        }
        if (target.isDirectory) throw IOException("Нельзя заменить папку содержимым файла.")

        val temp = Files.createTempFile(parent.toPath(), ".orbitforge-write-", ".tmp")
        try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            FileOutputStream(temp.toFile()).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temp,
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
