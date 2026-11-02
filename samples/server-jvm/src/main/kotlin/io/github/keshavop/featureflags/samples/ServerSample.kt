package io.github.keshavop.featureflags.samples

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.server.FeatureFlagServer
import io.github.keshavop.featureflags.server.ServerConfig
import io.github.keshavop.featureflags.server.ServerDataSource
import io.github.keshavop.featureflags.sync.TestDataSource

/**
 * Ramps `new-checkout` from 1 % to 30 % to 100 % and shows that users who had the feature keep it.
 * With a real delivery service, replace the TestDataSource with `endpoints = ServerEndpoints("https://flags.example.com")`.
 */
fun main() {
    val flags = TestDataSource()
    flags.update(flags.flag("new-checkout").fallthroughPercentage(1.0))

    FeatureFlagServer.create("sample-key", ServerConfig(dataSource = ServerDataSource.Custom(flags))).use { client ->
        val users = (1..10_000).map { EvaluationContext.user("user-$it").set("country", "IN").build() }
        var previous = emptySet<String>()
        for (percent in listOf(1.0, 30.0, 100.0)) {
            flags.update(flags.flag("new-checkout").fallthroughPercentage(percent))
            val enabled = users.filter { client.boolVariation("new-checkout", it, false) }.map { it.kind("user")!!.key }.toSet()
            println("%5.1f %% -> %5d of %d users have new-checkout; kept everyone from the previous stage: %s".format(percent, enabled.size, users.size, enabled.containsAll(previous)))
            previous = enabled
        }
    }
}
