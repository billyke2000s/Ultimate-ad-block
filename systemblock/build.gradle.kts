plugins { id("com.android.application") }
android {
 namespace = "com.example.systemblock"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.obskyra.systemwideblockbrowser"
  minSdk = 26
  targetSdk = 35
  versionCode = 5
  versionName = "0.5.0"
  testInstrumentationRunner = "com.example.systemblock.SmokeInstrumentation"
 }
}
dependencies {
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
}
