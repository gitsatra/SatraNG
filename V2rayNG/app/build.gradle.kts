plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.satra.ng"
        minSdk = 24
        // 35 = current stable Play target; 37 (canary) made sideload/Play Protect look more "unknown".
        targetSdk = 35
        versionCode = 777
        versionName = "2.7.1"

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    // Phones only need arm64; pass -PSATRA_ALL_ABIS=true for the other ABIs.
                    if (properties["SATRA_ALL_ABIS"] == "true") include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                    else include("arm64-v8a")
                }
                // Optional universal APK: -PSATRA_UNIVERSAL=true
                isUniversalApk = properties["SATRA_UNIVERSAL"] == "true"
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("satraRelease") {
            // Release keystore and its password are never committed: they come from the untracked
            // keystore/signing.properties (storePassword=..., keyAlias=..., keyPassword=...) or from
            // env SATRA_STORE_PASSWORD / SATRA_KEY_ALIAS / SATRA_KEY_PASSWORD (CI secrets).
            val keystoreFile = rootProject.file("keystore/satra-release.p12")
            val signProps: Map<String, String> = rootProject.file("keystore/signing.properties")
                .takeIf { it.exists() }?.readLines().orEmpty()
                .mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 && !l.trimStart().startsWith("#") }?.let { it[0].trim() to it[1].trim() } }
                .toMap()
            val storePw = signProps["storePassword"] ?: System.getenv("SATRA_STORE_PASSWORD")
            if (keystoreFile.exists() && !storePw.isNullOrEmpty()) {
                storeFile = keystoreFile
                storePassword = storePw
                keyAlias = signProps["keyAlias"] ?: System.getenv("SATRA_KEY_ALIAS") ?: "satrang"
                keyPassword = signProps["keyPassword"] ?: System.getenv("SATRA_KEY_PASSWORD") ?: storePw
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            manifestPlaceholders["appLabel"] = "@string/app_name"
            // R8 (full mode) shrink without renaming; see proguard-rules.pro for Gson/JNI keeps.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val satraSigning = signingConfigs.findByName("satraRelease")
            if (satraSigning?.storeFile?.exists() == true && satraSigning.storePassword != null) {
                signingConfig = satraSigning
            }
        }
        debug {
            // Side-by-side install with the user's production SatraNG: distinct id, label and data.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "SatraNG Dev"
            // Prefer the same release cert when available so Play Protect
            // reputation is stable across installs (not a fresh debug cert each machine).
            val satraSigning = signingConfigs.findByName("satraRelease")
            if (satraSigning?.storeFile?.exists() == true && satraSigning.storePassword != null) {
                signingConfig = satraSigning
            }
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "SatraNG_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            val versionCodes =
                mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "SatraNG_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        // SatraNG ships English (default), Persian and Russian only; the user asked to drop the other
        // upstream locales. Legacy upstream strings that were never translated fall back to English.
        disable += "MissingTranslation"
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "fa", "ru")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("roborazzi.output.dir", rootProject.file("../preview/premium").absolutePath)
            it.maxHeapSize = "3g"
            it.useJUnitPlatform()
            // Treat Jupiter discovery warnings as failures: JUnit 4 failed on an invalid @Test
            // method, such as one that returns a value, while Jupiter only warns and skips it.
            // The key is @API(status = EXPERIMENTAL) in JUnit 6; after a JUnit upgrade, check
            // that a temporary `@Test fun probe() = 1` still fails the unit test task.
            it.systemProperty("junit.platform.discovery.issue.severity.critical", "WARNING")
        }
    }

}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.appcompat)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Testing Libraries
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    // Offscreen Compose screenshots (Robolectric native graphics + Roborazzi), test classpath only.
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
