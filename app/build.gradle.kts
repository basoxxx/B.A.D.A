plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Chiave fissa da CI (secret GitHub): senza, ogni build ha firma diversa e l'aggiornamento richiede di disinstallare (perdendo i dati).
val keystorePath: String? = System.getenv("BADA_KEYSTORE")

android {
    namespace = "app.bada"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.bada"
        minSdk = 26
        targetSdk = 36 // edge-to-edge forzato: i layout usano fitsSystemWindows
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("bada") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("BADA_KEYSTORE_PASSWORD")
                keyAlias = "bada"
                keyPassword = System.getenv("BADA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("bada") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    testImplementation("junit:junit:4.13.2")
}
