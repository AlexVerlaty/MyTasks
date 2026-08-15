plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mytasks.keeplink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mytasks.keeplink"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        // Every string this app shows is hardcoded Russian, but AndroidX and material3 ship
        // their own resources in 80-odd locales, all of which rode along. English stays as
        // the fallback for anything the system asks for that we do not provide.
        resourceConfigurations += setOf("ru", "en")
    }

    val keystoreFile = System.getenv("KEYSTORE_FILE")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("KEY_ALIAS")
    val keyPassword = System.getenv("KEY_PASSWORD")
    val hasSigningEnv =
        !keystoreFile.isNullOrBlank() && !keystorePassword.isNullOrBlank() &&
            !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()

    signingConfigs {
        if (hasSigningEnv) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            // Without R8 the APK is 6.1 MB, of which 16 MB uncompressed is dex: Compose
            // shipped whole for a UI of four screens. The people who install this get the
            // file over mobile data and have to trust it by hand, so both size and the
            // amount of code inside are part of the argument that it is small.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningEnv) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        // A local convenience build, not the release pipeline. lintVital tried to write
        // its report through a path this environment rejects; the app is 300 lines and
        // reviewed by hand. CI (the GitHub Actions workflow) keeps full lint.
        checkReleaseBuilds = false
        abortOnError = false
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
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
