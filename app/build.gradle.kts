plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.vitruvianredux"
    compileSdk = 36

    defaultConfig {
        // Its own ID (not the upstream app's), so Cable installs next to it instead of over it.
        // The Kotlin packages keep the old name; only the installed app ID changed.
        applicationId = "nl.nielsnotsmurf.cable"
        minSdk = 26
        targetSdk = 36
        // CI release builds pass these so every published APK upgrades the last one
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 11
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // CI release builds sign with the private key from the repo's GitHub secrets,
            // so every published APK has the same signature and installs over the last one.
            val ciKeystore = System.getenv("SIGNING_KEYSTORE_PATH")?.let { file(it) }
            // Local builds fall back to the debug keystore, which allows the app to be
            // installed without "invalid package" errors.
            val keystorePath = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (ciKeystore != null && ciKeystore.exists()) {
                storeFile = ciKeystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else if (keystorePath.exists()) {
                storeFile = keystorePath
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                logger.warn("Debug keystore not found at ${keystorePath}. Release builds will be unsigned.")
            }
        }
    }

    buildTypes {
        release {
             isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Product flavors for production and beta builds
    // Note: Both use same applicationId so they replace each other when installed
    flavorDimensions += "version"
    productFlavors {
        create("production") {
            dimension = "version"
            // Production uses the default applicationId
            // No suffix needed - this is the main release
            buildConfigField("String", "BUILD_TYPE_LABEL", "\"Production Release\"")
        }
        create("beta") {
            dimension = "version"
            versionCode = 10  // Higher than existing beta (9) to allow update
            versionName = "0.6.2-beta"
            buildConfigField("String", "BUILD_TYPE_LABEL", "\"Beta\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Clean UI layer (separate module, see clean-ui/build.gradle.kts)
    implementation(project(":clean-ui"))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Compose Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // BLE - Nordic Library
    implementation(libs.nordic.ble)
    implementation(libs.nordic.ble.ktx)

    // BLE - Kable Library
    implementation(libs.kable.core)
    implementation(libs.kable.permissions)

    // Dependency Injection - Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Chart Library - MPAndroidChart
    implementation(libs.mpandroidchart)

    // Vico Charts - Modern Compose charting library (upgraded to stable 2.1.3)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // Compose Charts - For area charts and gradients
    implementation(libs.compose.charts)

    // Note: Charty removed - using Vico Charts for pie/donut charts instead

    // Logging - Timber
    implementation(libs.timber)

    // Accompanist - Permissions
    implementation(libs.accompanist.permissions)

    // Coil - Image Loading
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.truth)
}

