# AdBlock 0.5.0

The app is now named AdBlock throughout its interface, notification, VPN session and documentation. The README presents it independently, with required upstream data attribution retained.

The bundled list increases from 215,492 to 507,468 unique domain rules (2.35x). It combines StevenBlack unified hosts and HaGeZi Multi PRO, Pop-Up Ads and Threat Intelligence Feeds Medium. Added coverage includes ad/tracker/pop-up and known-threat domains. Duplicate and redundant subdomain rules are removed. Exceptions remain available for broken sites.

The list is now a compressed asset owned by the blocker module; it no longer imports browser project assets. Feed versions, input counts and SHA-256 hashes are bundled for reproducibility. Rule updates ship with releases; the app loads its offline bundle at startup.

The existing application ID, counters, activity history and exception preferences are preserved. Build publication requires bundle checks, unit tests, Android assembly, lint and API 26/35 cross-app DNS/lifecycle/UI integration tests. These do not establish full physical-device coverage or Play Protect clearance.

This is a debug-signed testing preview; compatibility with differently signed installed copies is not guaranteed. Uninstalling erases local settings and history. DNS filtering still occupies the Android VPN slot and cannot remove same-domain ads or intercept encrypted DNS in other apps. Do not enable Android lockdown.
