pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libadb-android (in-app wireless pairing with the watch) is only published there
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroupByRegex("""com\.github\.MuntashirAkon.*""") }
        }
    }
}

rootProject.name = "WatchSync"
include(":shared", ":mobile", ":wear")
