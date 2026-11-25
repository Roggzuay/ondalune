import java.util.Properties

// Google Play builds pass -Pondalune.playStore=true: Play delivers updates, so no GitHub update
// check or APK installer, and none of the permissions Play only grants to other kinds of apps.
val playStore: Boolean = providers.gradleProperty("ondalune.playStore").orNull?.toBoolean() ?: false

// F-Droid builds pass -Pondalune.selfUpdate=false: no GitHub update check and no APK installer.
val selfUpdate: Boolean = !playStore &&
  (providers.gradleProperty("ondalune.selfUpdate").orNull?.toBoolean() ?: true)

// Google Cast needs Google's own Cast SDK and Play Services, neither of which a reproducible
// F-Droid build can carry. Cast rides with the GitHub build, which is the same build that carries
// the updater, so -Pondalune.selfUpdate=false turns both off together. -Pondalune.cast= overrides
// that either way. Everything behind it is guarded by BuildConfig.CAST and lives in src/cast or
// src/noCast, so the app is whole with or without it.
val cast: Boolean = providers.gradleProperty("ondalune.cast").orNull?.toBoolean() ?: selfUpdate

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
    // Bumped in each release commit. F-Droid reads these two lines as written (no Gradle code runs).
    versionCode = 468200
    versionName = "2.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("boolean", "SELF_UPDATE", selfUpdate.toString())
    buildConfigField("boolean", "PLAY_STORE", playStore.toString())
    buildConfigField("boolean", "CAST", cast.toString())
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
    // Test builds that install next to the release instead of updating it: own package and
    // launcher name ("Ondalune Preview", src/preview/res), release-optimized, debug-signed.
    create("preview") {
      initWith(getByName("release"))
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-preview"
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += "release"
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
  // Exported Room schemas, so migration tests (Robolectric reads the debug assets) can build each old
  // version of the database. Debug builds only; release builds never carry them.
  sourceSets {
    getByName("debug").assets.srcDir("$projectDir/schemas")
    // One of these two is always compiled in, and they define the same names, so the rest of the
    // app never asks whether this build can cast.
    getByName("main").kotlin.srcDir(if (cast) "src/cast/java" else "src/noCast/java")
    when {
      playStore -> getByName("release").manifest.srcFile("src/playStore/AndroidManifest.xml")
      !selfUpdate -> getByName("release").manifest.srcFile("src/noSelfUpdate/AndroidManifest.xml")
    }
  }
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
  if (cast) {
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)
  }
  implementation(libs.androidx.work.runtime.ktx)
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
  testImplementation(libs.androidx.room.testing)
  // Test only: a local HTTP server, so the ListenBrainz tests never touch the network.
  testImplementation(libs.okhttp.mockwebserver)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
