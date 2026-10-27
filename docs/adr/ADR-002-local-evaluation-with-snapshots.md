# ADR-002: Local evaluation from snapshots (full rules on server, per-context snapshots on clients)

**Status:** Proposed
**Date:** 2026-10-27
**Deciders:** keshavop, security reviewer, backend platform lead

## Context
Flag evaluations sit on hot paths (request handling, UI rendering). A network round-trip per evaluation is
unacceptable for latency and availability. However, client devices are untrusted: shipping full targeting rules
would leak segment membership lists (often user IDs/emails) and business logic.

## Decision
- **Server SDK** downloads the full environment snapshot (all flags + segments) and evaluates any context locally.
- **Client SDKs** fetch a snapshot evaluated for the current context (`flag → value/variation`), cache it
  persistently, and evaluate by local lookup. An opt-in *filtered-rules* mode may ship only rules that reference
  the current context, for on-device re-evaluation.

## Options Considered

### Option A: Remote evaluation per call
| Dimension | Assessment |
|---|---|
| Complexity | Low SDK, high service load |
| Cost | High — one request per evaluation |
| Scalability | Poor |
| Team familiarity | High |

**Pros:** simplest SDK, no rules on device. **Cons:** latency, outage = broken app, cost.

### Option B: Full rules everywhere
| Dimension | Assessment |
|---|---|
| Complexity | Medium |
| Cost | Low |
| Scalability | High |
| Team familiarity | High |

**Pros:** offline re-evaluation on any attribute change. **Cons:** privacy leak; payload grows with segment size on mobile.

### Option C: Hybrid — full rules on server, per-context snapshot on clients (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Medium |
| Cost | Low — CDN + conditional requests |
| Scalability | High |
| Team familiarity | High |

**Pros:** zero-latency evaluation everywhere; no rule leakage to devices; small mobile payloads.
**Cons:** client must re-fetch on `identify`/attribute change; needs evaluation endpoint.

## Trade-off Analysis
The hybrid gives local evaluation latency on every platform while respecting the trust boundary. The cost — a
fetch on context change — is acceptable because context changes are rare (login/logout) and cached per context.

## Consequences
- Easier: hot-path performance, offline resilience, privacy story.
- Harder: two snapshot formats; cross-platform parity tests must cover server-evaluated vs. SDK-evaluated results.
- Revisit: filtered-rules mode once privacy review is complete.

## Action Items
1. [ ] Define JSON schemas for server and client snapshots in `conformance/schema/`.
2. [ ] Server SDK `allFlagsState` emits client snapshot format for SSR bootstrap.
