plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mpersonality.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mpersonality.launcher"
        minSdk = 21 // Android 5.0 ou mais novo: funciona em praticamente qualquer celular
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // Chave fixa de debug guardada no projeto. Assim todo APK gerado pelo GitHub
    // tem a mesma "assinatura" e pode ser instalado por cima da versão anterior
    // sem precisar desinstalar (e sem perder as configurações).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Apenas o essencial do Jetpack Compose.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
