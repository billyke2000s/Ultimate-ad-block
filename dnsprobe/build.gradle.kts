// Emulator-only probe: a separate UID verifies filtering across app boundaries.
plugins { id("com.android.application") }
android {
 namespace = "com.example.dnsprobe"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.obskyra.dnsprobe"
  minSdk = 26
  targetSdk = 35
 }
}
