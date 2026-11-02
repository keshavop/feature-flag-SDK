// JMH benchmarks for the evaluation hot path (HLD §4). Not published. Run: ./gradlew :benchmarks:jmh
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.jmh)
}

dependencies {
    jmh(project(":ff-core"))
}

jmh {
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    timeUnit.set("ns")
    benchmarkMode.set(listOf("avgt"))
    jmhVersion.set("1.37")
}
