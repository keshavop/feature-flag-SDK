package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.core.log.NoOpLogger
import io.github.keshavop.featureflags.sync.EpochClock
import io.github.keshavop.featureflags.sync.HttpTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What each platform shell (Android, iOS, Web) plugs into the shared client. */
public class ClientPlatform(
    public val name: String,
    public val transport: HttpTransport?,
    public val storage: KeyValueStore = InMemoryKeyValueStore(),
    public val lifecycle: AppLifecycle = AppLifecycle.ALWAYS_FOREGROUND,
    public val connectivity: Connectivity = Connectivity.ALWAYS_ONLINE,
    /** Where listeners run: the main thread on mobile, so UI code can react directly. */
    public val listenerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Where network, disk and event work runs. */
    public val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    public val logger: FeatureFlagsLogger = NoOpLogger,
    public val clock: EpochClock = EpochClock.SYSTEM,
)

/** Foreground/background state; polling and streaming pause in the background (HLD §6.3). */
public interface AppLifecycle {
    public val isForeground: StateFlow<Boolean>

    public companion object {
        public val ALWAYS_FOREGROUND: AppLifecycle = MutableAppLifecycle(true)
    }
}

public interface Connectivity {
    public val isOnline: StateFlow<Boolean>

    public companion object {
        public val ALWAYS_ONLINE: Connectivity = MutableConnectivity(true)
    }
}

/** Settable implementations, used by platform shells and tests. */
public class MutableAppLifecycle(
    foreground: Boolean = true,
) : AppLifecycle {
    private val state = MutableStateFlow(foreground)
    override val isForeground: StateFlow<Boolean> = state.asStateFlow()

    public fun setForeground(value: Boolean) {
        state.value = value
    }
}

public class MutableConnectivity(
    online: Boolean = true,
) : Connectivity {
    private val state = MutableStateFlow(online)
    override val isOnline: StateFlow<Boolean> = state.asStateFlow()

    public fun setOnline(value: Boolean) {
        state.value = value
    }
}
