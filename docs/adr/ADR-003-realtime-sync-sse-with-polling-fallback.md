# ADR-003: Real-time sync via SSE with polling fallback

**Status:** Proposed
**Date:** 2026-10-27
**Deciders:** keshavop, backend platform lead, SRE

## Context
Backends need flag changes (especially kill switches) in ≤ 1 s. Mobile clients need freshness without draining
battery or data. Corporate proxies and load balancers often break long-lived bidirectional connections.

## Decision
- **Server SDK:** Server-Sent Events stream (`put`/`patch`/`delete` + heartbeats). On repeated failures fall back
  to ETag polling while periodically retrying the stream.
- **Client SDKs:** ETag polling by default (foreground 30 s, paused in background, refresh on foreground/network
  regain). Optional SSE for apps that need near-real-time updates.

## Options Considered

### Option A: SSE + polling fallback (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Medium |
| Cost | Low — plain HTTP, CDN/LB friendly |
| Scalability | High — unidirectional, easy fan-out |
| Team familiarity | High |

**Pros:** works through most proxies; native `EventSource` in browsers; simple resumability (full `put` on reconnect).
**Cons:** one long-lived connection per server instance.

### Option B: WebSockets
| Dimension | Assessment |
|---|---|
| Complexity | Higher — bidirectional protocol, more proxy issues |
| Cost | Medium |
| Scalability | High |
| Team familiarity | Medium |

**Pros:** bidirectional. **Cons:** we don't need client→server push; worse proxy compatibility.

### Option C: Polling only
| Dimension | Assessment |
|---|---|
| Complexity | Low |
| Cost | Higher request volume at low intervals |
| Scalability | Medium |
| Team familiarity | High |

**Pros:** simplest. **Cons:** cannot meet ≤ 1 s server propagation without aggressive polling.

### Option D: gRPC streaming
**Pros:** efficient binary. **Cons:** poor browser support, heavier mobile dependency, LB complexity.

## Trade-off Analysis
SSE meets the backend latency target with the least infrastructure friction; polling is the universal safety net
and the right default for battery-sensitive clients.

## Consequences
- Easier: real-time kill switches; graceful degradation.
- Harder: stream service must handle many idle connections; SDK must implement backoff + version ordering.
- Revisit: HTTP/3 / WebTransport when broadly available.

## Action Items
1. [ ] Implement SSE parser in `ff-sync/commonMain` with platform byte-stream adapters.
2. [ ] Backoff with full jitter, 5-min read timeout, fallback-to-polling after 3 failures.
