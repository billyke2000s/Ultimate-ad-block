# Current state — read before work

Updated: 2026-11-03. Repository: `billyke2000s/Ultimate-ad-block`.
Product: **AdBlock**. Published **v0.4.0 testing prerelease**.

## Verified v0.4.0 release
- Release: https://github.com/billyke2000s/Ultimate-ad-block/releases/tag/v0.4.0
- Source: `ef5adc05b06dd742559c05ea0f7cb9699efb04b8`.
- Successful CI: https://github.com/billyke2000s/Ultimate-ad-block/actions/runs/41579642400
- 19 unit tests passed; lint 0 errors, 8 warnings; Android assembly passed.
- Both API 26/35 emulator suites passed: real cross-app blocked/allowed DNS, ordinary TCP, lifecycle cases, overview chart/tab visibility without scrolling, chart selection, allowed/blocked/failed activity filtering and search.
- Isolated Android persistence checks passed for save/reload, recording disabled and clearing history without resetting totals.
- Actual API 35 overview, bars, domain filters and Settings captures inspected. Screenshot counters are actual synthetic probe traffic, not production fixtures.
- APK SHA-256: `e92965d080215f971f785514573bbb7c340af7b37c256ad45806b596a883e3df`.
- Physical-device/font-scale/accessibility matrix and the previously reported Play Protect classification remain unresolved. This release changes UI and local query retention, not Play Protect status.

## Previous verified v0.3.0 release
- Release: https://github.com/billyke2000s/Ultimate-ad-block/releases/tag/v0.3.0
- APK: https://github.com/billyke2000s/Ultimate-ad-block/releases/download/v0.3.0/System-Wide-Block-Browser-v0.3.0.apk
- Source: `e0970d2a9b1e702fc8dfb0c7517c02606d275864`.
- Successful build, unit tests, lint and API 26/35 emulator run: https://github.com/billyke2000s/Ultimate-ad-block/actions/runs/41576934215
- Lint: 0 errors, 8 warnings (target API, backup rules and draw allocations). No checks suppressed.
- APK SHA-256: `a876d8cd1e4cc1087e6fb1c591ef4011a73d8f4804488dfffb83060d1ace9809`.
- Active/stopped action state and four chart selectors passed in emulator instrumentation. Actual API 35 dashboard, ring, domain and weekly captures visually inspected; no physical-device/all-font-size accessibility claim.
- README now includes actual emulator captures, with synthetic test traffic explicitly labelled.

## Previous v0.2.0 baseline
- Release: https://github.com/billyke2000s/Ultimate-ad-block/releases/tag/v0.2.0
- APK: https://github.com/billyke2000s/Ultimate-ad-block/releases/download/v0.2.0/System-Wide-Block-Browser-v0.2.0.apk
- Verified source commit: `042ab7a5d9dbd29b82bc452fb2a50252d3b362a9`.
- Successful build, emulator and publication run: https://github.com/billyke2000s/Ultimate-ad-block/actions/runs/41549297575
- APK SHA-256: `392f9bfb015224ca349641e8a0a94b5d0254b9d1ff98faadec48aaf20965dea1`.
- The evidence above applies to the immutable v0.2.0 release commit. The dashboard redesign below is tracked separately.

