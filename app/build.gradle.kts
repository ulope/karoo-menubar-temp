import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.ulope.karoostatusbartemp"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ulope.karoostatusbartemp"
        // Karoo 2 runs Android 8.1 (API 27), Karoo 3 runs Android 12.
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }?.removePrefix("v") ?: "0.1.0"

        // Lets the Karoo check for updates (see the release workflow, which publishes manifest.json).
        val repository = System.getenv("GITHUB_REPOSITORY") ?: "ulope/karoo-statusbar-temp"
        manifestPlaceholders["karooManifestUrl"] =
            "https://github.com/$repository/releases/latest/download/manifest.json"
    }

    signingConfigs {
        // A stable release key is injected from CI secrets so that updates can be
        // installed over previous versions. Without it, release builds fall back
        // to the debug key (installable, but not upgradeable across machines).
        val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
        if (!keystoreBase64.isNullOrBlank()) {
            create("release") {
                val keystoreFile = File.createTempFile("release", ".jks").apply {
                    deleteOnExit()
                    writeBytes(Base64.getDecoder().decode(keystoreBase64))
                }
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.karoo.ext)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
