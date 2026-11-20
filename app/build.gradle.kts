plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.enyaqtube"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.enyaqtube"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Fester Schlüssel, damit Updates über eine bestehende Installation
    // installiert werden können (Login bleibt erhalten).
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/enyaqtube.jks")
            storePassword = "enyaqtube"
            keyAlias = "enyaqtube"
            keyPassword = "enyaqtube"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
