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
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "dev.projectvyuh.solo"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                )
                cppFlags += listOf(
                    "-std=c++17",
                    "-O3",
                    "-DNDEBUG",
                    // ARMv8.2 baseline (Cortex-A75+, Snapdragon 845+) plus:
                    //   dotprod = 8-bit dot product (sdot/udot) — 2-4x int8 matmul
                    //   fp16    = half-precision arithmetic
                    //   i8mm    = 8-bit integer matrix multiply (smmla) — another ~2x
                    //             on Snapdragon 8 Gen 3+ / Tensor G3+ / Dimensity 9300+
                    "-march=armv8.2-a+dotprod+fp16+i8mm",
                    // -ffast-math is intentionally OMITTED: ggml's ggml-cpu/vec.h
                    // hard-errors with it. Attention softmax requires exp(-inf)=0 for
                    // causal masks; -ffinite-math-only would silently miscompile this.
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

// -----------------------------------------------------------------------------
// Privacy audit: verify libsolo_native.so does NOT export socket-related
// symbols. This is the build-time guarantee that no native code (ours or any
// transitive dep we statically link) can open a raw network socket, bypassing
// NetworkGuardInterceptor at the OkHttp layer.
//
// Detection: scan dynamic symbols of every libsolo_native.so produced under
// build/intermediates/cxx for matches against KNOWN socket-creating libc
// entry points. Anything matched is a build failure.
//
// Why this works: solo_jni.cpp + llama.cpp + ggml are all statically linked
// into one .so. If none of them reference these symbols, the linker doesn't
// pull them in. Greater-than-zero matches means something in our native code
// is reaching for network primitives.
// -----------------------------------------------------------------------------
val privacyAuditNative by tasks.registering {
    group = "verification"
    description = "Verify libsolo_native.so has no network-syscall symbols (NetworkGuard companion)"

    doLast {
        val ndkRoot = android.ndkDirectory
        val hostTag = when {
            org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "darwin-x86_64"
            org.gradle.internal.os.OperatingSystem.current().isLinux  -> "linux-x86_64"
            else -> throw GradleException("unsupported host for privacy audit")
        }
        val llvmNm = file("$ndkRoot/toolchains/llvm/prebuilt/$hostTag/bin/llvm-nm")
        if (!llvmNm.exists()) throw GradleException("llvm-nm not found at $llvmNm")

        val intermediatesRoot = file("$buildDir/intermediates/cxx")
        val sos = fileTree(intermediatesRoot) { include("**/libsolo_native.so") }.files
        if (sos.isEmpty()) {
            logger.warn("privacyAuditNative: no libsolo_native.so found under $intermediatesRoot (build it first); skipping")
            return@doLast
        }

        // libc socket-layer symbols. If any of these are referenced by our native
        // code or its statically-linked deps, the linker will leave a reference in
        // the .so's dynamic-undefined ("U") symbol table. We scan for those.
        val forbidden = listOf(
            "socket", "socketpair", "connect", "bind", "listen", "accept", "accept4",
            "sendto", "sendmsg", "recvfrom", "recvmsg",
            "getaddrinfo", "gethostbyname", "gethostbyname_r",
        )

        val violations = mutableListOf<String>()
        sos.forEach { so ->
            val output = providers.exec {
                commandLine(llvmNm.absolutePath, "--undefined-only", "--dynamic", so.absolutePath)
            }.standardOutput.asText.get()

            val undefined = output.lineSequence()
                .mapNotNull { it.trim().split(Regex("\\s+")).lastOrNull() }
                .filter { it in forbidden }
                .toSet()

            if (undefined.isNotEmpty()) {
                violations += "${so.relativeTo(rootDir)}: ${undefined.sorted().joinToString(", ")}"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Privacy audit FAILED — libsolo_native.so references forbidden network syscalls:\n  " +
                violations.joinToString("\n  ") +
                "\nNetworkGuardInterceptor only protects the OkHttp layer. Native code that opens" +
                "\nsockets bypasses it. Investigate and either (a) remove the dependency, or (b)" +
                "\nadd it to the audit allow-list with a written justification."
            )
        }
        logger.lifecycle("privacyAuditNative: OK — ${sos.size} .so file(s) scanned, no socket symbols")
    }
}

// Run the audit after each native build so a failure surfaces at build time.
afterEvaluate {
    tasks.matching { it.name.startsWith("buildCMake") }.configureEach {
        finalizedBy(privacyAuditNative)
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

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
