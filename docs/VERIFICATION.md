# Verification and acceptance criteria

## Evidence vocabulary
| Status | Meaning |
| --- | --- |
| Planned | No implementation yet |
| Implemented / unverified | Source exists; relevant behavior not verified |
| Core checks passed | Specific non-Android core checks ran successfully |
| Build verified | Android assemble succeeded for an exact commit; not runtime proof |
| CI verified | Named checks passed on a linked run and exact commit |
| Device verified | Documented scenario passed on a named device/Android version and APK commit |
| Blocked | Exact external/tooling blocker identified; work not complete |

Never label something “done”, “fully working”, “all apps”, or “release ready” without the relevant evidence. A missing SDK is a blocker, not a passing build. Source inspection and successful compilation do not prove connectivity or lifecycle behavior.

## Standard checks
Requirements: Java 17 **JDK** (compiler included), Gradle 8.9, Android SDK platform 35 and required build tools.

```sh
gradle --no-daemon :systemblock:testDebugUnitTest :systemblock:assembleDebug :systemblock:lintDebug
git diff --check
```

The GitHub workflow performs those module checks, builds the test APK and dnsprobe, then runs cross-app DNS/ordinary TCP and lifecycle checks on API 26/35 emulators before publishing a marked testing prerelease. Record the run URL, exact commit SHA and outcomes in STATUS. If CI fails, inspect actual logs; do not remove lint/tests or add broad suppressions to get green checks.

`CoreTest` plus `DnsRobustnessTest` now contain 15 passing Gradle/JUnit tests. They cover EDNS omission, packet bounds/opcode/question mismatch, UDP truncation budgets and bounded/fragmented encrypted bodies in addition to the original core checks.

`CoreTest` covers domain suffix/allowance matching, malformed DNS rejection, DNS answer association, IPv4/UDP framing and IPv4 header checksum. These four methods do not cover Android APIs, complete DNS interoperability, concurrency, UI or persistence.

## Required runtime matrix before a release
| Scenario | Expected evidence |
| --- | --- |
| Fresh install, consent accepted/declined | Protection starts only after consent; decline leaves no tunnel |
| Blocked and allowed lookups from another app | Actual DNS outcome, matching query counters; ordinary browsing works |
| Domain exception, parent and lookalike domains | Allowance wins; no false suffix match |
| Wi-Fi/mobile transitions and upstream timeout | No app freeze; documented error/recovery behavior |
| IPv4 and IPv6 connectivity | Ordinary traffic still works; scope/bypasses remain explicit |
| Start/stop repeatedly; stop while list loads | No orphan tunnel/thread/socket or stale status |
| System revoke and another VPN replacing this one | Tunnel/notification shut down and dashboard agrees |
| Background and process kill | No misleading connected display; persistence behavior documented |
| Private DNS on/off; app-specific DoH | Document bypass instead of claiming protection |
| All chart modes, zero counters, reset, reopen | Values reflect measured DNS outcomes; no fabricated categories |
| Notification permission denied (Android 13+) | Service behavior and controls remain correct |
| Android 8 minimum and Android 15 target | Install/start/stop and foreground service behavior checked |

Never enable lockdown for this DNS-only design. TCP DNS and large/truncated/EDNS answers need interoperability testing and implementation decisions before making broad reliability claims.

## Review targets needing further tests
- Lifecycle race between tunnel creation and onDestroy; close all descriptors/sockets on partial initialization.
- Malformed DNS header/opcode/count/compression handling, fragments, UDP size boundaries and EDNS.
- Saturated queue, upstream spoof/mismatch, failure recovery and main-thread responsiveness.
- Chart clipping/accessibility, configuration changes and concurrent stats reset.

A release requires a successful Android build/lint/test run, passing critical runtime cases and an explicit list of remaining limitations. Do not invent a physical device result when only an emulator is available.

## Verified v0.2.0 baseline
Release source `042ab7a5d9dbd29b82bc452fb2a50252d3b362a9`: https://github.com/billyke2000s/Ultimate-ad-block/actions/runs/41549297575
Both API 26/35 emulator jobs passed. The CI-only dnsprobe runs as a normal foreground app with a distinct UID and uses Android DNS APIs, ordinary name resolution and TCP connectivity. The parent test requires increased local blocked/allowed counters. Private DNS is disabled in the emulator test fixture. Physical-device and remaining scenarios above are still pending.
