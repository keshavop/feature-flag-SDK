package io.github.keshavop.featureflags.sync

import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.core.model.Snapshot
import io.github.keshavop.featureflags.core.store.FlagStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything a data source needs from the SDK instance that owns it. */
public class DataSourceContext(
    public val store: FlagStore,
    public val scope: CoroutineScope,
    public val transport: HttpTransport?,
    public val logger: FeatureFlagsLogger,
    public val clock: EpochClock = EpochClock.SYSTEM,
)

/** Creates the data source for one SDK instance; configuration picks one (streaming, polling, file, test, offline). */
public fun interface DataSourceFactory {
    public fun create(context: DataSourceContext): DataSource
}

/** Serves a fixed snapshot (offline mode, bootstrap, local files). */
public class StaticDataSource(
    private val store: FlagStore,
    private val snapshot: Snapshot?,
    private val clock: EpochClock = EpochClock.SYSTEM,
) : DataSource {
    private val state = MutableStateFlow(DataSourceStatus(DataSourceStatus.State.INITIALIZING, clock.nowMillis()))

    override val status: StateFlow<DataSourceStatus> = state.asStateFlow()

    override suspend fun start(): Boolean {
        snapshot?.let(store::init)
        state.value = DataSourceStatus(DataSourceStatus.State.VALID, clock.nowMillis())
        return true
    }

    override fun stop() {
        state.value = DataSourceStatus(DataSourceStatus.State.OFF, clock.nowMillis())
    }

    public companion object {
        /** No network at all: flags evaluate from [snapshot] (or are missing, serving defaults). */
        public fun offline(snapshot: Snapshot? = null): DataSourceFactory =
            DataSourceFactory { StaticDataSource(it.store, snapshot ?: Snapshot.EMPTY, it.clock) }
    }
}
