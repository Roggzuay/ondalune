import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * Minutes since 2026-01-01 UTC at build time. A ValueSource is re-read on every build (a plain
 * System call would be frozen by the configuration cache), so each APK gets a higher
 * versionCode and installs as an update instead of a reinstall.
 */
abstract class BuildMinute : ValueSource<Int, ValueSourceParameters.None> {
  override fun obtain(): Int = ((System.currentTimeMillis() / 1000L - 1_767_225_600L) / 60L).toInt()
}

val buildMinute: Int = providers.of(BuildMinute::class) {}.get()
val buildStamp: String = Instant.ofEpochSecond(1_767_225_600L + buildMinute * 60L)
  .atZone(ZoneId.systemDefault())
  .format(DateTimeFormatter.ofPattern("yyyy.MM.dd.HHmm"))

// Tagged releases pin both values (-Pondalune.versionCode / -Pondalune.versionName) so a rebuild
// of the same tag, e.g. by F-Droid, produces the same version.
val pinnedVersionCode: Int? = providers.gradleProperty("ondalune.versionCode").orNull?.toInt()
val pinnedVersionName: String? = providers.gradleProperty("ondalune.versionName").orNull

/**
 * Release signing, never committed. Locally it comes from `keystore.properties` in the project
 * root (git-ignored); in CI from the ONDALUNE_KEYSTORE_* environment variables. Without either,
 * release builds are left unsigned (F-Droid and other rebuilders sign them themselves).
 */
val releaseSigning: Map<String, String>? = run {
  val file = rootProject.file("keystore.properties")
  when {
    file.exists() -> Properties().apply { file.inputStream().use(::load) }
      .let { p -> p.stringPropertyNames().associateWith { p.getProperty(it) } }
    System.getenv("ONDALUNE_KEYSTORE_FILE") != null -> mapOf(
      "storeFile" to System.getenv("ONDALUNE_KEYSTORE_FILE"),
      "storePassword" to System.getenv("ONDALUNE_KEYSTORE_PASSWORD").orEmpty(),
      "keyAlias" to System.getenv("ONDALUNE_KEY_ALIAS").orEmpty(),
      "keyPassword" to System.getenv("ONDALUNE_KEY_PASSWORD").orEmpty()
    )
    else -> null
  }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "io.github.roggzuay.ondalune"
  compileSdk = 37

  defaultConfig {
    applicationId = "io.github.roggzuay.ondalune"
    minSdk = 24
    targetSdk = 36
    versionCode = pinnedVersionCode ?: buildMinute
    versionName = pinnedVersionName ?: buildStamp

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (releaseSigning != null) {
      create("release") {
        storeFile = file(releaseSigning.getValue("storeFile"))
        storePassword = releaseSigning.getValue("storePassword")
        keyAlias = releaseSigning.getValue("keyAlias")
        keyPassword = releaseSigning.getValue("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      // R8-optimized and non-debuggable: debug builds run Compose several times slower.
      isMinifyEnabled = true
      isShrinkResources = true
      isCrunchPngs = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // No Google-encrypted dependency blob in the APK (F-Droid and IzzyOnDroid require this).
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.materialkolor)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  // Installs the bundled baseline profiles (Compose + app) on sideloaded installs.
  implementation("androidx.profileinstaller:profileinstaller:1.4.1")
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation("dev.chrisbanes.haze:haze:1.7.3")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
