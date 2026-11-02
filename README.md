# System Wide Block Browser — Ultimate Ad Block

Separate Android app based on the domain list from [Blockbrowser](https://github.com/billyke2000s/Blockbrowser). The original browser source is preserved in `app/`; the new independent blocker is `systemblock/`, package `com.obskyra.systemwideblockbrowser`.

## For any AI or contributor
Start with [AGENTS.md](AGENTS.md), then [current status and handoff](docs/STATUS.md). The [architecture map](docs/ARCHITECTURE.md) identifies all entry points. See [verification](docs/VERIFICATION.md) before making completion claims.

## Features
- User-approved local VpnService, start/stop controls and foreground notification.
- Bundled 215,000+ ad/tracker domain rules, including subdomains.
- Allowed domain exceptions, applied after restarting protection.
- Pie and doughnut charts of allowed/blocked/failed DNS queries; top-domain bars; seven-day activity line.
- Device-local saved counters, capped domain/daily history and reset control.

## Honest scope
This is a DNS filter, not a promise to remove every ad. It cannot remove same-domain ads (including many YouTube ads), inspect HTTPS content, hide blank ad spaces in other apps, or enforce filtering against apps using encrypted DNS/hard-coded IPs. Android Private DNS may bypass it. It occupies Android's single VPN slot and does not provide a remote privacy tunnel. Allowed DNS is forwarded to Cloudflare 1.1.1.1 over certificate-verified DNS over TLS (port 853), without plaintext fallback. Only the virtual DNS /32 route enters the tunnel; regular IPv4 and IPv6 traffic flows directly. Do **not** enable Android's “Block connections without VPN” setting for this DNS-only design. Client DNS over TCP is not implemented. Large UDP replies signal truncation, so clients needing TCP may fail. See the release notes for tested scenarios and remaining limits.

Counts represent DNS queries, not individual ads or bandwidth saved. Domain statistics retain at most 200 names and 90 days. No analytics/upload backend. Allowed DNS queries are visible to the upstream resolver.

## Build
Java 17, Android SDK 35 and Gradle 8.9:
```
gradle :systemblock:testDebugUnitTest :systemblock:assembleDebug :systemblock:lintDebug
```
GitHub Actions builds an installable debug APK artifact. Debug signing is for testing; configure a stable release key before distributing updates. No APK has yet been verified on a physical device.

## Testing on Android
1. Install the systemblock APK alongside Blockbrowser. Grant the VPN prompt and start protection.
2. With Private DNS disabled, request a blocked domain from another app: expect NXDOMAIN and an increased blocked count. Confirm a normal domain resolves and internet connectivity remains working on Wi-Fi/mobile data.
3. Allow the blocked domain, stop/start and confirm it resolves. Check parent-domain matching does not match lookalike domains.
4. Stop from notification, revoke VPN permission, switch networks, rotate/reopen the dashboard and restart the process. Verify the notification and state agree and counters persist.
5. Check pie/doughnut totals, bars and daily trend, zero-data states and reset. Check IPv6 connectivity. Test with Private DNS on to observe the documented bypass limitation.
6. Confirm another VPN replaces this one and the UI reports stopped. Do not enable lockdown.

## Attribution
The bundled blocklist is retained from Blockbrowser with source comments: StevenBlack hosts (MIT) and HaGeZi Multi PRO (GPL-3.0). Preserve applicable upstream licences/attribution when redistributing; see blocklist header and original repository docs. Browser-specific history remains in CHANGELOG.md and docs/.
