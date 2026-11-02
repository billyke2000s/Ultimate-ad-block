A testing preview of the independent Android DNS blocker, with encrypted upstream DNS and reliability fixes.

- Allowed lookups use reusable, certificate-verified Cloudflare DNS-over-HTTPS connections; no plaintext fallback. Networks blocking port 443 can prevent allowed lookups.
- Tunnel streams own separate descriptors, and TLS cleanup runs off the Android main thread.
- Tunnel creation and shutdown share a lifecycle lock; stop/revoke closes tunnel, worker pool and upstream connections. Dashboard Stop now explicitly tears down the tunnel rather than waiting for a bound service to be destroyed.
- Android 8 trusts the verified SSL.com Root Certification Authority ECC certificate for the resolver hostname only, retaining full TLS verification.
- Corrected EDNS response validation, synthetic error framing, malformed packet bounds and oversized UDP answer handling.
- Kept bounded statistics on reload, added readable day labels/chart accessibility text, and disabled unsupported Android always-on mode.
- Includes source attribution and bundled list licence texts.

Publication is gated on unit tests, Android assembly, lint, and emulator integration tests on Android 8 (API 26) and Android 15 (API 35). Emulator tests exercise actual blocked/allowed DNS and repeated service start/stop. Physical phones, network transitions, notification permission denial and visual/accessibility review still need testing.

This APK is debug-signed for testing. It has no stable production signing/update guarantee; replacing an older testing APK may require uninstalling it, which erases its local statistics/settings. Do not uninstall an existing copy unless you accept that loss.

Known limits: occupies Android's VPN slot; not a remote privacy VPN; no same-domain ad removal or interception of app-specific encrypted DNS. Android Private DNS can bypass filtering. Client TCP DNS is not yet supported; oversized UDP replies signal truncation, so clients needing TCP may fail. Do not enable "Block connections without VPN". Counters measure DNS queries, not individual ads or bandwidth saved. Buffered recent statistics can be lost after abrupt process death.

The source and bundled blocklist are included in this repository. See `systemblock/src/main/assets/licenses/` and the blocklist source header for attribution/licences.

DNS-only routing uses Android's `allowBypass()` option: default DNS still uses the virtual resolver, while apps may deliberately select another network. This is not an enforcement/lockdown VPN. The runtime tests require automatic IPv4/IPv6 DNS API queries, ordinary app name resolution and ordinary TCP connectivity as well as an increased local blocked counter.
