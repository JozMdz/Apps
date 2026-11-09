plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.apps.seleccionarpronunciar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apps.seleccionarpronunciar"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"

        // Solo arquitecturas ARM (teléfonos) para no inflar el APK con las librerías del OCR.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Firmado con la clave de depuración para poder instalarlo directamente.
            // Cámbialo por tu propia clave si vas a publicarlo.
            signingConfig = signingConfigs.getByName("debug")
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
    // Reconocimiento de texto en el dispositivo (sin internet), para PDF e imágenes.
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
