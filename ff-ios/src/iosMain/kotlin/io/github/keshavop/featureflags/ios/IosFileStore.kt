package io.github.keshavop.featureflags.ios

import io.github.keshavop.featureflags.client.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL

/**
 * One file per key under `Application Support/FeatureFlags`, excluded from iCloud backup. Writes are atomic (Foundation
 * writes to a temporary file and renames it).
 */
public class IosFileStore(
    private val directory: NSURL = defaultDirectory(),
) : KeyValueStore {
    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            NSString.stringWithContentsOfURL(file(key), NSUTF8StringEncoding, null)
        }

    override suspend fun write(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) {
            NSFileManager.defaultManager.createDirectoryAtURL(
                directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            directory.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
            NSString.create(string = value).writeToURL(file(key), atomically = true, encoding = NSUTF8StringEncoding, error = null)
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { NSFileManager.defaultManager.removeItemAtURL(file(key), error = null) }
    }

    private fun file(key: String): NSURL {
        require(key.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "invalid storage key '$key'" }
        return directory.URLByAppendingPathComponent("$key.json")!!
    }

    public companion object {
        public fun defaultDirectory(): NSURL {
            val base =
                NSFileManager.defaultManager.URLForDirectory(
                    NSApplicationSupportDirectory,
                    NSUserDomainMask,
                    null,
                    create = true,
                    error = null,
                )
                    ?: error("Application Support directory unavailable")
            return base.URLByAppendingPathComponent("FeatureFlags", isDirectory = true)!!
        }
    }
}
