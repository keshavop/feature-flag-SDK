# Feature Flag SDK

A universal feature-flag SDK for **Web, Android, iOS and Kotlin backends**, built on a shared
**Kotlin Multiplatform** core so every platform evaluates flags with identical semantics.

- **Client SDKs (Web / Android / iOS):** download a per-user snapshot, persist it, and evaluate locally —
  zero network latency, offline-first.
- **Percentage rollouts:** release to a configurable share of users (e.g. 1 %, then 30 %, then 100 %) with
  sticky, cross-platform-consistent bucketing — users already included stay included as you ramp up.
- **Server SDK (Kotlin / JVM):** full rule set in memory, lock-free thread-safe evaluation for high throughput,
  real-time updates via SSE with polling fallback.

> Status: **all six phases implemented** — server, Android, iOS and Web SDKs, observability and release tooling. Not yet
> released; see the [implementation plan](docs/implementation-plan.md) for what is still open.
> The design documents below describe the architecture; implementation follows the
> [implementation plan](docs/implementation-plan.md).

## Server SDK quick start

```kotlin
val client = FeatureFlagServer.create(
    sdkKey = System.getenv("FF_SERVER_KEY"),
    config = ServerConfig(endpoints = ServerEndpoints("https://flags.example.com")),
)
val user = EvaluationContext.user("user-42").set("country", "IN").build()
if (client.boolVariation("new-checkout", user, default = false)) { /* new flow */ }
client.close()   // on shutdown: flushes analytics events
```

For tests, use `ServerDataSource.Custom(TestDataSource())`; see `samples/server-jvm`.

## Android quick start

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FeatureFlags.init(this, "mob-key", EvaluationContext.user(currentUserId).build(),
            ClientConfig(endpoints = ClientEndpoints("https://flags.example.com")))
    }
}
// anywhere, on any thread — a local lookup, never a network call
if (FeatureFlags.get().boolVariation("new-checkout", false)) { /* … */ }
```

## iOS quick start

```swift
import FeatureFlags

let flags = FeatureFlags.shared.start(
    mobileKey: "mob-key",
    context: FeatureFlags.shared.userContext(key: userId, attributes: ["plan": "pro"]),
    baseUri: "https://flags.example.com")
if flags.boolVariation(key: "new-checkout", default: false) { /* … */ }
let result = try await flags.identify(newContext: FeatureFlags.shared.userContext(key: newUserId, attributes: [:]))
```

## Web quick start

```js
import { initialize } from "@keshavop/feature-flags";

const flags = await initialize("cli-key", { kind: "user", key: "user-42", plan: "pro" },
  { baseUri: "https://flags.example.com", bootstrap: window.__FLAGS__ /* optional, from allFlagsState */ });
if (flags.boolVariation("new-checkout", false)) { /* … */ }
const stop = flags.on("change:new-checkout", (value) => render(value));
```

## Packages

| Platform | Package | Registry |
|---|---|---|
| Kotlin Multiplatform / JVM / iOS | `io.github.keshavop:feature-flags-core` | Maven Central |
| Kotlin/Java server | `io.github.keshavop:feature-flags-server-jvm` | Maven Central |
| Android | `io.github.keshavop:feature-flags-android` | Maven Central |
| iOS (Swift) | `https://github.com/keshavop/feature-flag-SDK` (`FeatureFlags` XCFramework) | Swift Package Manager |
| Web (browser / Node) | `@keshavop/feature-flags` | npm |

## Building

Requires JDK 17+ (CI uses 21). iOS targets compile on any host; iOS tests run on macOS only.

```sh
./gradlew build                       # compile all targets, ktlint, API check, JVM + JS tests
./gradlew apiDump                     # after an intended public API change
./gradlew :ff-web:prepareNpmPackage   # npm package in ff-web/build/npm
./gradlew publishToMavenLocal         # Maven artifacts in ~/.m2
```

Releases are cut by pushing a `v*` tag; see [docs/releasing.md](docs/releasing.md).

## Documentation site

`website/` is a Next.js site with step-by-step integration guides for every platform (including Next.js server-side
rendering), an interactive percentage-rollout playground and all the documents below. It renders `docs/` at build time,
so editing a Markdown file here updates the site. On every push to `main`, `.github/workflows/docs.yml` publishes it to
GitHub Pages at `https://keshavop.github.io/feature-flag-SDK/`, with the Dokka API reference under `/api/`.

```sh
cd website
npm ci
npm run dev    # http://localhost:3000
npm test       # bucketing conformance vectors and doc link rewriting
npm run build  # static export in website/out
```

## Design documents

| Document | Description |
|---|---|
| [High-Level Design](docs/hld.md) | Requirements, system context, SDK profiles, data flows, resilience, security |
| [Low-Level Design](docs/lld.md) | Modules, domain model, public APIs, evaluation algorithm, concurrency, sync, events, testing |
| [Implementation Plan](docs/implementation-plan.md) | Phased roadmap, PR breakdown, exit criteria, risks, per-phase status |
| [Getting started](docs/getting-started.md) | Server, Android, iOS and Web usage |
| [Threat model](docs/threat-model.md) | Keys, personal data, payload handling, residual risks |
| [Releasing](docs/releasing.md) | One-click release to Maven Central, npm and Swift Package Manager |

### Architecture Decision Records

| ADR | Decision |
|---|---|
| [ADR-001](docs/adr/ADR-001-kotlin-multiplatform-core.md) | Kotlin Multiplatform shared core with thin platform shells |
| [ADR-002](docs/adr/ADR-002-local-evaluation-with-snapshots.md) | Local evaluation from snapshots |
| [ADR-003](docs/adr/ADR-003-realtime-sync-sse-with-polling-fallback.md) | SSE streaming with polling fallback |
| [ADR-004](docs/adr/ADR-004-deterministic-bucketing-murmur3.md) | MurmurHash3 deterministic bucketing |
| [ADR-005](docs/adr/ADR-005-backend-concurrency-model.md) | Immutable snapshot + atomic swap concurrency |
| [ADR-006](docs/adr/ADR-006-percentage-rollout-model.md) | Configurable, sticky percentage rollouts (1 %, 30 %, …) |

## Planned modules

```
ff-core            pure evaluation semantics (models, evaluator, bucketing, store)
ff-sync            polling / SSE / file data sources
ff-events          event batching & delivery
ff-cache           persistent snapshot cache (per platform)
ff-client-common   client orchestration (Android, iOS, Web)
ff-server-jvm      Kotlin/Java server SDK
ff-android | ff-ios | ff-web   platform shells
```

## License

See [LICENSE](LICENSE).
