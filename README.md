# Feature Flag SDK

A universal feature-flag SDK for **Web, Android, iOS and Kotlin backends**, built on a shared
**Kotlin Multiplatform** core so every platform evaluates flags with identical semantics.

- **Client SDKs (Web / Android / iOS):** download a per-user snapshot, persist it, and evaluate locally —
  zero network latency, offline-first.
- **Percentage rollouts:** release to a configurable share of users (e.g. 1 %, then 30 %, then 100 %) with
  sticky, cross-platform-consistent bucketing — users already included stay included as you ramp up.
- **Server SDK (Kotlin / JVM):** full rule set in memory, lock-free thread-safe evaluation for high throughput,
  real-time updates via SSE with polling fallback.

> Status: **Phase 0 (build & release pipeline)**. The documents below describe the architecture; implementation
> follows the [implementation plan](docs/implementation-plan.md).

## Packages

| Platform | Package | Registry |
|---|---|---|
| Kotlin Multiplatform / JVM / iOS | `io.github.keshavop:feature-flags-core` | Maven Central |
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

## Design documents

| Document | Description |
|---|---|
| [High-Level Design](docs/hld.md) | Requirements, system context, SDK profiles, data flows, resilience, security |
| [Low-Level Design](docs/lld.md) | Modules, domain model, public APIs, evaluation algorithm, concurrency, sync, events, testing |
| [Implementation Plan](docs/implementation-plan.md) | Phased roadmap, PR breakdown, exit criteria, risks |

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