## v0.2.0 baseline implementation and evidence
| Area | Current status | Evidence / limits |
| --- | --- | --- |
| Independent package / Android APK | CI verified | Install/build tested on Android 8 API 26 and Android 15 API 35 emulators |
| Unit tests | Passed | 15 Gradle/JUnit tests: domain matching, malformed DNS/bounds, EDNS/question validation, UDP truncation and bounded encrypted response bodies |
| Android lint | Passed | 0 errors, 6 warnings; no baseline or disabled checks |
| Cross-app DNS filtering | Emulator verified | A normal foreground test-only app checks blocked/allowed Android DNS resolution; parent asserts increased blocked and allowed counters |
| Ordinary network connectivity | Emulator verified | Test app also resolves example.com normally and opens an ordinary TCP connection |
| Encrypted upstream | Emulator verified | Certificate-verified Cloudflare DNS over HTTPS; protected sockets; 1.1.1.1 and 1.0.0.1 bootstrap; no plaintext fallback |
| Stop/start lifecycle | Emulator verified for named cases | Repeated start/stop, stop during initialization, notification stop action; explicit stop command tears down bound VPN |
| Tunnel ownership / cleanup | Implemented and exercised in CI | Separate owned stream descriptors; bounded nonblocking reader; TLS cleanup off main thread; shared publication/shutdown lock |
| Pie / doughnut / bars / daily line | Implemented | Final Android 15 screenshot inspected for basic dashboard/pie rendering; all-mode visual/accessibility review remains pending |
| Local statistics | Implemented / partial runtime evidence | Real counters checked; saved history capped on load and update; abrupt process-death durability not verified |
| Signing / release | Testing prerelease published | Debug signing only; stable production signing/update compatibility remains pending |
| Physical devices / network transitions | Unverified | No physical-phone, Wi-Fi/mobile transition, notification-denial or complete IPv6 runtime matrix claimed |

## Important fixes in 0.2.0
- Allowed DNS now uses authenticated HTTPS through OkHttp 4.12.0. Socket descriptors are created before VPN protection; replies and connection times are bounded.
- Android 8's missing SSL.com ECC root is supplied for cloudflare-dns.com only. Full chain and hostname verification remain enabled; no device-wide CA is installed. Fingerprint/provenance are recorded in PRIVACY_SECURITY.
- Default DNS remains filtered through the virtual /32 route. Android bypass is enabled for compatibility with ordinary network selection and DNS-family reachability checks. Apps deliberately selecting another network can bypass filtering.
- Error replies remove uncounted EDNS data; response matching compares the question instead of the full query length; oversized UDP answers signal truncation.
- Dashboard Stop uses an explicit stop intent. Shutdown closes the tunnel and worker connections, and supports a requested restart during teardown.
- Blocklist source comments, MIT/GPL texts, and runtime dependency notices are bundled in APK assets.
- dnsprobe/ is a CI-only normal app, never included in the published blocker APK. It exists to verify actual cross-app behavior rather than relying on the VPN provider UID.

## Known limits
- Not every ad can be removed; same-domain ads remain. No HTTPS interception/cosmetic filtering.
- Android Private DNS, app-specific DoH/DoT, explicit network selection, hard-coded IPs and cached answers can bypass the filter.
- Occupies Android's VPN slot; it cannot run alongside another VPN in the same profile. It is not a remote privacy VPN.
- **Client TCP DNS is not implemented.** Large UDP replies are truncated correctly, but clients requiring TCP may fail.
- Do not enable Android “Block connections without VPN”; this app does not forward ordinary traffic.
- No per-app identity/statistics or measured bandwidth savings. Counts are DNS queries, not individual ads.
- Counters are buffered; abrupt process death may lose recent increments. History is capped at 200 domains / 90 days.
- Debug-signed builds are testing artifacts. Installing over an older differently signed build can fail; uninstalling erases settings/statistics.

## README and installation report — 2026-11-03
- Redesigned README with an original Snake-inspired animated banner, source renderer, project navigation and explicit verification boundaries.
- Removed the inaccurate ransomware feature sentence from the README; no application behavior changed.
- User supplied a screenshot of Google Play Protect blocking the v0.2.0 APK as harmful. Cause and classification remain unresolved. Do not claim the APK is cleared or recommend bypassing the warning.
- This report concerns installation; full physical-device runtime testing remains pending.

