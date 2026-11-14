# Build setup and testing

## Known-good baseline

The official Pleos sample builds with JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compileSdk/targetSdk 35. Start there, then take Android Studio's upgrade suggestions one at a time, verifying the build after each. Library versions: latest stable at project creation, pinned in the catalog; don't use `+` or `latest.release`.

## `gradle/libs.versions.toml` (shape)

```toml
[versions]
agp = "8.8.2"
kotlin = "2.0.0"
ksp = "<matching kotlin>"
composeBom = "<latest stable>"
hilt = "<latest stable>"
pleosVehicle = "2.3.2"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
pleos-vehicle = { group = "ai.pleos.playground", name = "Vehicle", version.ref = "pleosVehicle" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

(Fill unknown versions from the official release pages when the project is created; the Compose compiler plugin version equals the Kotlin version since Kotlin 2.0.)

## `app/build.gradle.kts` essentials

```kotlin
android {
    defaultConfig { minSdk = 28 }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    flavorDimensions += "data"
    productFlavors {
        create("pleos") { dimension = "data" }   // real SDKs
        create("demo") { dimension = "data" }    // Fake repositories for offline demos
    }
}
kotlin { jvmToolchain(17) }
```

Check R8 keep rules for the Pleos SDKs on the first release build (`assemblePleosRelease`) and run it on the emulator before submitting.

## Tests

| Level | Tooling | What |
|---|---|---|
| Unit (JVM) | JUnit4, `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`), Turbine | ViewModels with fake repositories; repository mapping of SDK results/errors |
| UI | `androidx.compose.ui:ui-test-junit4` | Key screens from fixed `UiState` (incl. restricted mode) |
| Device | Emulator + Virtual Console | Real SDK flows, driving restriction toggle, CCP |

```kotlin
@Test fun `shows unsupported when door is not readable`() = runTest {
    val repo = FakeVehicleRepository().apply { door.value = VehicleResult.Unsupported("x") }
    val vm = DoorViewModel(repo)
    vm.uiState.test {
        assertThat(awaitItem().unsupported).isTrue()
    }
}
```

Replace `Dispatchers.Main` with a test dispatcher (`Dispatchers.setMain`) in a JUnit rule. Inject dispatchers so tests never wait on real I/O.

## Verify

`/verify :app:testPleosDebugUnitTest` and `/verify :app:assemblePleosDebug` before claiming a change is done.
