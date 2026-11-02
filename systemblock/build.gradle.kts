plugins { id("com.android.application") }
android {
 namespace = "com.example.systemblock"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.obskyra.systemwideblockbrowser"
  minSdk = 26
  targetSdk = 35
  versionCode = 2
  versionName = "0.2.0"
  testInstrumentationRunner = "com.example.systemblock.SmokeInstrumentation"
 }
 sourceSets.getByName("main").assets.srcDir("../app/src/main/assets")
}
dependencies {
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
}
