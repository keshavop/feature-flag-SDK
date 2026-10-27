# ADR-004: Deterministic bucketing with MurmurHash3 (x86_32) over 100,000 buckets

**Status:** Proposed
**Date:** 2026-10-27
**Deciders:** keshavop, experimentation/data lead

## Context
Percentage rollouts and experiments require each context to land in the same bucket every time, on every
platform, without storing assignments. The hash must be fast, well-distributed and trivially portable.

## Decision
`bucket = murmur3_x86_32(utf8("<flagKey|seed>.<salt>.<bucketByValue>"), seed=0) as unsigned mod 100_000`,
implemented in pure Kotlin in `ff-core/commonMain`. Weights are expressed in 1/1000 %.

## Options Considered

### Option A: MurmurHash3 32-bit (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Low — ~40 lines, portable |
| Cost | Very low CPU |
| Scalability | n/a |
| Team familiarity | High — widely used for bucketing |

**Pros:** excellent distribution, fast, no crypto dependency, easy to reproduce in analytics (SQL/Spark UDFs exist).
**Cons:** non-cryptographic (irrelevant here).

### Option B: SHA-1 / SHA-256 prefix
**Pros:** standard everywhere. **Cons:** slower; platform crypto APIs differ (JS async `SubtleCrypto`) → hurts sync evaluation.

### Option C: Platform `hashCode()`
**Pros:** free. **Cons:** differs across platforms and runtimes — breaks stickiness. Rejected.

## Trade-off Analysis
MurmurHash3 combines speed with deterministic cross-platform output when implemented once in common Kotlin;
100,000 buckets give 0.001 % granularity, sufficient for gradual rollouts on very large populations.

## Consequences
- Easier: identical assignment on every platform and in offline analysis.
- Harder: changing the algorithm later re-buckets everyone → requires a `bucketingVersion` field.
- Revisit: only with a versioned migration.

## Action Items
1. [ ] Implement `Murmur3.hash32x86` + ≥ 200 conformance vectors.
2. [ ] Distribution property test (±0.3 % per decile over 1M keys).
