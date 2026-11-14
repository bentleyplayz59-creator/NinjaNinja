plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing key comes from environment variables (set by the GitHub workflow from repo secrets).
// If they are missing (for example a local build), the default debug key is used instead.
val ksFile: String? = System.getenv("KEYSTORE_FILE")
val ksPass: String? = System.getenv("KEYSTORE_PASSWORD")
val ksAlias: String? = System.getenv("KEY_ALIAS")
val keyPass: String? = System.getenv("KEY_PASSWORD")
val hasCustomKey = !ksFile.isNullOrEmpty() && file(ksFile).exists() &&
    !ksPass.isNullOrEmpty() && !ksAlias.isNullOrEmpty() && !keyPass.isNullOrEmpty()

android {
    namespace = "com.bentleyplayz.modbuilderapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bentleyplayz.modbuilderapp"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    if (hasCustomKey) {
        signingConfigs {
            create("bentleyplayz") {
                storeFile = file(ksFile!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = keyPass
            }
        }
        buildTypes {
            getByName("debug") { signingConfig = signingConfigs.getByName("bentleyplayz") }
            getByName("release") { signingConfig = signingConfigs.getByName("bentleyplayz") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
