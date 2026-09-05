plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gssc.daylog"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gssc.daylog"
        minSdk = 29
        targetSdk = 34
        versionCode = 9
        versionName = "1.8"
    }

    signingConfigs {
        getByName("debug") {
            // A fixed key kept in the repo, so every build signs the same way
            // and Android will install one APK over another.
            val ks = rootProject.file("app/daylog.keystore")
            if (ks.exists()) {
                storeFile = ks
                storeType = "PKCS12"
                storePassword = "daylog"
                keyAlias = "daylog"
                keyPassword = "daylog"
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
