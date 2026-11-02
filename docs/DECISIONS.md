# Engineering decisions

## D001 — independent app/module
Use `systemblock/` with its own application ID. Preserve the browser copy as reference and a source for the shared domain list. This allows both apps to coexist and avoids shipping new behavior as a silent browser replacement.

## D002 — DNS-only local VPN for the first implementation
Use Android VpnService and a virtual DNS /32 route. It provides domain filtering across cooperating apps without root, while avoiding a general traffic forwarding stack. Costs: one VPN slot, encrypted DNS bypasses, no same-domain/cosmetic filtering and no lockdown compatibility. This is not a remote encrypted VPN. Full traffic routing would require an explicit architecture change and runtime verification.

## D003 — shared domain list
Reuse the existing browser asset rather than inventing a second rule source. It includes combined ad/tracker sources; therefore the UI must not invent categories such as malware vs ads. Updating rules requires attribution/provenance and false-positive checks.

## D004 — measured statistics only
Display allowed/blocked/failed DNS queries, top blocked domains and daily blocked-query trend. No individual-ad count, bandwidth savings, per-app ranking or estimated categories. Keep history bounded and device-local.

## D005 — no claimed release yet
CI produces a testing APK artifact. Stable signing/release publication and device acceptance are separate steps; a debug artifact does not prove release readiness.

When changing a decision, append its replacement, rationale and verification requirements. Do not rewrite history to make a new design look previously accepted.
