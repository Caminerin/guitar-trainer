plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.caminerin.guitartrainer"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // Por defecto usa el keystore de pruebas (incluido en el repo) para que
            // CI pueda firmar el AAB. Al publicar de verdad, define estas variables
            // de entorno con tu keystore real: RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
            // RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD.
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "debug.keystore")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "guitartrainer"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "guitartrainer"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "guitartrainer"
        }
    }

    defaultConfig {
        applicationId = "com.caminerin.guitartrainer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // AdMob (Google Mobile Ads). Usa IDs de TEST hasta crear cuenta AdMob real.
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
