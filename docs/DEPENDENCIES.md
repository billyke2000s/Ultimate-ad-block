# Dependencies, attribution and distribution

## Build
- Android Gradle Plugin 8.7.3 (root build file).
- Gradle 8.9 (CI setup); Java 17 JDK; Android SDK compile/target 35, minimum 26.
- systemblock uses Android/Java APIs for charts/VPN, and OkHttp 4.12.0 (Apache-2.0) for authenticated HTTPS, connection reuse, cancellation, framing and bounded timeouts. Kotlin/Okio transitive dependencies retain upstream notices.
- JUnit 4.13.2 for module unit tests.
- Original `app` module keeps its inherited dependencies; do not confuse these with systemblock requirements.

## Bundled data
`systemblock/src/main/assets/blocklist.txt.gz` is AdBlock's independent bundled list. It combines StevenBlack unified hosts (MIT) and HaGeZi Multi PRO, Pop-Up Ads and Threat Intelligence Feeds Medium (GPL-3.0). The compressed header preserves source attribution; `blocklist-sources.json` records snapshot versions, SHA-256 hashes and input counts. Licence texts are bundled in `systemblock/src/main/assets/licenses/`. Run the updater from the repo root, then the offline checker. The app streams decompression into its rule parser; no external project asset directory is part of this module's source set.

## Network dependency
Current allowed-DNS resolver: Cloudflare 1.1.1.1 (with 1.0.0.1 bootstrap fallback) over DNS over HTTPS on TCP 443, verified against cloudflare-dns.com with system trust and endpoint identification. No plaintext fallback. The protocol changed in 0.2.0 to encrypt allowed lookups; the resolver operator is unchanged. The committed list enables offline rule loading; no remote blocklist download is needed at app start.

## Build and signing
GitHub Actions uploads a debug APK artifact. Debug signing is suitable for initial testing but is not a stable production update strategy. Before release, configure a dedicated stable signing key outside git, confirm update compatibility, document release versioning and preserve existing user data. Do not reuse or expose another project's private signing credentials.

## Inherited workflows and scripts
Original browser workflows have `.disabled` suffixes and do not run. They are historical reference. The active workflow is `.github/workflows/system-block.yml`; `scripts/update-blocklist.sh` now refreshes AdBlock’s own bundle; other inherited scripts are reference material.


## Experimental native VPN build

Use `git clone --recurse-submodules` (or `git submodule update --init --recursive`),
Go 1.25.3, NDK 27.2.12479018 and CMake 3.22.1. Gradle invokes scripts/build-go.sh,
then CMake builds the JNI/OpenVPN engine for arm64-v8a, armeabi-v7a and x86_64.
Native libraries use 16 KiB ELF alignment. API 26 and application ID are preserved.

| Component | Exact pin | Licence |
| --- | --- | --- |
| WireGuard Go | v0.0.0-20250521234502-f333402bd9cb | MIT |
| WireGuard Android parser | 1.0.20260102 | Apache-2.0 |
| OpenVPN 3 | 0b27d9df493db8bc9dc459a2b1cb6b67037742b2 | MPL-2.0 selected |
| gVisor | 39ed1f5ac29c | Apache-2.0 |
| Mbed TLS | 3.6.5 | Apache-2.0 selected |
| ASIO / fmt / LZ4 | 1.30.2 / 11.2.0 / 1.10.0 | Boost-1.0 / MIT / BSD-2-Clause |

Go module checksums and CMake archive SHA-256 hashes pin downloaded dependencies.
Full licences/notices are bundled in assets/licenses, with an immutable upstream
OpenVPN source URL and submodule for corresponding source. No crypto library is
replaced by an unverified binary download. Debug-signature compatibility with older
release APKs is still not established; no production signing key is available here.
