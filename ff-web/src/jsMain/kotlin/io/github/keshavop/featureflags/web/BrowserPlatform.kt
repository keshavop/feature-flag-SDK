package io.github.keshavop.featureflags.web

import io.github.keshavop.featureflags.client.AppLifecycle
import io.github.keshavop.featureflags.client.Connectivity
import io.github.keshavop.featureflags.client.InMemoryKeyValueStore
import io.github.keshavop.featureflags.client.KeyValueStore
import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `localStorage`-backed store; falls back to memory where storage is unavailable (Node, some private modes) or full.
 * Snapshots are small, so the synchronous API is fine.
 */
internal class LocalStorageStore(
    private val storage: dynamic = findLocalStorage(),
) : KeyValueStore {
    private val fallback = InMemoryKeyValueStore()

    override suspend fun read(key: String): String? =
        if (storage == null) fallback.read(key) else (runCatching { storage.getItem(key) as String? }.getOrNull())

    override suspend fun write(
        key: String,
        value: String,
    ) {
        if (storage == null || runCatching { storage.setItem(key, value) }.isFailure) fallback.write(key, value)
    }

    override suspend fun delete(key: String) {
        if (storage != null) runCatching { storage.removeItem(key) }
        fallback.delete(key)
    }

    private companion object {
        fun findLocalStorage(): dynamic = runCatching { js("typeof localStorage !== 'undefined' ? localStorage : null") }.getOrNull()
    }
}

/** Foreground = page visible (`visibilitychange`); always foreground outside browsers. */
internal class BrowserLifecycle : AppLifecycle {
    private val document: dynamic = js("typeof document !== 'undefined' ? document : null")
    private val state = MutableStateFlow(document == null || document.visibilityState != "hidden")
    override val isForeground: StateFlow<Boolean> = state.asStateFlow()

    init {
        document?.addEventListener("visibilitychange", { _: dynamic -> state.value = document.visibilityState != "hidden" })
    }
}

/** `navigator.onLine` plus `online`/`offline` events; always online outside browsers. */
internal class BrowserConnectivity : Connectivity {
    private val window: dynamic = js("typeof window !== 'undefined' ? window : null")
    private val state = MutableStateFlow(window?.navigator?.onLine != false)
    override val isOnline: StateFlow<Boolean> = state.asStateFlow()

    init {
        window?.addEventListener("online", { _: dynamic -> state.value = true })
        window?.addEventListener("offline", { _: dynamic -> state.value = false })
    }
}

internal class ConsoleLogger(
    private val debugEnabled: Boolean,
) : FeatureFlagsLogger {
    override fun debug(message: String) {
        if (debugEnabled) console.log("[feature-flags] $message")
    }

    override fun info(message: String) = console.info("[feature-flags] $message")

    override fun warn(
        message: String,
        error: Throwable?,
    ) = console.warn("[feature-flags] $message", error?.message ?: "")

    override fun error(
        message: String,
        error: Throwable?,
    ) = console.error("[feature-flags] $message", error?.message ?: "")
}
