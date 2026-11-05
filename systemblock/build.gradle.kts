plugins { id("com.android.application") }
android {
 namespace = "com.example.systemblock"
 compileSdk = 35
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
 defaultConfig {
  applicationId = "com.obskyra.systemwideblockbrowser"
  minSdk = 26
  targetSdk = 35
  versionCode = 6
  versionName = "0.6.0-experimental"
  ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
  externalNativeBuild { cmake { cppFlags += "-std=c++20" } }
  testInstrumentationRunner = "com.example.systemblock.SmokeInstrumentation"
 }
 packaging { jniLibs.excludes += setOf("**/libwg-go.so", "**/libwg-quick.so", "**/libwg.so") }
 ndkVersion = "27.2.12479018"
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
dependencies {
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
 implementation("com.wireguard.android:tunnel:1.0.20260102")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
}

val buildGo by tasks.registering(Exec::class) {
 environment("ANDROID_NDK_HOME", android.sdkDirectory.resolve("ndk/${android.ndkVersion}").absolutePath)
 commandLine("bash", rootProject.file("scripts/build-go.sh").absolutePath)
 inputs.dir(rootProject.file("native/go"))
 inputs.file(rootProject.file("scripts/build-go.sh"))
 outputs.dir(layout.buildDirectory.dir("go"))
}
tasks.configureEach {
 if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) dependsOn(buildGo)
}
