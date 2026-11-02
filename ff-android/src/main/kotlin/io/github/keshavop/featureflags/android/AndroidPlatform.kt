package io.github.keshavop.featureflags.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.keshavop.featureflags.client.AppLifecycle
import io.github.keshavop.featureflags.client.Connectivity
import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground state from activity callbacks (no AndroidX needed). */
internal class AndroidLifecycle(
    application: Application,
) : AppLifecycle {
    private val handler = Handler(Looper.getMainLooper())
    private val state = MutableStateFlow(false)
    override val isForeground: StateFlow<Boolean> = state.asStateFlow()

    private val tracker =
        ForegroundTracker(
            schedule = { delay, action -> handler.postDelayed(action, delay) },
            onChange = { state.value = it },
        )

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) = tracker.activityStarted()

                override fun onActivityStopped(activity: Activity) = tracker.activityStopped()

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}

/** Online state from [ConnectivityManager] callbacks. */
internal class AndroidConnectivity(
    context: Context,
) : Connectivity {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val state = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = state.asStateFlow()

    init {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        manager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    state.value = true
                }

                override fun onLost(network: Network) {
                    state.value = currentlyOnline()
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun currentlyOnline(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            manager.activeNetworkInfo?.isConnected == true
        }
}

/** Logs to Logcat under the tag `FeatureFlags`. */
public class LogcatLogger : FeatureFlagsLogger {
    override fun debug(message: String) {
        Log.d(TAG, message)
    }

    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun warn(
        message: String,
        error: Throwable?,
    ) {
        Log.w(TAG, message, error)
    }

    override fun error(
        message: String,
        error: Throwable?,
    ) {
        Log.e(TAG, message, error)
    }

    private companion object {
        const val TAG = "FeatureFlags"
    }
}
