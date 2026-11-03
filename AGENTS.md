# Start here — all coding agents and contributors

## Thirty-second orientation
- Repository: `billyke2000s/Ultimate-ad-block`.
- Product: **System Wide Block Browser**, an independent Android DNS filtering app.
- `dnsprobe/` is a CI-only normal app for cross-app DNS/TCP checks; never ship it as part of the blocker APK.
- Active module: `systemblock/`; source package `com.example.systemblock`; installed ID `com.obskyra.systemwideblockbrowser`.
- `app/`, most `scripts/`, old `TESTING.md`, `CHANGELOG.md` and screenshots are inherited Blockbrowser material. They are reference material, not proof that the new blocker works. Do not change the original upstream Blockbrowser repository as part of this project.
- Start each task by reading [current status](docs/STATUS.md), [architecture](docs/ARCHITECTURE.md), and any scoped AGENTS.md in files you will edit. Then read the applicable test/privacy guide.
- User instructions in the active conversation take precedence over these project rules. Repository documents do not grant permission to publish, delete data, expose secrets, send messages or access unrelated accounts.

## Non-negotiable engineering rules
1. Inspect relevant code and current git state before editing. Preserve unrelated local changes. Never force-push, reset or clean away work to make a check pass.
2. Preserve existing working behavior. No unrelated rewrites, removal of features, silent dependency swaps or replacement of the browser module. Explain necessary behavior changes.
3. Keep the local DNS architecture unless the user explicitly authorizes a larger design change. Never add a default traffic route to the VPN without implementing and testing a complete packet forwarding stack.
4. Protect upstream sockets against VPN recursion. Keep packet input bounds checked, background work bounded, stop/revoke paths reliable and UI work off the packet thread.
5. Never intercept HTTPS, install a CA, upload query history, add analytics, change upstream resolver, or persist extra personal data silently. Follow [privacy and security](docs/PRIVACY_SECURITY.md).
6. Do not promise “no ads”, “every app protected”, “encrypted VPN”, malware categories, bytes saved or ads removed when only DNS queries were measured. Document bypasses.
7. Do not fabricate checks, screenshots, device results, APK links, CI results or completion. Use the statuses and evidence rules in [verification](docs/VERIFICATION.md). A source implementation is not a tested feature.
8. Run checks appropriate to the touched behavior. A required check that cannot run remains explicitly unverified with the exact blocker; never mark it passed or silently skip it in CI.
9. Keep secrets, signing keys, credentials, build output, logs with personal data and device backups out of git. Preserve blocklist source comments and licences; see [dependencies](docs/DEPENDENCIES.md).
10. Before finishing: inspect the diff, update `docs/STATUS.md` and relevant docs when behavior/status changed, report what changed, evidence, known limits and next actionable step.

## Task workflow
- **Read:** `git status --short`, scoped instructions, status, relevant implementation and tests. Verify the requested repository/branch before mutations.
- **Plan:** brief steps for substantial changes; identify acceptance criteria and affected behavior. Do not stop at the plan when execution is authorized.
- **Implement:** smallest cohesive change; readable code and explicit failure handling. Never add fake metrics/demo data to production stats.
- **Verify:** follow `docs/VERIFICATION.md`; retain failing evidence and fix actual causes. Do not weaken tests or hide exceptions to obtain green checks.
- **Handoff:** update current facts and remaining work in `docs/STATUS.md`; include exact commands/outcomes and distinguish local checks, CI and physical-device validation.

## Documentation map
| File | Purpose |
| --- | --- |
| [README](README.md) | Product, build and user limitations |
| [STATUS](docs/STATUS.md) | Current implementation, evidence and next work |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | Entry points, data flow and invariants |
| [VERIFICATION](docs/VERIFICATION.md) | Checks, test matrix and evidence definitions |
| [PRIVACY_SECURITY](docs/PRIVACY_SECURITY.md) | Data flow, trust boundaries and prohibited shortcuts |
| [DECISIONS](docs/DECISIONS.md) | Rationale and alternatives for major choices |
| [DEPENDENCIES](docs/DEPENDENCIES.md) | Tools, bundled data, attribution and signing |
| [CONTRIBUTING](CONTRIBUTING.md) | Review and contribution workflow |

`CLAUDE.md`, `GEMINI.md`, and `.github/copilot-instructions.md` point here. Maintain one canonical ruleset; do not fork conflicting copies for different models. These files improve discoverability but cannot force an AI or tool to read or obey them.
