// Clean UI: the white, multi-lifter UI layer.
//
// This module must never depend on :app. It only knows the TrainerGateway
// interface; :app provides the implementation (see app/.../cleanui/CleanUiBridge.kt).
// Keeping it that way means upstream Phoenix changes can only ever break the bridge,
// never the screens in here.
//
// Web track (docs/WEB_TRACK.md): this is a Kotlin Multiplatform module. Screens and logic
// live in src/commonMain and are also compiled for the browser (wasmJs, shown by :web-app).
// src/androidMain and src/wasmJsMain only hold the platform pieces behind expect/actual.
// New UI and logic go in commonMain, so the app and the web stay the same.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    // Also unpacks the Skia runtime (skiko) the wasm tests load.
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Compose's Skia runtime only loads in a browser, so the web tests run in headless Chrome.
        browser {
            testTask {
                useKarma { useChromeHeadlessNoSandbox() }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // JetBrains' multiplatform builds of the same Compose and AndroidX libraries. On Android
            // they resolve to the androidx artifacts below, at the versions the app already uses.
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "nl.nielsnotsmurf.cleanui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json for parsing the 1RM map in unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20260814")
}
