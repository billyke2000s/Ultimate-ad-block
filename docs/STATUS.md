# Current state — read before work

Updated: 2026-11-02. Repository: `billyke2000s/Ultimate-ad-block`. Product name: **System Wide Block Browser**.

## What the owner requested
A separate app based on Blockbrowser's ad blocking, filtering across Android apps using a local VPN where appropriate, with a pie chart and other chart styles. The owner authorized publishing this source and bundled blocklist to this public repository, and asked for cross-AI rules and a clear handoff. This record is project context, not blanket future authorization for unrelated actions.

## Implementation and evidence
| Area | Current status | Evidence / limit |
| --- | --- | --- |
| Independent module and package | Build verified | Android debug APK assembled in CI; device installation pending |
| VPN start/stop, consent, notification | Implemented / unverified | `Dashboard`, `BlockVpn`; no device test |
| DNS parser/reply framing | Core checks passed | Four core methods ran on Java 17 compiler/runtime; not complete DNS coverage |
| Domain matching and exceptions | Core checks passed | Exact/parent/lookalike/allowance cases in CoreTest |
| Encrypted upstream forwarding | Implemented / verification pending | Protected TLS sockets to Cloudflare; new integration tests pending |
| Pie, doughnut, bars, daily line | Implemented / unverified | Dashboard source; no rendered Android UI check |
| Local counters/reset/persistence | Implemented / unverified | Stats source; process/lifecycle tests pending |
| Android build, lint, JUnit Gradle task | CI verified | All three Gradle tasks passed on commit `cbf5e3e50e00d4fe5f0b68ed11f26d20381b5f6c`; see run below |
| Cross-AI guidance | Implemented | Root/scoped AGENTS, model entry files and docs |
| APK release | Debug testing artifact available; no production release | CI uploaded `System-Wide-Block-Browser-debug`; device verification and stable signing remain pending |

## 0.2.0 reliability work (verification pending)
- Encrypted Cloudflare upstream via reusable, protected TLS sockets with endpoint verification, timeouts and no plaintext fallback.
- Shared lifecycle lock prevents tunnel publication after stop; stop/revoke closes TLS connections and workers.
- EDNS-compatible question validation, clean synthetic DNS errors, bounds checks and UDP truncation budgets.
- Restored statistics are capped; chart accessibility text/day labels improved; unsupported always-on mode disabled.
- Added regression tests and Android 8/15 emulator integration jobs. No passing result is claimed for this revision until CI completes.
- Testing release v0.2.0 is gated on all checks; debug signing and client TCP DNS remain explicit limitations.

## Previous CI evidence (before 0.2.0 changes)
- Verified commit: `cbf5e3e50e00d4fe5f0b68ed11f26d20381b5f6c`.
- Successful run: https://github.com/billyke2000s/Ultimate-ad-block/actions/runs/41539086946
- Passed: `:systemblock:testDebugUnitTest`, `:systemblock:assembleDebug`, `:systemblock:lintDebug` (Gradle 8.9 / Java 17).
- APK artifact: https://github.com/billyke2000s/Ultimate-ad-block/actions/runs/41539086946/artifacts/13185521268
- Fixed dashboard lint failures by replacing raw orientation/permission constants with Android API constants; paint flags are now named too. No checks disabled or baselines added.
- CI success verifies compilation/core unit tests/lint, not actual VPN filtering, UI appearance or device lifecycle.

## Actual checks performed
- Four `CoreTest` methods executed successfully using `java -m jdk.compiler/com.sun.tools.javac.Main` plus a temporary minimal JUnit-compatible assertion harness, then the Java runtime: domain rules, malformed DNS, response validation, framing/checksum. This was **not** Gradle/JUnit or an Android build. Harness is scratch-only, not a production dependency.
- `git diff --check` passed before guidance additions; rerun for final changes.
- No emulator, physical-device, UI rendering, DNS integration or benchmark result has been obtained. Android lint passed in the linked CI run.

## Next work, in order
1. Install the debug artifact and run the device matrix below. CI build/lint/unit tests now pass; retain these checks for subsequent changes.
2. Exercise lifecycle races, especially stop during initial tunnel establishment and partial initialization failure; fix leaks/stale state with tests if found.
3. Test DNS interoperability: EDNS, large/truncated replies, unsupported TCP, malformed queries, queue saturation and upstream failure. Decide TCP handling before broad reliability claims.
4. Run the device matrix in VERIFICATION, including ordinary IPv4/IPv6 connectivity and Private DNS bypasses. Verify charts visually and accessibility.
5. Complete attribution/licensing review, stable signing and release/version strategy before distribution.

## Known limitations
- Not every ad can be blocked; same-domain ads remain.
- Private DNS, app-specific DoH/DoT, hard-coded addresses and cached answers can bypass the DNS filter.
- One VPN app per Android profile. No concurrent privacy VPN or lockdown compatibility.
- Upstream DNS now uses TLS; port 443 must be reachable. Client TCP DNS and encrypted-DNS interception are not implemented.
- Per-app statistics, cosmetic filtering, traffic forwarding and bandwidth saving measurements do not exist.
- Shutdown synchronization and large UDP reply truncation are implemented; new runtime/regression checks must pass before verified claims.
- Buffered stats may lose the latest increments after abrupt process death; history has retention caps.

## Handoff discipline
Replace stale facts here when evidence changes. Preserve precise limits. Link CI runs/commits and record device models/Android versions, cases, result and date. Never treat old upstream screenshots or browser testing docs as proof for systemblock. A new chat should start at root AGENTS.md and this file, then inspect the current source and git state.
