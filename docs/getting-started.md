# Getting started

All SDKs share one evaluation engine, so a flag gives the same answer on every platform. Pick yours below.
The SDK has no built-in service URL: point it at your flag delivery service (contract in [HLD §7](./hld.md#7-delivery-contract-what-the-sdk-depends-on)).

| | Server (JVM) | Android | iOS | Web |
|---|---|---|---|---|
| Key type | server key (secret) | mobile key | mobile key | client key |
| Data | all rules, evaluated locally | values for one user | values for one user | values for one user |
| Updates | SSE stream, polling fallback | polling (foreground), optional pings | same | same |

## Server (Kotlin / Java)

```kotlin
dependencies { implementation("io.github.keshavop:feature-flags-server-jvm:<version>") }
```

```kotlin
val client = FeatureFlagServer.create(
    System.getenv("FF_SERVER_KEY"),
    ServerConfig(endpoints = ServerEndpoints("https://flags.example.com")),
)
val user = EvaluationContext.user("user-42").set("country", "IN").build()
val enabled = client.boolVariation("new-checkout", user, false)       // local, lock-free, never throws
val detail = client.boolVariationDetail("new-checkout", user, false)  // value + variation + reason
client.track("checkout-completed", user, null, 49.99)
client.flagTracker.addFlagValueChangeListener("new-checkout", user) { println("now ${it.newValue}") }
client.close() // on shutdown
```

Java: `ServerConfig.builder().endpoints(new ServerEndpoints("https://…")).build()` and the same methods.
Tests: `ServerConfig(dataSource = ServerDataSource.Custom(TestDataSource()))`, then
`td.update(td.flag("new-checkout").fallthroughPercentage(30.0))`. Local files: `FileDataSource.factory(path)`.
Metrics: `MicrometerMetrics(registry)` (`ff.eval.count`, `ff.datasource.state`); tracing: add `OpenTelemetryHook()` from
`feature-flags-otel` to `hooks`.

## Android

```kotlin
dependencies { implementation("io.github.keshavop:feature-flags-android:<version>") }
```

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FeatureFlags.init(this, "mob-key", EvaluationContext.user(userId).build(),
            ClientConfig(endpoints = ClientEndpoints("https://flags.example.com")))
    }
}

val flags = FeatureFlags.get()
if (flags.boolVariation("new-checkout", false)) { /* … */ }
flags.addListener("new-checkout") { change -> /* main thread */ }
lifecycleScope.launch { flags.identify(EvaluationContext.user(newUserId).build()) }
```

Cached values are served at start-up, before the network answers. Polling pauses in the background.

## iOS (Swift)

Swift Package Manager: add `https://github.com/keshavop/feature-flag-SDK` and the `FeatureFlags` product.

```swift
import FeatureFlags

let flags = FeatureFlags.shared.start(mobileKey: "mob-key",
    context: FeatureFlags.shared.userContext(key: userId, attributes: ["plan": "pro"]),
    baseUri: "https://flags.example.com")
if flags.boolVariation(key: "new-checkout", default: false) { /* … */ }
let handle = FeatureFlags.shared.observe(client: flags, key: "new-checkout") { change in /* main thread */ }
let result = try await flags.identify(newContext: FeatureFlags.shared.userContext(key: newUserId, attributes: [:]))
```

## Web

```sh
npm install @keshavop/feature-flags
```

```js
import { initialize } from "@keshavop/feature-flags";

const flags = await initialize("cli-key", { kind: "user", key: "user-42", plan: "pro" }, {
  baseUri: "https://flags.example.com",
  bootstrap: window.__FLAGS__, // optional: server SDK's allFlagsState(context) rendered into the page
});
flags.boolVariation("new-checkout", false);
const stop = flags.on("change:new-checkout", (value, previous) => render(value));
await flags.identify({ kind: "user", key: "user-43" });
```

## Percentage rollouts

Configured in the flag service; every SDK evaluates them identically ([ADR-006](./adr/ADR-006-percentage-rollout-model.md)).
Raising a rollout from 1 % to 30 % keeps everyone who already had the feature. Rollouts can target everyone
(fallthrough), a rule's audience (e.g. `country = IN`), organizations (`contextKind = organization`) or devices.
