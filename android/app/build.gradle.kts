plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.projectvyuh.solo"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.projectvyuh.solo"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"

        ndk {
            // LiteRT-LM AAR ships native code for these ABIs; we lock to arm64-v8a
            // for our target devices.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Google AI Edge LiteRT-LM — Solo's on-device inference engine.
    // Wraps Google's production runtime that powers Gemini Nano in Chrome,
    // Chromebook Plus, and Pixel Watch. Officially supports Gemma 3n / Gemma 4
    // via .litertlm model files from huggingface.co/litert-community.
    implementation(libs.litertlm.android)

    // sherpa-onnx — Solo's voice runtime. Supports Moonshine STT, Kokoro TTS,
    // Silero VAD, and openWakeWord in one AAR. Distributed via GitHub releases
    // (no Maven artifact). Developer must download `sherpa-onnx-1.13.2.aar`
    // from github.com/k2-fsa/sherpa-onnx/releases into `app/libs/` before
    // building — see SOLO-VOICE.md §6.
    implementation(files("libs/sherpa-onnx-1.13.2.aar"))

    // Apache Commons Compress — extract tar.bz2 voice model archives published
    // by sherpa-onnx (Moonshine, Kokoro). Pulled in for Phase 1B; size impact
    // ~1 MB. We use only the tar + bzip2 surfaces.
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
