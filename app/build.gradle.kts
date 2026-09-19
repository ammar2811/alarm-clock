plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // Kotlin package and resource namespace, kept as upstream so the diff against AOSP stays readable.
    namespace = "com.android.deskclock"
    compileSdk = 37

    defaultConfig {
        // Distinct from Google Clock (com.google.android.deskclock) so both can be installed.
        // ClockContract.AUTHORITY derives from this, and the manifest uses ${applicationId}.
        applicationId = "com.ammar.deskclock"
        // Raised from upstream's 23 because the object detection library used by the
        // photo challenge requires 24. Forcing it with tools:overrideLibrary was the
        // alternative, and that is documented as risking runtime failures. Android 7.0
        // is from 2016, so this costs nothing in practice.
        minSdk = 24
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // The detection library ships native code for every ABI, which quadruples the
            // APK. These two cover real devices and the emulator used for testing.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    androidResources {
        // The detection model is memory mapped at runtime, so it must not be compressed.
        noCompress += "tflite"
    }

    buildFeatures {
        // ClockContract reads BuildConfig.APPLICATION_ID, and AGP 8+ omits BuildConfig by default.
        buildConfig = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // With AGP 9 built-in Kotlin, jvmTarget defaults to targetCompatibility above.

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        // Lint gates :app:check, so every remaining error has to be a real one.
        abortOnError = true

        // The ~100 res/values-* directories come from AOSP untouched and account for roughly
        // 1200 findings on their own, which is enough to bury anything real. They are not ours
        // to fix: the strings are upstream translations of upstream copy, and we ship no
        // translations of our own, so every string this fork adds would reopen the same noise.
        disable += setOf(
            // Upstream translations disagree with the English source about how many format
            // specifiers a string has, and about which plural quantities a language uses.
            "StringFormatCount",
            "ImpliedQuantity",
            "UnusedQuantity",
            "PluralsCandidate",
            // Lint's dictionary does not speak the ~100 languages bundled here.
            "Typos",
            // Everything this fork adds is English only and always will be, so the check
            // reports one finding per locale per new string and never anything actionable.
            "MissingTranslation"
        )

        // Raising targetSdk past 30 changes real runtime behavior: notification and exact alarm
        // permissions become runtime grants, foreground services need declared types, and
        // ACTION_CLOSE_SYSTEM_DIALOGS stops working. That is its own piece of work with its own
        // testing, not something to smuggle in under a lint cleanup.
        disable += "ExpiredTargetSdkVersion"
    }

    testOptions {
        unitTests {
            // Robolectric needs merged resources, and stubbed android.util.Log calls
            // should return defaults rather than throwing.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}


dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.percentlayout)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.media)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.legacy.support.core.ui)
    implementation(libs.androidx.legacy.support.v13)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Photo challenge: camera preview plus on-device object detection.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
