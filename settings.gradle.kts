pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // PREFER_PROJECT: the Kotlin/Wasm plugin adds the Node.js and Yarn download repositories
    // to the root project for the web tests. No module declares its own, so all others still
    // resolve from the list below.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// No spaces: the Kotlin/Wasm build uses this as the npm package name.
rootProject.name = "vitruvian-project-phoenix"
include(":app")
include(":clean-ui")
