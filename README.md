# Feature Flag SDK

A universal feature-flag SDK for **Web, Android, iOS and Kotlin backends**, built on a shared
**Kotlin Multiplatform** core so every platform evaluates flags with identical semantics.

- **Client SDKs (Web / Android / iOS):** download a per-user snapshot, persist it, and evaluate locally —
  zero network latency, offline-first.
- **Server SDK (Kotlin / JVM):** full rule set in memory, lock-free thread-safe evaluation for high throughput,
  real-time updates via SSE with polling fallback.

> Status: **design phase**. The documents below describe the architecture; implementation follows the
> [implementation plan](docs/implementation-plan.md).

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
