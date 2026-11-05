# AdBlock 0.6.0 — experimental VPN integration

Testing build from `feature/unified-vpn-protocols`, draft PR #1. This is not a
production-readiness claim. Consult STATUS for exact CI/device evidence.

- In-process WireGuard and OpenVPN behind the existing single BlockVpn service.
- Shared raw packet forwarding and IPv4/IPv6 UDP/TCP DNS filtering in VPN mode.
- Secure multi-profile import, selection, editing, deletion and connection controls.
- Profile/server DNS stays inside the selected tunnel; blocking toggles independently.
- Foreground status, authenticated connection state, duration and measured IP bytes.
- Existing app ID, 507,468 rules, allowlists, counters, charts and activity retained.

Import `.conf`, embedded-certificate `.ovpn`, or a ZIP with relative certificates.
OpenVPN accepts username/password, PEM client certificates and encrypted private
keys. Server CA/role verification and TLS >=1.2 remain enforced. Unsupported
scripts, external PKI, compression and legacy verification/cipher opt-ins fail.

Remaining acceptance work includes real provider egress/public-IP changes,
physical Wi-Fi/mobile switching, Android process-death/lockdown and permission
revocation. Host WireGuard protocol tests do not replace Android device evidence.
DNS-only client TCP remains unsupported. Private DNS/app-specific DoH/DoT bypass
filtering. Split-route exclusions intentionally use direct traffic.

Unexpected remote failure retains routes while the service lives. To enforce
protection after process death, use Android Always-on VPN + Block connections
without VPN with a remote profile selected. Never use lockdown in DNS-only mode.

This APK uses testing/debug signing. Compatibility with a previously installed
release signature has not been established. No old releases are changed/deleted,
and no uninstall/migration that erases existing settings is performed automatically.
