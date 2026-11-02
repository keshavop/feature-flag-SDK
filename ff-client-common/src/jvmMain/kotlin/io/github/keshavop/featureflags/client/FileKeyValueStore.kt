package io.github.keshavop.featureflags.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One file per key in [directory] (on Android: `noBackupFilesDir`, so cached flags are not part of cloud backups).
 * Writes go to a temp file that is atomically renamed, so a crash never leaves a half-written snapshot.
 */
public class FileKeyValueStore(
    private val directory: File,
) : KeyValueStore {
    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            file(key).takeIf { it.isFile }?.readText()
        }

    override suspend fun write(
        key: String,
        value: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val temp = File(directory, "${file(key).name}.tmp")
            temp.writeText(value)
            Files.move(temp.toPath(), file(key).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { file(key).delete() }
    }

    private fun file(key: String): File {
        require(key.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "invalid storage key '$key'" }
        return File(directory, "$key.json")
    }
}
