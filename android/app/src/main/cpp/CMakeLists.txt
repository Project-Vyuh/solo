cmake_minimum_required(VERSION 3.22.1)

project("solo")

# -----------------------------------------------------------------------------
# Solo native library: JNI bridge + statically linked llama.cpp inference engine.
#
# Build strategy:
#   - llama.cpp is added as a subdirectory and built as static libs.
#   - solo_native is a SHARED library (loaded by Android via System.loadLibrary).
#   - All llama / ggml symbols are linked statically INTO libsolo_native.so —
#     producing a single self-contained .so the APK can ship.
# -----------------------------------------------------------------------------

# llama.cpp options. Most default to ${LLAMA_STANDALONE} which is OFF here
# (since we're a subdirectory), but we set them explicitly for clarity and
# protection against upstream default changes.
set(BUILD_SHARED_LIBS         OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON        OFF CACHE BOOL "" FORCE)   # not needed: solo_jni uses llama.h directly
set(LLAMA_BUILD_TESTS         OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS         OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER        OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP           OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI            OFF CACHE BOOL "" FORCE)
set(LLAMA_USE_PREBUILT_UI     OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL             OFF CACHE BOOL "" FORCE)   # no HTTPS in-process
set(LLAMA_LLGUIDANCE          OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL                OFF CACHE BOOL "" FORCE)   # we don't download via llama
set(LLAMA_ALL_WARNINGS        OFF CACHE BOOL "" FORCE)

# ggml backend / feature flags
set(GGML_OPENMP               OFF CACHE BOOL "" FORCE)   # NDK OpenMP is unreliable
set(GGML_LLAMAFILE            OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS          OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES       OFF CACHE BOOL "" FORCE)
set(GGML_CPU                  ON  CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL           OFF CACHE BOOL "" FORCE)

# ARM-specific CPU features for arm64-v8a. These mirror the cppFlags
# in app/build.gradle.kts and unlock SIMD paths in ggml's CPU backend
# that are critical for inference speed on Snapdragon 8 Gen 3+.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)   # don't probe host
    set(GGML_CPU_ARM_ARCH     "armv8.2-a+dotprod+fp16+i8mm" CACHE STRING "" FORCE)
endif()

add_subdirectory(llama.cpp)

# -----------------------------------------------------------------------------
# Solo JNI bridge
# -----------------------------------------------------------------------------
add_library(
    solo_native
    SHARED
    solo_jni.cpp
)

target_include_directories(solo_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    solo_native
    PRIVATE
    llama
    ggml
    ${log-lib}
    ${android-lib}
)

# Strip the final .so in Release to reduce APK size. AGP will additionally
# run its own strip pass, but doing it here ensures debug symbols never
# make it into the intermediate artifacts AGP packages.
set_target_properties(solo_native PROPERTIES
    LINK_FLAGS_RELEASE "-Wl,--gc-sections -Wl,--strip-all"
)
