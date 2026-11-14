# Gradle (Kotlin DSL) for the Kotlin + Compose Pleos app

Versions of AGP/compileSdk: use what the current Android Studio wizard generates; only the Pleos-specific parts below are fixed by the docs.

`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
rootProject.name = "pleos"
include(":app")
```

`app/build.gradle.kts` (relevant parts)
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ai.dejavroom.app"
    defaultConfig {
        minSdk = 28
        // targetSdk/compileSdk: wizard default (emulator image is API 34)
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.pleos.vehicle)   // 2.3.2; full catalog: skill android-app-architecture → references/build-and-test.md
}
```

Gradle itself runs on JDK 17 or 21 (`org.gradle.java.home` or Android Studio's Gradle JDK setting). The app's JVM target stays 17 even when the server uses 21/25.
