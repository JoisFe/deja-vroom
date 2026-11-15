import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Road view provider settings (docs/roadview-setup.md).
// config/roadview.yml is the shared base, packaged as the `roadview.yml` asset and read by the app at startup.
// local.properties (or -P...) overrides it per machine; secrets such as the Mapillary token live only there.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun localSetting(name: String): String =
    ((findProperty(name) as String?) ?: localProperties.getProperty(name) ?: "").trim()
fun buildConfigString(value: String) = "\"" + value.replace("\\", "").replace("\"", "") + "\""

val roadviewProviders = setOf("kakao", "mapillary", "demo")

/** Changes whenever web/roadview changes; appended to page URLs so WebView and Pages caches never serve an old page. */
val roadviewPageRevision: String = rootProject.file("web/roadview").walkTopDown()
    .filter { it.isFile }.sortedBy { it.path }
    .fold(MessageDigest.getInstance("SHA-256")) { digest: MessageDigest, file -> digest.apply { update(file.readBytes()) } }
    .digest().joinToString("") { "%02x".format(it) }.take(12)
val roadviewConfigFile = rootProject.file("config/roadview.yml")
val roadviewProviderOverride = localSetting("roadviewProvider")
val roadviewProvider: String = roadviewProviderOverride.ifEmpty {
    roadviewConfigFile.takeIf { it.exists() }?.readLines()
        ?.firstNotNullOfOrNull { Regex("""^provider:\s*["']?([A-Za-z]+)["']?\s*(#.*)?$""").find(it)?.groupValues?.get(1) }
        .orEmpty()
}.lowercase()
require(roadviewProvider in roadviewProviders) {
    "config/roadview.yml의 provider는 ${roadviewProviders.joinToString(" | ")} 중 하나여야 한다 (현재: '$roadviewProvider'). " +
        "local.properties의 roadviewProvider로 덮어쓸 수도 있다. docs/roadview-setup.md 참고"
}

android {
    namespace = "ai.dejavroom.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.dejavroom.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ROADVIEW_PROVIDER_OVERRIDE", buildConfigString(roadviewProviderOverride))
        buildConfigField("String", "ROADVIEW_BASE_URL_OVERRIDE", buildConfigString(localSetting("roadviewBaseUrl")))
        buildConfigField("String", "ROADVIEW_PAGE_REVISION", buildConfigString(roadviewPageRevision))
        buildConfigField("String", "MAPILLARY_CLIENT_TOKEN", buildConfigString(localSetting("mapillaryClientToken")))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets["main"].assets.srcDir(rootProject.file("config"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    // Pleos Connect SDK: the car's own navigation tells the app where the car is (docs/pleos-sdk.md).
    implementation(libs.pleos.navi.helper)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json on the JVM; the Android stub throws in local unit tests.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
