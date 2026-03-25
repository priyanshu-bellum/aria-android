pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://alphacephei.com/maven/")   // Vosk
        maven("https://jitpack.io")                // LiveKit
    }
}

rootProject.name = "aria-android"
include(":app")
