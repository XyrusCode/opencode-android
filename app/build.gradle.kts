import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing.
//
// CI writes keystore.properties from the ANDROID_KEYSTORE_* secrets before the
// build; a developer can drop the same file next to this one to reproduce a
// signed build locally. The key must be identical everywhere or Android treats
// each build as a separate app and in-app updates stop working.
//
// This previously leaned on actions/cache for ~/.android/debug.keystore. Gradle
// resolves that path from ANDROID_SDK_HOME, not $HOME, so the cache never found
// the file, never saved it, and every release quietly shipped with a fresh
// signing identity. Passing the key in explicitly removes the dependency and
// lets the build fail loudly when it is missing.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

val releaseStoreFile = keystoreProps.getProperty("storeFile")
val releaseStorePassword = keystoreProps.getProperty("storePassword")
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")
val releaseKeyPassword = keystoreProps.getProperty("keyPassword")

val hasReleaseKey = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

// Fail CI rather than fall back: an unsigned or wrongly-signed release that
// looks fine is far worse than a build that stops.
if (gradle.startParameter.taskNames.any { it.contains("Release") } && !hasReleaseKey) {
    throw GradleException(
        "No release signing key. Expected keystore.properties at " +
            "${keystorePropsFile.absolutePath} (CI writes it from the " +
            "ANDROID_KEYSTORE_BASE64 / ANDROID_KEYSTORE_PASSWORD secrets)."
    )
}

android {
    namespace = "com.xyrus.opencode"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xyrus.opencode"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Signed with one long-lived release key so the app can update over
            // its own previous install. Earlier releases were signed with the
            // ephemeral CI debug key, so Android saw a different app each time.
            // Falls back to the debug key for local unit-test runs, which do not
            // match the guard above. CI always supplies the real key, so any
            // release task without one fails before reaching this line.
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // android.jar ships org.json as a stub that throws in unit tests. The real
    // implementation on the test classpath makes SessionMessage testable
    // without dragging in Robolectric.
    testImplementation("org.json:json:20231013")
}
