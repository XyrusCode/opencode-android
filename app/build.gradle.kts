plugins {
    id("com.android.application") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.6.10" apply false
    id("com.squareup.suitegrade") version "1.2.1" apply false
}

// Project configuration
project.ext {
    composeBom = "1.6.0"
}

android {
    namespace = "com.xyruscode.opencode"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.xyruscode.opencode"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        // WebSocket library uses Java 8 features
        compileOptions {
            sourceCompatibility JavaVersion.VERSION_1_8
            targetCompatibility JavaVersion.VERSION_1_8
        }

        kotlinOptions {
            jvm = "11"
            freeCompilerArgs = [
                "-Xplugin:inline-property-copy",
                "-Xplugin:kotlinify"
            ]
        }
    }

    buildTypes {
        release {
            signingConfig = project.signingConfigs.release
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.0"
    }

    kotlinExtensions {
        compose = "androidx.compose:compose-botlin:1.6.0-rc01"
    }
}

dependencies {
    // Android core
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.room:room-ktx:2.5.0")
    annotationProcessor("androidx.room:room-compiler:2.5.0")

    // Material3 UI
    implementation("androidx.material3:material3:1.2.0")

    // WebSocket client (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.8.9")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit:junit-extensions:1.1.3")
    testImplementation("androidx.test:ext:1.4.0")

    // Androidx test
    androidTestImplementation("androidx.test:ruint:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test:core:1.4.0")
}