## Dashboard redesign — 0.3.0
- Native dashboard redesigned with a dark green visual system, shield/launcher icon, one service-driven start/pause action, stat cards and chart tabs.
- Android settings, reset and explanations moved to dialogs; allowed-domain controls remain available. Stats, exceptions, package ID and DNS behavior are preserved.
- Added active/stopped action checks, all-mode chart interaction checks and per-mode emulator screenshots to CI.
- README warning box removed at the user's request. The Play Protect report above remains unresolved; removing presentation copy is not clearance.
- User screenshots show v0.2.0 running with real blocked/allowed counters on their phone; this is limited observation, not the full device matrix.
- Validation for the redesigned UI: build, unit tests, lint and both emulator suites passed; v0.3.0 published. See the verified release section. The earlier Play Protect report remains unresolved.

## Compact navigation and query activity — 0.4.0
- Replaced the tall dashboard with a compact overview and fixed Overview / Activity / Settings navigation.
- Four chart selectors share the available width. Charts are smaller on short displays; Android tests assert chart and selector visibility without scrolling at default emulator font size.
- Activity displays the newest 200 actual DNS queries with domain, allowed/blocked/failed result and time, searchable and filterable. Blocked rows offer an explicit allow-domain action; restarting protection still applies exceptions.
- User explicitly requested visibility of allowed and blocked domains. Recent allowed/failed names and timestamps are now stored locally as well as blocked records. Settings disclose this, allow recording to be disabled and activity to be cleared without clearing totals.
- Existing counters, daily history, exceptions, package identity and DNS routing are preserved. No per-app identity is claimed.
- Native alert dialogs now use the app palette; Settings is a full page.
- Build, 19 unit tests, lint and API 26/35 emulator checks passed. v0.4.0 is published; see verified release above.

## Standalone AdBlock branding and expanded rules — 0.5.0
- Display name, dashboard, VPN session, notification, README, banner and release artifact use AdBlock.
- README no longer links or promotes the browser project. Required upstream data credits remain in licence notices.
- Independent compressed asset: 507,468 unique rules versus 215,492 previously (2.35x). StevenBlack + HaGeZi Multi PRO + Pop-Up Ads + TIF Medium, with parent/subdomain deduplication.
- Additional coverage includes known threats, not only advertising. The medium threat list limits mobile memory requirements; no family, gambling or whole-TLD category list was added.
- Removed the Gradle asset dependency on the original browser module. Updater records exact feed versions/hashes; CI validates the committed bundle and connectivity/ad sentinels.
- Offline bundle integrity check passed locally. Android build, lint, unit and API 26/35 integration checks pending for this revision.

## Unified VPN development branch — 2026-11-05
- Branch: `feature/unified-vpn-protocols`, draft PR #1.
- Added `docs/UNIFIED_VPN_PLAN.md` specifying one-service WireGuard/OpenVPN integration, profile handling, DNS policies and acceptance tests.
- Added a hamburger menu to Dashboard with navigation and clearly marked **coming soon** VPN/profile entries. Existing DNS-only VPN behaviour is unchanged.
- **Not implemented:** WireGuard engine, OpenVPN engine, full traffic tunnel, profile import/storage, DNS integration for tunneled traffic, VPN connection status.
- **Unverified:** build, tests, emulator, physical devices. Do not publish as functional VPN support until implemented and verified.

## Next work
1. Run the remaining physical-device matrix in VERIFICATION; record models, Android versions, APK hash and exact cases.
2. Implement and test client TCP DNS before broad DNS interoperability claims.
3. Set up dedicated stable production signing/version updates without committing keys or silently uninstalling existing builds.
4. Review all chart modes/accessibility, process-death persistence, queue saturation, network changes, VPN revocation/replacement, notification denial and IPv6-only environments.

## Handoff discipline
Read root AGENTS, this file and scoped instructions. Preserve the original browser module. Keep exact source SHA/run/device evidence; implemented, emulator-verified and physical-device-verified are different statuses. Do not replace these limits with “all apps”, “no ads”, “fully working” or invented device results.
