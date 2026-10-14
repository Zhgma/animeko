pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}
dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "animecomic-web"
include(":app-language")
