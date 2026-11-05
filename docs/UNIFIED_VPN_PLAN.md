# Unified VPN support — implementation specification

Status: **Design only; WireGuard/OpenVPN are not yet implemented or verified.** This document must not be used as evidence of working VPN functionality.

## Goal
Keep one Android `VpnService` owned by AdBlock. Offer three modes: DNS-only AdBlock (existing behaviour), WireGuard + AdBlock, and OpenVPN + AdBlock. The menu is opened from a hamburger icon; the existing Overview / Activity / Settings navigation remains intact.

## UI
- Hamburger drawer: **Protection**, **VPN profiles**, **Import profile**, **Connection settings**, **Privacy & diagnostics**.
- Protection screen: mode selector (AdBlock only / WireGuard / OpenVPN), connect/disconnect, VPN status, tunnel endpoint and duration, independent **Block domains** switch.
- Profile import: Android Storage Access Framework `.conf` WireGuard and `.ovpn` OpenVPN files; validate before saving, show profile name, allow edit/delete, and confirm replacing an active profile.
- No real private keys, credentials, server IPs or query logs in screenshots, notifications, analytics or exported diagnostics.
- Explain that VPN mode requires a configured external VPN server/provider; installing this app alone does not create a remote VPN.

## Architecture
- Refactor `BlockVpn` into one service/controller for mode, consent, lifecycle and protected underlying transport sockets.
- **Do not** launch a second `VpnService` (including a library's own service).
- DNS-only mode preserves the existing DNS-only route `10.111.0.2/32`, encrypted Cloudflare upstream and existing query counters.
- VPN modes establish a complete IPv4 and IPv6 packet forwarding tunnel using the selected protocol engine, with routes, MTU, DNS servers, endpoint socket protection, network handover and teardown. Full routes (`0.0.0.0/0`, `::/0`) are permitted **only when the full tunnel is implemented and tested**.
- Integrate a reviewed WireGuard backend/tunnel implementation and a separately reviewed OpenVPN engine with compatible Android licensing and native ABI packaging. A configuration parser or menu alone is not VPN support.
- Funnel DNS packets through existing `DomainRules` before tunnelling allowed DNS. Support UDP and TCP DNS and account for IPv6. Define explicit behaviour for DNS-over-TLS, DNS-over-HTTPS, Android Private DNS and split-tunnel routes; never claim universal blocking of encrypted application DNS.
- Protect protocol transport sockets using `VpnService.protect()` before connecting to prevent recursion. Handle rekey, reconnect, captive portals, network changes and IPv6 without bypassing user policy.
- Keep allowed DNS resolution consistent with profile DNS settings and leak policy; do not silently override a provider's resolver with Cloudflare. Expose an explicit DNS policy choice.
- In VPN mode, a disabled block switch passes DNS without filtering while retaining the tunnel; disconnecting the remote tunnel can return to DNS-only protection if selected.
- Default to fail-closed for an unexpected VPN tunnel drop when VPN mode is selected, with clearly labelled user-controlled fallback. Avoid claiming Android lockdown support until tested.

## Security and persistence
- Import profile files via document picker; store secrets in app-private encrypted storage with Android Keystore-backed keys where available; never commit example real credentials.
- OpenVPN credential prompts and certificate verification must be handled securely; do not accept invalid certificates or insecure defaults.
- Support WireGuard allowed IPs, peers, endpoint DNS, keepalive and key management; OpenVPN auth modes, certificates and TLS settings must be validated against supported features.
- Keep existing settings, stats and allowlist across upgrades. Include migrations and rollback tests.

## Acceptance tests (required before marking complete)
1. Android 8 and Android 15: existing DNS-only mode continues to block, allow, log and preserve ordinary internet access.
2. WireGuard profile connects to a controlled test endpoint; confirm public egress IP, IPv4/IPv6, DNS, throughput, disconnect/reconnect, roaming and no traffic leaks.
3. OpenVPN profile connects to a controlled test endpoint with certificate validation; same checks, plus password/certificate profile cases.
4. In both VPN modes, blocked domains fail and allowed domains resolve through the intended DNS policy; toggling blocking does not drop VPN.
5. Verify VPN cannot connect without Android consent; revoked consent stops all traffic and clears tunnel resources.
6. Test profile import errors, malformed files, bad credentials, network changes, sleep/wake, process death and always-on behaviour.
7. Confirm notifications and drawer status match real service state; tests must not simulate a successful VPN connection without a real endpoint.
8. CI compiles all supported ABIs, runs unit/instrumentation tests and produces reproducible debug APKs; physical-device tests remain separately labelled.

## Delivery sequence
1. Inspect current service/UI and establish a baseline CI run.
2. Add drawer and non-secret profile metadata/storage with import validation.
3. Extract reusable DNS filtering from the DNS-only service; test unchanged mode.
4. Integrate WireGuard packet forwarding into the same service and verify real endpoints.
5. Integrate OpenVPN packet forwarding and verify real endpoints.
6. Add DNS policy, failure behaviour, migration and full matrix tests.
7. Update README, STATUS, ARCHITECTURE, PRIVACY_SECURITY, DEPENDENCIES, release notes and screenshots with **only verified claims**.

Do not release as working WireGuard/OpenVPN support until both engines pass the above tests.
