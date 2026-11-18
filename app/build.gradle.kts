plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A chave do APK release NÃO fica no projeto. O GitHub Actions a recebe dos
// "Secrets" do repositório e informa o caminho e a senha por estas variáveis.
val releaseKeystorePath: String = System.getenv("MPERSONALITY_KEYSTORE_FILE") ?: ""
val releaseKeystorePassword: String = System.getenv("MPERSONALITY_KEYSTORE_PASSWORD") ?: ""
val hasReleaseKey = releaseKeystorePath.isNotBlank() &&
    releaseKeystorePassword.isNotBlank() &&
    file(releaseKeystorePath).exists()

android {
    namespace = "com.mpersonality.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mpersonality.launcher"
        minSdk = 21 // Android 5.0 ou mais novo: funciona em praticamente qualquer celular
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
        resValue("string", "app_name", "MPersonality")
    }

    signingConfigs {
        // Chave fixa de debug guardada no projeto (não é segredo, serve só para testes).
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = "mpersonality"
                keyPassword = releaseKeystorePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Nome e identificação diferentes: o debug e o release podem ficar instalados juntos.
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "MPersonality Debug")
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            // R8: remove código não usado e otimiza o resto. Deixa o app bem mais rápido.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    lint {
        checkReleaseBuilds = false
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
