# ADR-006: Percentage rollout model — sticky, monotonic ramps with control-plane schedules

**Status:** Proposed
**Date:** 2026-10-27
**Deciders:** keshavop, product/experimentation lead, backend platform lead

## Context
Teams need to release a feature to a configurable share of users — e.g. **1 % of the user base**, then 5 %, 30 %,
100 % — either across everyone or within a targeted subset ("30 % of users in country = IN"). Two properties are
non-negotiable:
1. **Stickiness:** the same user gets the same answer on every evaluation, device and platform.
2. **Monotonic ramp-up:** users who got the feature at 1 % must still have it at 30 % (no flapping in/out),
   and ramp-down removes the most recently added users first.

Ramps are often scheduled ("go to 5 % tomorrow 10:00 if healthy") and must be instantly reversible (kill switch).

## Decision
1. A rollout is a list of `(variation, weight)` ranges over **100,000 buckets** (0.001 % granularity), assigned by
   the MurmurHash3 bucket of ADR-004. A percentage `p` maps to `weight = round(p × 1000)`.
2. **Range layout is stable:** the rolled-out (treatment) variation always occupies the range starting at bucket 0;
   the control/"off" variation is always the **last** range. Increasing `p` only extends the treatment range, so
   every previously included user stays included (monotonic). Decreasing `p` drops the highest buckets first.
3. **Bucketing unit is configurable** (`bucketBy` attribute + `contextKind`): user key (default), device key for
   anonymous users, organization key for account-level rollouts.
4. Contexts without the `bucketBy` attribute land in the **last (control) range**, never in treatment.
5. A rollout can be a rule's outcome (percentage of a targeted subset) or the fallthrough (percentage of everyone
   not otherwise targeted). Individual targets (e.g. internal users forced on, VIPs forced off) take priority.
6. **Schedules live in the control plane**, not the SDK: the flag management service applies each stage at its
   time and publishes a normal `patch` (propagates in ≤ 1 s to servers via SSE). The SDK always evaluates the
   *current* percentage, so behaviour never depends on device clocks.

## Options Considered

### Option A: Sticky bucket ranges + control-plane schedules (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Low in SDK — pure function of (flag, salt, key) |
| Cost | None at runtime |
| Scalability | High — no per-user state |
| Team familiarity | High |

**Pros:** deterministic, stateless, cross-platform identical, monotonic ramps, instant rollback by patch.
**Cons:** re-weighting a *multi-variant experiment* mid-flight reassigns some users (documented; control plane warns).

### Option B: Stored per-user assignments
**Pros:** perfect stickiness even across weight changes. **Cons:** needs a datastore lookup per evaluation —
violates zero-latency local evaluation; huge storage.

### Option C: Schedules evaluated inside the SDK (time-based stages in the payload)
**Pros:** no control-plane job needed. **Cons:** depends on client clocks (skewed / user-modifiable); different
devices would disagree at stage boundaries; harder to pause a ramp on a bad metric.

## Trade-off Analysis
Hash-range bucketing gives stickiness for free with zero runtime state; a fixed range layout turns it into a
monotonic ramp. Keeping schedules server-side preserves the SDK as a pure evaluator and makes "pause/rollback the
ramp" a single control-plane action.

## Consequences
- Easier: 1 % → 100 % progressive delivery with no user flapping; kill switch = set to 0 % or flag off.
- Harder: control plane must enforce the range layout (treatment first, control last) and validate weights sum to 100,000.
- Revisit: guardrail-driven automatic ramp pause/rollback (metrics-based) — control-plane feature, no SDK change.

## Action Items
1. [ ] Implement `Rollout`/`PercentageRollout` model + validation in `ff-core` (LLD §4.4).
2. [ ] Conformance vectors: same key evaluated at 1 %, 5 %, 30 %, 100 % is never removed when ramping up.
3. [ ] `TestDataSource` helper `percentageRollout(...)` for consumer tests.
