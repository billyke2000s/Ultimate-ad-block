# System Wide Block Browser 0.4.0

- Compact overview with fixed Overview / Activity / Settings navigation.
- All four chart selectors fit across the screen; smaller charts replace the oversized cards.
- Searchable actual domain activity with All / Blocked / Allowed / Failed filters, timestamps and an allow-domain action.
- Separate settings page and dialogs styled to match the app.

Activity stores the newest 200 DNS queries locally, including allowed/failed names, outcomes and times. This is new in 0.4.0 and responds to the request to inspect domain outcomes. Settings can turn off new activity recording or clear its history independently of totals. Older totals and exceptions remain intact; old allowed queries cannot be recovered.

Publication requires unit tests, Android assembly, lint and API 26/35 integration tests. UI checks cover overview visibility, all chart modes, outcome filtering and search. Isolated tests cover history retention, reload, disabling recording and clearing while retaining totals. The full physical-device/accessibility matrix remains pending.

Debug-signed testing APK. Different build signing certificates can prevent an in-place update; uninstalling deletes local settings/history. The previously reported Play Protect flag has not been cleared by this UI change.

DNS behavior remains unchanged: one VPN slot, Cloudflare DNS over HTTPS, no HTTPS interception, Private DNS/app-specific encrypted DNS bypasses, no same-domain ad removal and no client TCP DNS. Do not enable Android lockdown. Counters represent DNS queries.
