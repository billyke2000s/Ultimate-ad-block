# System blocker module rules

Read root AGENTS.md and docs/STATUS.md first.

- `Dashboard` is the UI/consent entry; `BlockVpn` owns the VPN lifecycle; `DnsPacket` handles untrusted bytes; `DomainRules` matches suffixes; `Stats` owns saved counters.
- Keep the application ID separate from original Blockbrowser so both apps can coexist.
- VPN consent must come from Android's VpnService.prepare flow. Never auto-accept it or report connected until a tunnel exists.
- Only the virtual DNS address is routed into the tunnel. Ordinary IPv4/IPv6 packets must remain routable. A DNS-only tunnel is incompatible with Android lockdown (“Block connections without VPN”).
- All malformed packet work must be bounded. Validate IP/UDP/DNS lengths before reads, transaction/question association before accepting answers, and parent-domain boundaries in matching.
- Test allowance precedence and exact/suffix matching without lookalike matches. DNS queries are not individual ads.
- Forwarded DNS must never loop into this tunnel. Stop, revocation and initialization failure must close descriptors/sockets, stop workers and remove active status/notification. Test races, not just the happy path.
- Keep domain history and daily history bounded. Do not write preferences once per packet. Do not invent per-app identity from DNS packets.
- Run module unit tests, assemble and lint after code changes. Real device behavior remains unverified until a documented device test is recorded.
