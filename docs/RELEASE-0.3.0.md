# System Wide Block Browser 0.3.0 — dashboard redesign

- Rebuilt the native dashboard with a forest-green palette, shield identity, status card and one state-aware start/pause action.
- Added compact blocked/allowed/failed query cards and pill selectors for pie, ring, domains and weekly charts.
- Moved Android settings, statistics reset and detailed help into dialogs; kept allowed-domain management on the dashboard.
- Added system-bar inset handling, saved chart selection on recreation, accessible chart summaries and a custom launcher icon.
- Removed the prominent README warning box at the user's request. The installation report remains documented in docs/STATUS.md; this release does not establish Play Protect clearance.

The release workflow requires unit tests, Android assembly, lint and API 26/35 emulator checks before publication. UI checks exercise the active/stopped action and every chart selector; emulator screenshots are retained as evidence. Full physical-device and accessibility verification remains pending.

This APK is debug-signed for testing. It may not update a differently signed installed copy. Uninstalling erases settings/statistics; do not uninstall unless you accept that loss.

Filtering behavior is unchanged from 0.2.0: local DNS-only VPN, certificate-verified Cloudflare DNS over HTTPS, no plaintext fallback. It occupies Android's VPN slot. Private DNS and app-specific encrypted DNS can bypass it. Same-domain ads are not removed; client TCP DNS is not implemented. Do not enable Android's “Block connections without VPN”. Counters represent DNS queries, not ads or bytes saved.
