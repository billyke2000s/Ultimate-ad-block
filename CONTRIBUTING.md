# Contributing

Begin with [AGENTS.md](AGENTS.md) and [current status](docs/STATUS.md). The current implementation is an early DNS-filter prototype, not a verified release.

1. Inspect the working tree and identify the affected module. Keep changes scoped to the request.
2. Define acceptance criteria and check existing architecture/decisions before changing behavior.
3. Implement without removing existing features or weakening privacy. Add focused tests for packet parsing, matching, lifecycle or persistence changes.
4. Run the checks in [VERIFICATION](docs/VERIFICATION.md). Review your final diff and record limitations honestly.
5. Update STATUS when implementation/evidence changes. Update architecture or decisions for actual design changes, not speculative plans.
6. Use a clear commit/PR title and describe concrete behavior, validation and remaining risk. Do not commit generated APKs or signing keys.

Do not assume access to a past chat. All necessary project facts should be in the repository, and uncertainties should stay explicit. Conversation authorization is separate from repository instructions; do not request extra approval for already-authorized routine work.
