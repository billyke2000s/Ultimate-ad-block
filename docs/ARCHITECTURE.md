# Architecture and code map

## Product boundary
The new app is module `systemblock`. Gradle also retains `app`, the original browser copy. New APKs use `com.obskyra.systemwideblockbrowser`; Java sources use `com.example.systemblock`. The current build workflow targets only systemblock.

## Data flow
1. `Dashboard.java` calls VpnService.prepare and starts `BlockVpn` only after system consent.
2. `BlockVpn.java` loads `DomainRules` from the shared asset `app/src/main/assets/blocklist.txt` plus locally stored exceptions.
3. A local VPN advertises DNS `10.111.0.2`, owns `10.111.0.1`, routes **only 10.111.0.2/32**, and allows the IPv6 family. Regular traffic is not forwarded by this service.
4. The tunnel reader passes IPv4 UDP/53 packets to `DnsPacket`. Other packet types are currently ignored.
5. `DomainRules` checks allow exceptions first, then exact/parent-domain block matches. A blocked query receives NXDOMAIN.
6. A bounded worker pool forwards allowed queries over protected, reusable DNS-over-HTTPS connections to `1.1.1.1:443` (fallback `1.0.0.1:443`), with certificate/hostname verification for `cloudflare-dns.com`. It validates response ID/opcode/question before returning it. Failure sends SERVFAIL with no plaintext fallback. Responses respect a maximum 1232-byte EDNS UDP budget (512 without EDNS); larger responses signal truncation.
7. `Stats` records query outcomes and blocked host/day counters; `Dashboard` draws charts and refreshes approximately every 1.5 seconds while resumed.

## File map
| File | Responsibility |
| --- | --- |
| `systemblock/src/main/AndroidManifest.xml` | Permissions, entry activity, protected VPN service |
| `systemblock/src/main/java/com/example/systemblock/Dashboard.java` | Consent, controls, charts, exceptions and explanations |
| `systemblock/src/main/java/com/example/systemblock/BlockVpn.java` | Tunnel, foreground notification, sockets/workers, shutdown |
| `systemblock/src/main/java/com/example/systemblock/TlsDns.java` | Protected TLS connection, framing, authentication and timeout |
| `systemblock/src/main/java/com/example/systemblock/DnsPacket.java` | IPv4/UDP framing, DNS question/answer checks and replies |
| `systemblock/src/main/java/com/example/systemblock/DomainRules.java` | Bundled rules, allowance precedence and suffix matching |
| `systemblock/src/main/java/com/example/systemblock/Stats.java` | Local preferences and bounded history |
| `systemblock/src/test/java/com/example/systemblock/CoreTest.java` | Four core test methods |
| `.github/workflows/system-block.yml` | Tests, Android build, lint and APK artifact |

## Invariants
- Never add `0.0.0.0/0` or `::/0` to this VPN without a complete traffic forwarding design.
- Domain matching respects dot boundaries: blocking `ads.example` includes `a.ads.example`, not `notads.example`.
- Counts are queries; multiple queries may correspond to one ad or no ad. No per-app attribution exists.
- Allowed queries use protected upstream sockets; all query data is untrusted input.
- The reader, worker pool and sockets must terminate on stop/revoke; initialization and shutdown use a shared lock; automated emulator scenarios exercise repeated stop/start, including startup stops.

## Persistence
`settings` preferences contain a domain exception string. `dns_stats` preferences contain `data`: blocked/allowed/failed totals, up to 200 blocked domain counters, and up to 90 daily counters. Stats are buffered and saved periodically/on lifecycle events. Abrupt process death may lose the latest buffered increments. No query-upload backend is present.

## Unsupported paths
TCP DNS, direct DoH/DoT interception, general packet forwarding, HTTPS content filtering, certificate installation, same-domain ad removal, and simultaneous use of another VPN app. Android Private DNS and app-specific encrypted DNS may bypass filtering. See STATUS before claiming any implementation works on a device.

DNS-only routing uses Android's `allowBypass()` option: default DNS still uses the virtual resolver, while apps may deliberately select another network. This is not an enforcement/lockdown VPN. The runtime tests require automatic IPv4/IPv6 DNS API queries, ordinary app name resolution and ordinary TCP connectivity as well as an increased local blocked counter.
