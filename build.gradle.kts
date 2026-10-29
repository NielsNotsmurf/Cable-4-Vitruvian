// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Security: lift vulnerable libraries that AGP, Kotlin and Hilt pull onto the build classpath
// (Dependabot alerts). Build-time only; none of these end up in the APK or the web app.
buildscript {
    dependencies {
        classpath(platform("io.netty:netty-bom:4.1.139.Final"))
        constraints {
            classpath("com.google.guava:guava:33.7.2-jre")
            classpath("org.bouncycastle:bcprov-jdk18on:1.85")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.85")
            classpath("org.bouncycastle:bcutil-jdk18on:1.85")
            classpath("org.apache.commons:commons-compress:1.28.0")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// Workaround for JavaPoet version conflict with Hilt
allprojects {
    configurations.all {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
            // Security: Guava older than 33.7.2 has a known vulnerability (Dependabot alert).
            eachDependency {
                if (requested.group == "com.google.guava" && requested.name == "guava" &&
                    requested.version?.startsWith("33.7.2") != true
                ) {
                    val flavor = if (requested.version?.endsWith("-jre") == true) "jre" else "android"
                    useVersion("33.7.2-$flavor")
                    because("security fix")
                }
            }
        }
    }
}