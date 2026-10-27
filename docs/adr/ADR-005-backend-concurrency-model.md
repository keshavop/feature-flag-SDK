# ADR-005: Backend concurrency — immutable snapshot + atomic reference swap (lock-free reads)

**Status:** Proposed
**Date:** 2026-10-27
**Deciders:** keshavop, backend platform lead

## Context
The server SDK serves ≥ 100k evaluations/s/core across many request threads while a background data source applies
updates. Evaluations must be consistent (prerequisites and segments read from the same version), never block, and
have p99 < 1 µs.

## Decision
Hold the whole flag state as an immutable `Snapshot` in a single `AtomicReference`. Each evaluation performs one
volatile read and works on that immutable graph. Updates are applied by a single writer coroutine that builds a new
snapshot copy-on-write (persistent maps for large environments) and CAS-swaps it. Listeners are notified
asynchronously on a dedicated dispatcher.

## Options Considered

### Option A: Immutable snapshot + atomic swap (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Low |
| Cost | Copy per update (mitigated by persistent collections) |
| Scalability | Excellent read scaling, no contention |
| Team familiarity | High |

**Pros:** lock-free wait-free reads; consistent multi-flag view; trivially correct; multiplatform via atomicfu.
**Cons:** O(n) copy per update with plain maps; extra GC on frequent updates.

### Option B: `ConcurrentHashMap` per item
| Dimension | Assessment |
|---|---|
| Complexity | Low |
| Cost | Low per update |
| Scalability | Good |
| Team familiarity | High |

**Pros:** cheap point updates. **Cons:** no consistent view across flag + prerequisites + segments mid-update; JVM-only.

### Option C: `ReentrantReadWriteLock`
**Pros:** familiar. **Cons:** reader contention / cache-line bouncing at high throughput; writer starvation risks.

## Trade-off Analysis
Updates are rare (seconds–minutes) while reads are constant (µs). Optimising for reads and consistency is the
right asymmetry; persistent hash maps reduce update cost to O(log n).

## Consequences
- Easier: reasoning about correctness, lincheck verification, sharing code with clients.
- Harder: very large environments with high update rates need persistent collections (planned behind `Snapshot.withFlag`).
- Revisit: if profiling shows GC pressure from snapshot churn.

## Action Items
1. [ ] Implement `InMemoryFlagStore` with atomicfu.
2. [ ] Lincheck + 64-thread stress test; JMH benchmark gate in CI.
