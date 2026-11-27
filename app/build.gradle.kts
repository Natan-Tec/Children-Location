plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.natantec.childrenlocation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.natantec.childrenlocation"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }

    // Chave de debug FIXA (versionada de propósito): todo build sai com a mesma assinatura,
    // então o APK atualiza por cima do anterior e o login com Google tem um SHA-1 estável.
    // Não é um segredo. Para a versão pública, trocar por uma chave de release privada.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Login com Google (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Leitor de QR da Google (não precisa de permissão de câmera) e gerador de QR
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")

    // Mapa OpenStreetMap, sem chave de API
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
