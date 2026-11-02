# Current state — read before work

Updated: 2026-11-02. Repository: `billyke2000s/Ultimate-ad-block`. Product name: **System Wide Block Browser**.

## What the owner requested
A separate app based on Blockbrowser's ad blocking, filtering across Android apps using a local VPN where appropriate, with a pie chart and other chart styles. The owner authorized publishing this source and bundled blocklist to this public repository, and asked for cross-AI rules and a clear handoff. This record is project context, not blanket future authorization for unrelated actions.

## Implementation and evidence
| Area | Current status | Evidence / limit |
| --- | --- | --- |
| Independent module and package | Implemented / unverified | `systemblock/build.gradle.kts`; Android build not yet verified |
| VPN start/stop, consent, notification | Implemented / unverified | `Dashboard`, `BlockVpn`; no device test |
| DNS parser/reply framing | Core checks passed | Four core methods ran on Java 17 compiler/runtime; not complete DNS coverage |
| Domain matching and exceptions | Core checks passed | Exact/parent/lookalike/allowance cases in CoreTest |
| UDP upstream forwarding | Implemented / unverified | Protected sockets to 1.1.1.1; no on-device connectivity evidence |
| Pie, doughnut, bars, daily line | Implemented / unverified | Dashboard source; no rendered Android UI check |
| Local counters/reset/persistence | Implemented / unverified | Stats source; process/lifecycle tests pending |
| Android build, lint, JUnit Gradle task | Unverified | Local environment lacks installed Android SDK/Gradle; use CI |
| Cross-AI guidance | Implemented | Root/scoped AGENTS, model entry files and docs |
| APK release | Not released | No verified APK/device result; workflow intended to produce testing artifact |

## Actual checks performed
- Four `CoreTest` methods executed successfully using `java -m jdk.compiler/com.sun.tools.javac.Main` plus a temporary minimal JUnit-compatible assertion harness, then the Java runtime: domain rules, malformed DNS, response validation, framing/checksum. This was **not** Gradle/JUnit or an Android build. Harness is scratch-only, not a production dependency.
- `git diff --check` passed before guidance additions; rerun for final changes.
- No emulator, physical-device, UI rendering, DNS integration, benchmark or Android lint result has been obtained locally.

## Next work, in order
1. Push authorized changes and inspect the actual GitHub Actions build. Fix build/lint failures without disabling checks. Record URL, SHA and results here.
2. Exercise lifecycle races, especially stop during initial tunnel establishment and partial initialization failure; fix leaks/stale state with tests if found.
3. Test DNS interoperability: EDNS, large/truncated replies, unsupported TCP, malformed queries, queue saturation and upstream failure. Decide TCP handling before broad reliability claims.
4. Run the device matrix in VERIFICATION, including ordinary IPv4/IPv6 connectivity and Private DNS bypasses. Verify charts visually and accessibility.
5. Complete attribution/licensing review, stable signing and release/version strategy before distribution.

## Known limitations
- Not every ad can be blocked; same-domain ads remain.
- Private DNS, app-specific DoH/DoT, hard-coded addresses and cached answers can bypass the DNS filter.
- One VPN app per Android profile. No concurrent privacy VPN or lockdown compatibility.
- Standard DNS upstream is unencrypted. TCP DNS and encrypted-DNS interception are not implemented.
- Per-app statistics, cosmetic filtering, traffic forwarding and bandwidth saving measurements do not exist.
- Stop/initialization races and large packet handling need further review/runtime coverage. Do not describe these as fixed merely because this document mentions them.
- Buffered stats may lose the latest increments after abrupt process death; history has retention caps.

## Handoff discipline
Replace stale facts here when evidence changes. Preserve precise limits. Link CI runs/commits and record device models/Android versions, cases, result and date. Never treat old upstream screenshots or browser testing docs as proof for systemblock. A new chat should start at root AGENTS.md and this file, then inspect the current source and git state.
