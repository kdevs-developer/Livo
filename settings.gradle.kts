pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        kotlin("kapt") version "2.2.20"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Livo"
include(":app")
