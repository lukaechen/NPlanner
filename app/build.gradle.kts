plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "at.lukaechen.nplanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "at.lukaechen.nplanner"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.44"
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                System.getenv("NPLANNER_KEYSTORE_PATH")
                    ?: error("NPLANNER_KEYSTORE_PATH fehlt")
            )
            storePassword = System.getenv("NPLANNER_KEYSTORE_PASSWORD")
                ?: error("NPLANNER_KEYSTORE_PASSWORD fehlt")
            keyAlias = System.getenv("NPLANNER_KEY_ALIAS")
                ?: error("NPLANNER_KEY_ALIAS fehlt")
            keyPassword = System.getenv("NPLANNER_KEY_PASSWORD")
                ?: error("NPLANNER_KEY_PASSWORD fehlt")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
}
