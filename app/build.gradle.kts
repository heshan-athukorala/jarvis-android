plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.marklvi.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marklvi.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"
    }

    val releaseKey = file("release-key.jks")
    if (releaseKey.exists()) {
        signingConfigs {
            create("release") {
                storeFile = releaseKey
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (releaseKey.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
}


dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
