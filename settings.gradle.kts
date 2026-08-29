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
        maven("https://s3.amazonaws.com/android.truex.com/tar/prod/maven")
    }
}

rootProject.name = "TrueXAndroidExamples"
include(":app")

