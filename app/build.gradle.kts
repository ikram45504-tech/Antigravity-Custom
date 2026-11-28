import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "id.vern.antigravity"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.ikram.antigravity"
        minSdk = 26
        targetSdk = 28
        versionCode = 30
        versionName = "1.0.29"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("int", "GATEWAY_PORT", "45161")
        buildConfigField("int", "CORE_PORT", "45158")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("zip", "gz", "tar")
    }

    val localProperties = Properties().apply {
        val propFile = rootProject.file("local.properties")
        if (propFile.exists()) {
            propFile.inputStream().use { load(it) }
        }
    }

    fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    val customKeystore = listOf(
        file("KuatoDev.jks"),
        rootProject.file("app/KuatoDev.jks")
    ).firstOrNull { it.exists() }
    val defaultReleaseKeystore = listOf(
        file("release.keystore"),
        rootProject.file("app/release.keystore")
    ).firstOrNull { it.exists() }

    val explicitStorePassword = localProperties.getProperty("signing.storePassword")
        ?: (project.findProperty("storePassword") as String?)
        ?: envOrNull("SIGNING_STORE_PASSWORD")
    val explicitKeyAlias = localProperties.getProperty("signing.keyAlias")
        ?: (project.findProperty("alias") as String?)
        ?: envOrNull("SIGNING_KEY_ALIAS")
    val explicitKeyPassword = localProperties.getProperty("signing.keyPassword")
        ?: (project.findProperty("keyPassword") as String?)
        ?: envOrNull("SIGNING_KEY_PASSWORD")

    val hasExplicitCustomSigning = customKeystore != null &&
        !explicitStorePassword.isNullOrBlank() &&
        !explicitKeyAlias.isNullOrBlank() &&
        !explicitKeyPassword.isNullOrBlank()

    val resolvedKeystore = if (hasExplicitCustomSigning) customKeystore else defaultReleaseKeystore
    val resolvedStorePassword = if (hasExplicitCustomSigning) explicitStorePassword else (explicitStorePassword ?: "antigravity123")
    val resolvedKeyAlias = if (hasExplicitCustomSigning) explicitKeyAlias else (explicitKeyAlias ?: "antigravity")
    val resolvedKeyPassword = if (hasExplicitCustomSigning) explicitKeyPassword else (explicitKeyPassword ?: "antigravity123")

    val hasReleaseSigning = resolvedKeystore != null && resolvedKeystore.exists() &&
        !resolvedStorePassword.isNullOrBlank() &&
        !resolvedKeyAlias.isNullOrBlank() &&
        !resolvedKeyPassword.isNullOrBlank()

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = resolvedKeystore
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("Release signing keystore/credentials not configured; falling back to debug signing.")
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        // Keep abortOnError = false until lint-baseline.xml is generated in a full SDK environment.
        abortOnError = false
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}

