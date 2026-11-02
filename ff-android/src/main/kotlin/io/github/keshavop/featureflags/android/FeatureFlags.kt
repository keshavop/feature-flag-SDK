package io.github.keshavop.featureflags.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import io.github.keshavop.featureflags.client.ClientConfig
import io.github.keshavop.featureflags.client.ClientPlatform
import io.github.keshavop.featureflags.client.FeatureFlagClient
import io.github.keshavop.featureflags.client.FeatureFlagClients
import io.github.keshavop.featureflags.client.FileKeyValueStore
import io.github.keshavop.featureflags.client.IdentifyResult
import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.transport.okhttp.OkHttpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android entry point (LLD §10.1). Call [init] once, typically in `Application.onCreate`, then use [get] anywhere.
 * Cached flags are served immediately; listeners run on the main thread.
 *
 * ```
 * FeatureFlags.init(this, "mob-…", EvaluationContext.user(userId).build(),
 *     ClientConfig(endpoints = ClientEndpoints("https://flags.example.com")))
 * if (FeatureFlags.get().boolVariation("new-checkout", false)) { … }
 * ```
 */
public object FeatureFlags {
    @Volatile
    private var instance: FeatureFlagClient? = null
    private val callbacks = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    @JvmOverloads
    @Synchronized
    public fun init(
        application: Application,
        mobileKey: String,
        context: EvaluationContext,
        config: ClientConfig = ClientConfig(),
    ): FeatureFlagClient {
        instance?.let { return it }
        val logger = LogcatLogger()
        val platform =
            ClientPlatform(
                name = "android",
                transport = OkHttpTransport(SdkInfo.userAgent("android")),
                storage = FileKeyValueStore(File(application.noBackupFilesDir, "feature-flags")),
                lifecycle = AndroidLifecycle(application),
                connectivity = AndroidConnectivity(application),
                listenerDispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher(),
                backgroundDispatcher = Dispatchers.IO,
                logger = logger,
            )
        return FeatureFlagClients.create(mobileKey, context, config, platform).also { instance = it }
    }

    /** The client created by [init]. */
    @JvmStatic
    public fun get(): FeatureFlagClient = checkNotNull(instance) { "FeatureFlags.init() has not been called" }

    /** Callback flavour of [FeatureFlagClient.identify] for Java callers; the callback runs on the main thread. */
    @JvmStatic
    public fun identify(
        context: EvaluationContext,
        callback: IdentifyCallback,
    ) {
        callbacks.launch { callback.onResult(get().identify(context)) }
    }

    public fun interface IdentifyCallback {
        public fun onResult(result: IdentifyResult)
    }
}
