import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing comes from keystore.properties locally, or from environment variables in CI. With
// neither present, release falls back to the debug key so `assembleRelease` is always installable.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: providers.environmentVariable(env).orNull

// CI passes -PversionName=1.2.3 -PversionCode=42 from the tag and run number.
val appVersionName = providers.gradleProperty("versionName").getOrElse("1.5.2")
val appVersionCode = providers.gradleProperty("versionCode").map { it.toInt() }.getOrElse(10502)

android {
    namespace = "dev.rafa.waktusholat"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.rafa.waktusholat"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = false
    }

    androidResources {
        localeFilters += listOf("en", "in")
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "SIGNING_STORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // Null-check intrinsics on every public parameter are dead weight in a closed app.
            freeCompilerArgs.addAll(
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions",
            )
        }
    }

    buildFeatures {
        buildConfig = false
        resValues = false
    }

    // The dependency manifest is only read by Play; it is noise in a sideloaded APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "META-INF/*.version",
            "META-INF/**/LICENSE*",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
            "kotlin/**",
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf("GradleDependency", "OldTargetApi", "AndroidGradlePluginVersion")
    }
}

dependencies {
    // Deliberately framework-only: the UI needs no compatibility layer, and skipping AppCompat,
    // Material and RecyclerView keeps the release APK around 100 KB.
    implementation(project(":core"))
}
