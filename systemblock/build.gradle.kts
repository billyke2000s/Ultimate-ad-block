plugins { id("com.android.application") }
android {
 namespace = "com.example.systemblock"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.obskyra.systemwideblockbrowser"
  minSdk = 26
  targetSdk = 35
  versionCode = 1
  versionName = "0.1.0"
 }
 sourceSets.getByName("main").assets.srcDir("../app/src/main/assets")
}
dependencies { testImplementation("junit:junit:4.13.2") }
