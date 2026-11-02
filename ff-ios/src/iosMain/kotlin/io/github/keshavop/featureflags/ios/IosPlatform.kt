package io.github.keshavop.featureflags.ios

import io.github.keshavop.featureflags.client.AppLifecycle
import io.github.keshavop.featureflags.client.Connectivity
import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.darwin.dispatch_get_main_queue

/** Foreground state from `UIApplication` notifications. Create on the main thread. */
internal class IosLifecycle : AppLifecycle {
    private val state =
        MutableStateFlow(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
    override val isForeground: StateFlow<Boolean> = state.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ -> state.value = false }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, queue) { _ -> state.value = true }
    }
}

/** Online state from `NWPathMonitor`. */
internal class IosConnectivity : Connectivity {
    private val state = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> = state.asStateFlow()
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path -> state.value = nw_path_get_status(path) == nw_path_status_satisfied }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}

/** Logs through `NSLog` with a `[FeatureFlags]` prefix. */
public class NsLogLogger : FeatureFlagsLogger {
    override fun debug(message: String): Unit = log("DEBUG", message, null)

    override fun info(message: String): Unit = log("INFO", message, null)

    override fun warn(
        message: String,
        error: Throwable?,
    ): Unit = log("WARN", message, error)

    override fun error(
        message: String,
        error: Throwable?,
    ): Unit = log("ERROR", message, error)

    private fun log(
        level: String,
        message: String,
        error: Throwable?,
    ) {
        NSLog("%@", "[FeatureFlags] $level $message${error?.let { ": ${it.message}" } ?: ""}")
    }
}
