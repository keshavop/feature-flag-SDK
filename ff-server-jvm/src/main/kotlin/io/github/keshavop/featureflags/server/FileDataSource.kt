package io.github.keshavop.featureflags.server

import io.github.keshavop.featureflags.core.model.SnapshotCodec
import io.github.keshavop.featureflags.sync.DataSource
import io.github.keshavop.featureflags.sync.DataSourceContext
import io.github.keshavop.featureflags.sync.DataSourceFactory
import io.github.keshavop.featureflags.sync.DataSourceStatus
import io.github.keshavop.featureflags.sync.ErrorInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Loads flags from a snapshot JSON file (the server snapshot format), for local development or air-gapped deployments.
 * With [reloadInterval] the file is re-read whenever its modification time changes.
 */
public class FileDataSource private constructor(
    private val path: Path,
    private val reloadInterval: Duration?,
    private val context: DataSourceContext,
) : DataSource {
    private val state = MutableStateFlow(DataSourceStatus(DataSourceStatus.State.INITIALIZING, context.clock.nowMillis()))
    private var job: Job? = null
    private var lastModified = 0L

    override val status: StateFlow<DataSourceStatus> = state.asStateFlow()

    override suspend fun start(): Boolean {
        val loaded = load()
        if (reloadInterval != null && job == null) {
            job =
                context.scope.launch {
                    while (isActive) {
                        delay(reloadInterval.toMillis())
                        if (Files.getLastModifiedTime(path).toMillis() != lastModified) load()
                    }
                }
        }
        return loaded
    }

    override fun stop() {
        job?.cancel()
        state.value = DataSourceStatus(DataSourceStatus.State.OFF, context.clock.nowMillis())
    }

    private fun load(): Boolean =
        try {
            lastModified = Files.getLastModifiedTime(path).toMillis()
            context.store.init(SnapshotCodec.decode(Files.readString(path)))
            state.value = DataSourceStatus(DataSourceStatus.State.VALID, context.clock.nowMillis())
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            context.logger.error("Could not load flags from $path", e)
            val error = ErrorInfo(ErrorInfo.Kind.INVALID_DATA, message = e.message, timeMillis = context.clock.nowMillis())
            state.value = state.value.copy(lastError = error)
            false
        }

    public companion object {
        @JvmStatic
        @JvmOverloads
        public fun factory(
            path: Path,
            reloadInterval: Duration? = null,
        ): DataSourceFactory = DataSourceFactory { FileDataSource(path, reloadInterval, it) }
    }
}
