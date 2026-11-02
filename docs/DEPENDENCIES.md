# Dependencies, attribution and distribution

## Build
- Android Gradle Plugin 8.7.3 (root build file).
- Gradle 8.9 (CI setup); Java 17 JDK; Android SDK compile/target 35, minimum 26.
- systemblock runtime uses Android/Java APIs, with no third-party chart/VPN library.
- JUnit 4.13.2 for module unit tests.
- Original `app` module keeps its inherited dependencies; do not confuse these with systemblock requirements.

## Bundled data
`app/src/main/assets/blocklist.txt` is copied from Blockbrowser and shared as systemblock assets. Its source header identifies StevenBlack unified hosts (MIT) and HaGeZi Multi PRO (GPL-3.0), including upstream URLs and generation/version comments. Preserve the header. Review upstream licence obligations and include required texts/notices before distributing a release; this document is not a completed legal/licensing audit. Do not invent a licence for inherited source if the upstream repository does not grant one.

## Network dependency
Current allowed-DNS resolver: Cloudflare 1.1.1.1 over UDP 53. Do not change it silently. Never describe this as encrypted DNS. The committed list enables offline rule loading; no remote blocklist download is needed at app start.

## Build and signing
GitHub Actions uploads a debug APK artifact. Debug signing is suitable for initial testing but is not a stable production update strategy. Before release, configure a dedicated stable signing key outside git, confirm update compatibility, document release versioning and preserve existing user data. Do not reuse or expose another project's private signing credentials.

## Inherited workflows and scripts
Original browser workflows have `.disabled` suffixes and do not run. They are historical reference. The active workflow is `.github/workflows/system-block.yml`; inherited updater/scripts still target Blockbrowser and must not be treated as the new app's update infrastructure.
