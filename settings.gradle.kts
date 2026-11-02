pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "SystemWideBlockBrowser"
include(":app")

include(":systemblock")

// Test-only APK for cross-app DNS integration checks.
include(":dnsprobe")
