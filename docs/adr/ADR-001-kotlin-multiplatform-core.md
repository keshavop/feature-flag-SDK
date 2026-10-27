# ADR-001: Kotlin Multiplatform shared core with thin platform shells

**Status:** Proposed
**Date:** 2026-10-27
**Deciders:** keshavop (SDK owner), mobile leads (Android/iOS), web lead, backend platform lead

## Context
We must ship SDKs for Web, Android, iOS and Kotlin backends. The most expensive bug class in feature-flag SDKs is
**semantic drift**: the same flag returning different variations on different platforms (operator edge cases,
bucketing hash differences, prerequisite ordering). Our team is Kotlin-first. Binary size and native ergonomics
matter on iOS and Web.

## Decision
Implement all semantics (models, evaluator, operators, bucketing, sync state machines, event batching) once in
Kotlin Multiplatform (`commonMain`), compiled to JVM, Android, iOS (XCFramework) and JS (IR). Each platform gets a
thin shell for I/O, persistence, lifecycle and an idiomatic public API (SKIE for Swift, `@JsExport` + `.d.ts` for Web).

## Options Considered

### Option A: Kotlin Multiplatform core (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Medium — KMP build/publishing tooling, Swift interop needs SKIE |
| Cost | Low — one evaluator, one test suite |
| Scalability | High — new targets (Wasm, edge workers, desktop) reuse core |
| Team familiarity | High — Kotlin-first team |

**Pros:** single source of truth for semantics; shared conformance tests; one release train; Kotlin native on Android & backend.
**Cons:** iOS framework size overhead (~1 MB); Swift API needs care; Kotlin/JS bundle larger than hand-written TS.

### Option B: Native SDK per platform (Kotlin, Swift, TypeScript) + shared spec
| Dimension | Assessment |
|---|---|
| Complexity | High — 3 codebases × every feature |
| Cost | High — 3× implementation and maintenance |
| Scalability | Medium — each new feature lands 3 times |
| Team familiarity | Medium — needs Swift & TS specialists |

**Pros:** best-in-class idioms and size per platform. **Cons:** semantic drift risk; triple effort; slower feature velocity.

### Option C: C/Rust core via FFI
| Dimension | Assessment |
|---|---|
| Complexity | High — FFI on 4 targets, Wasm for Web |
| Cost | High |
| Scalability | High |
| Team familiarity | Low |

**Pros:** smallest, fastest core. **Cons:** JNI cost on the server hot path; unfamiliar stack; hard debugging on mobile.

## Trade-off Analysis
Correctness parity across platforms outweighs a few hundred KB of binary size. KMP removes drift by construction
while keeping the server path pure JVM (no JNI). The main risks (Swift ergonomics, JS bundle size) are bounded and
mitigated: SKIE + a small Swift extension layer; Web shell bypasses Ktor and uses browser `fetch`/`EventSource`.

## Consequences
- Easier: one evaluator, one conformance suite, synchronized releases, new platforms cheap.
- Harder: KMP build & multi-registry publishing (Maven Central, SPM, npm); iOS debugging across the Kotlin boundary.
- Revisit: if Web bundle > 25 KB gz after DCE, consider a hand-written TS shell around a minimal Kotlin/JS evaluator.

## Action Items
1. [ ] Phase 0 spike: measure iOS XCFramework and JS bundle size for `ff-core`.
2. [ ] Set up `build-logic` convention plugins for KMP targets.
3. [ ] Add SKIE to `ff-ios`; design Swift facade review with iOS lead.
