# Threat model

Scope: the SDKs in this repository. The flag delivery service, admin UI and analytics backend are out of scope, but
the SDK's assumptions about them are listed.

## Assets

| Asset | Where | Why it matters |
|---|---|---|
| Server SDK key | backend config / env | grants all rules and segments of an environment |
| Targeting rules and segment lists | server memory only | business logic; segment lists often contain user ids/emails |
| End-user context (ids, attributes) | app memory, requests, events | personal data |
| Cached flag values | device storage | reveal which features a user has |
| Analytics events | in transit, events backend | personal data, experiment integrity |

## Trust boundaries and mitigations

| Threat | Mitigation in the SDK |
|---|---|
| Server key extracted from an app | Client SDKs use separate client/mobile keys that can only fetch values evaluated for the supplied context (ADR-002). Rules and segment lists never reach devices. |
| Keys leaking through logs or proxies | Keys travel only in the `Authorization` header, never in URLs. The SDK never logs keys or context attribute values. |
| Network attacker | HTTPS endpoints are expected (the SDK does not downgrade); certificate pinning can be added through a custom `HttpTransport` / OkHttp client. |
| Malicious or corrupt payloads | Payloads are fully parsed before anything is applied; invalid ones are rejected and the last good snapshot stays. Older versions never overwrite newer ones, tombstones block resurrection. Unknown operators never match. Regexes are limited to a portable subset without lookaround/backreferences (no catastrophic backtracking on the hot path). Prerequisite cycles and segment nesting > 10 are reported as malformed. |
| Personal data in events | Private attributes (global or per context, including nested paths) are removed before events are serialized and listed only by name in `_meta.redactedAttributes`. Custom events carry context keys only. OpenTelemetry events include values and context ids only when explicitly enabled. |
| Personal data in device caches | Cache file names are hashes of key + context, not user ids. Android stores them in `noBackupFilesDir`; iOS marks the directory excluded from backup. Only flag values are cached, not context attributes. |
| User switching on shared devices | `identify` clears the previous user's values at once and discards responses still in flight for them (generation counter). |
| Denial of service against the app | Evaluation never throws and never blocks on I/O. Event buffers are bounded (events are dropped and counted, never queued without limit). Invalid keys (401/403) stop all retries. Reconnects use exponential backoff with jitter. |
| Thundering herd on the service | Polling intervals are jittered (±10 %), the minimum poll interval is 30 s, and conditional requests (ETag) make unchanged data cheap. |

## Residual risks

- Client-side values are visible to the device's user by design; never put secrets in flag values.
- Web `localStorage` is readable by any script on the origin; an XSS bug exposes cached flag values.
- Event payloads from iOS and Web are not gzip-compressed (still sent over HTTPS).
- Supply chain: releases are signed (Maven Central) and published with npm provenance; the XCFramework checksum is pinned in `Package.swift`.
