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

// ---------------------------------------------------------------------------
// Release signing
//
// Resolved once, at configuration time, so both the `signingConfigs` block and
// the assemble guard below agree on whether a real release key is available.
// There is deliberately no hardcoded password fallback: a guessable release key
// lets anyone produce an APK that Android accepts as an upgrade of this app.
// ---------------------------------------------------------------------------
val localProperties = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val resolvedKeystore = listOf(
    file("release.keystore"),
    rootProject.file("app/release.keystore"),
    file("KuatoDev.jks"),
    rootProject.file("app/KuatoDev.jks")
).firstOrNull { it.exists() }

val releaseStorePassword: String? = localProperties.getProperty("signing.storePassword")
    ?: (project.findProperty("storePassword") as String?)
    ?: envOrNull("SIGNING_STORE_PASSWORD")
val releaseKeyAlias: String? = localProperties.getProperty("signing.keyAlias")
    ?: (project.findProperty("alias") as String?)
    ?: envOrNull("SIGNING_KEY_ALIAS")
val releaseKeyPassword: String? = localProperties.getProperty("signing.keyPassword")
    ?: (project.findProperty("keyPassword") as String?)
    ?: envOrNull("SIGNING_KEY_PASSWORD")

val hasReleaseSigning: Boolean = resolvedKeystore != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "id.vern.antigravity"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.ikram.antigravity"
        // The bundled Linux userspace needs the glibc bootstrap, exec of
        // extracted binaries, and MANAGE_EXTERNAL_STORAGE behaviour; none of
        // that is viable below Android 11. Keep this in sync with README.md.
        minSdk = 30
        targetSdk = 28
        versionCode = 7
        versionName = "1.0.6"

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

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = resolvedKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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
            // Fail closed: never silently emit a debug-signed "release" APK,
            // because it cannot be installed as an upgrade over a real release.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "WARNING: no release signing config found (keystore + " +
                        "signing.storePassword/signing.keyAlias/signing.keyPassword " +
                        "or SIGNING_* env vars). `assembleRelease` will refuse to build " +
                        "a distributable APK; use `assembleDebug` for local testing."
                )
                null
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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

// Guard against the historical failure mode where a release APK was produced
// without any signing configuration and silently shipped debug-signed.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Refusing to build ${project.path}:${this.name} without release signing.\n" +
                    "Provide app/release.keystore (or app/KuatoDev.jks) plus credentials via " +
                    "local.properties (signing.storePassword, signing.keyAlias, " +
                    "signing.keyPassword) or the SIGNING_STORE_PASSWORD / " +
                    "SIGNING_KEY_ALIAS / SIGNING_KEY_PASSWORD environment variables."
            )
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
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
}

