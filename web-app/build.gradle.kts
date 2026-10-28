// The browser version of the app (docs/WEB_TRACK.md): the same Clean UI screens from
// :clean-ui, compiled to WebAssembly, with a TrainerGateway that lives in the browser.
// Build:  ./gradlew :web-app:wasmJsBrowserDistribution  -> web-app/build/dist/wasmJs/productionExecutable
// Run:    ./gradlew :web-app:wasmJsBrowserDevelopmentRun
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Pure-Kotlin files the Android app owns and the web needs as-is (exercise regions, 1RM test
// anchors). Compiled from the app's own copy, so there is one source for both.
val appShared = tasks.register<Sync>("syncAppSharedSources") {
    from(rootProject.file("app/src/main/java")) {
        include(
            "com/example/vitruvianredux/domain/model/ExerciseBucket.kt",
            "com/example/vitruvianredux/data/baseline/AnchorExerciseConfig.kt"
        )
    }
    into(layout.buildDirectory.dir("generated/appShared"))
}

// The exercise library and 1RM map the app bundles, served next to the web app.
val appAssets = tasks.register<Sync>("syncAppAssets") {
    from(rootProject.file("app/src/main/assets/exercise_dump.json"))
    from(rootProject.file("clean-ui/src/androidMain/assets/one_rep_max_map.json"))
    into(layout.buildDirectory.dir("generated/appAssets"))
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "vitruvian.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            kotlin.srcDir(appShared)
            resources.srcDir(appAssets)
            dependencies {
                implementation(project(":clean-ui"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.navigation.compose)
            }
        }
    }
}
