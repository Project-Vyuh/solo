// Solo JNI bridge: Kotlin <-> llama.cpp.
//
// Design:
//   - One LlamaSession C++ object per loaded model. The session owns the
//     llama_model, llama_context, and sampler chain.
//   - Kotlin holds an opaque jlong handle (a pointer to the LlamaSession).
//   - nativeStreamCompletion invokes a Java callback (TokenCallback.onToken)
//     for each generated piece. The callback returns a boolean — false aborts
//     the loop. The session also exposes an atomic abort flag for external
//     cancellation (timeouts, screen off, thermal throttle, user cancel).
//   - All llama.cpp APIs are called from the JNI thread; no background threads
//     are spawned in native code. Concurrency is the Kotlin coroutine layer's
//     responsibility.

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "SoloNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::once_flag g_backend_init_flag;

void ensure_backend_initialized() {
    std::call_once(g_backend_init_flag, []() {
        llama_backend_init();
        LOGI("llama backend initialized (mmap=%d, mlock=%d)",
             llama_supports_mmap() ? 1 : 0,
             llama_supports_mlock() ? 1 : 0);
    });
}

void llama_log_to_logcat(ggml_log_level level, const char * text, void * /*user_data*/) {
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        default: break;
    }
    __android_log_write(prio, "llama.cpp", text);
}

struct LlamaSession {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    llama_sampler * sampler = nullptr;
    std::atomic_bool abort_flag{false};

    ~LlamaSession() {
        if (sampler) llama_sampler_free(sampler);
        if (ctx)     llama_free(ctx);
        if (model)   llama_model_free(model);
    }
};

inline LlamaSession * handle_to_session(jlong h) {
    return reinterpret_cast<LlamaSession *>(h);
}

inline jlong session_to_handle(LlamaSession * s) {
    return reinterpret_cast<jlong>(s);
}

std::string jstring_to_utf8(JNIEnv * env, jstring js) {
    if (!js) return {};
    const char * c = env->GetStringUTFChars(js, nullptr);
    std::string s(c ? c : "");
    if (c) env->ReleaseStringUTFChars(js, c);
    return s;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text, bool add_bos) {
    int n_estimate = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                     nullptr, 0, add_bos, /*parse_special*/ true);
    if (n_estimate <= 0) return {};
    std::vector<llama_token> out(n_estimate);
    int n_actual = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                  out.data(), (int32_t) out.size(),
                                  add_bos, /*parse_special*/ true);
    if (n_actual < 0) return {};
    out.resize(n_actual);
    return out;
}

std::string token_to_piece(const llama_vocab * vocab, llama_token tok, bool render_special) {
    std::string out(16, '\0');
    int n = llama_token_to_piece(vocab, tok, out.data(), (int32_t) out.size(), 0, render_special);
    if (n < 0) {
        out.resize(-n);
        n = llama_token_to_piece(vocab, tok, out.data(), (int32_t) out.size(), 0, render_special);
        if (n < 0) return {};
    }
    out.resize(n);
    return out;
}

} // namespace

// -----------------------------------------------------------------------------
// JNI exports
// -----------------------------------------------------------------------------

extern "C" {

JNIEXPORT jstring JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeVersion(JNIEnv * env, jobject /*thiz*/) {
    std::ostringstream s;
    s << "solo-native/0.0.1 llama.cpp-b9371"
      << " mmap=" << (llama_supports_mmap() ? 1 : 0)
      << " mlock=" << (llama_supports_mlock() ? 1 : 0);
    return env->NewStringUTF(s.str().c_str());
}

JNIEXPORT void JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeInitBackend(JNIEnv * /*env*/, jobject /*thiz*/) {
    llama_log_set(llama_log_to_logcat, nullptr);
    ensure_backend_initialized();
}

JNIEXPORT void JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeFreeBackend(JNIEnv * /*env*/, jobject /*thiz*/) {
    llama_backend_free();
}

JNIEXPORT jlong JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeLoadModel(
        JNIEnv * env, jobject /*thiz*/,
        jstring path,
        jint    n_ctx,
        jint    n_threads,
        jint    n_gpu_layers) {

    ensure_backend_initialized();

    std::string model_path = jstring_to_utf8(env, path);
    if (model_path.empty()) {
        LOGE("nativeLoadModel: empty model path");
        return 0;
    }

    auto session = std::make_unique<LlamaSession>();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;
    mparams.use_mmap     = true;
    mparams.use_mlock    = false;

    LOGI("loading model: %s (n_ctx=%d, n_threads=%d, n_gpu_layers=%d)",
         model_path.c_str(), n_ctx, n_threads, n_gpu_layers);

    session->model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (!session->model) {
        LOGE("llama_model_load_from_file failed");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = (uint32_t) n_ctx;
    cparams.n_batch         = 512;
    cparams.n_ubatch        = 512;
    cparams.n_threads       = n_threads;
    cparams.n_threads_batch = n_threads;
    // Flash attention: let llama.cpp pick the right backend implementation.
    // Gives 1.5-2x prefill on supported architectures.
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
    // KV cache quantization: q8_0 halves KV memory vs f16 with negligible
    // quality impact. Critical for long contexts on memory-constrained mobile.
    cparams.type_k          = GGML_TYPE_Q8_0;
    cparams.type_v          = GGML_TYPE_Q8_0;

    session->ctx = llama_init_from_model(session->model, cparams);
    if (!session->ctx) {
        LOGE("llama_init_from_model failed");
        return 0;
    }

    auto schain_params = llama_sampler_chain_default_params();
    session->sampler = llama_sampler_chain_init(schain_params);
    llama_sampler_chain_add(session->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("model loaded: n_ctx=%u, n_vocab=%d",
         llama_n_ctx(session->ctx),
         llama_vocab_n_tokens(llama_model_get_vocab(session->model)));

    return session_to_handle(session.release());
}

JNIEXPORT void JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeUnloadModel(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto * s = handle_to_session(handle);
    if (!s) return;
    LOGI("unloading model");
    delete s;
}

JNIEXPORT void JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeAbort(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto * s = handle_to_session(handle);
    if (!s) return;
    s->abort_flag.store(true);
}

JNIEXPORT jstring JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeGetModelInfo(
        JNIEnv * env, jobject /*thiz*/, jlong handle) {
    auto * s = handle_to_session(handle);
    if (!s) return env->NewStringUTF("{}");

    char desc[256] = {0};
    llama_model_desc(s->model, desc, sizeof(desc));

    std::ostringstream j;
    j << "{"
      << "\"description\":\"" << desc << "\","
      << "\"n_ctx\":" << llama_n_ctx(s->ctx) << ","
      << "\"n_vocab\":" << llama_vocab_n_tokens(llama_model_get_vocab(s->model)) << ","
      << "\"n_params\":" << llama_model_n_params(s->model) << ","
      << "\"size_bytes\":" << llama_model_size(s->model)
      << "}";
    return env->NewStringUTF(j.str().c_str());
}

JNIEXPORT jint JNICALL
Java_dev_projectvyuh_solo_data_llm_jni_LlamaBridge_nativeStreamCompletion(
        JNIEnv * env, jobject /*thiz*/,
        jlong   handle,
        jstring prompt_j,
        jint    max_tokens,
        jfloat  temperature,
        jfloat  top_p,
        jint    top_k,
        jint    seed,
        jobject callback) {

    auto * s = handle_to_session(handle);
    if (!s) return -1;
    s->abort_flag.store(false);

    const llama_vocab * vocab = llama_model_get_vocab(s->model);

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_cls, "onToken", "(Ljava/lang/String;)Z");
    if (!on_token) {
        LOGE("TokenCallback.onToken(String):Boolean not found");
        return -1;
    }

    if (s->sampler) llama_sampler_free(s->sampler);
    auto schain_params = llama_sampler_chain_default_params();
    s->sampler = llama_sampler_chain_init(schain_params);
    if (top_k > 0)              llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(top_k));
    if (top_p > 0.f && top_p < 1.f)
                                llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(top_p, 1));
    if (temperature > 0.f)      llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(s->sampler,
        temperature <= 0.f ? llama_sampler_init_greedy()
                           : llama_sampler_init_dist((uint32_t) seed));

    std::string prompt = jstring_to_utf8(env, prompt_j);
    bool add_bos = llama_vocab_get_add_bos(vocab);
    std::vector<llama_token> tokens = tokenize(vocab, prompt, add_bos);
    if (tokens.empty()) {
        LOGE("tokenize returned empty");
        return -1;
    }

    const uint32_t n_ctx = llama_n_ctx(s->ctx);
    if ((uint32_t) tokens.size() >= n_ctx) {
        LOGE("prompt (%zu tokens) exceeds n_ctx (%u)", tokens.size(), n_ctx);
        return -1;
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(s->ctx, batch) != 0) {
        LOGE("prefill decode failed");
        return -1;
    }

    int n_generated = 0;
    std::string utf8_carry;

    for (int i = 0; i < max_tokens; ++i) {
        if (s->abort_flag.load()) {
            LOGI("generation aborted at token %d", i);
            break;
        }

        llama_token next = llama_sampler_sample(s->sampler, s->ctx, -1);
        llama_sampler_accept(s->sampler, next);

        if (llama_vocab_is_eog(vocab, next)) break;

        std::string piece = token_to_piece(vocab, next, /*render_special*/ false);
        utf8_carry += piece;

        // Find longest valid UTF-8 prefix; trailing partial bytes wait for the
        // next token to complete the codepoint.
        size_t emit_end = utf8_carry.size();
        while (emit_end > 0) {
            unsigned char b = (unsigned char) utf8_carry[emit_end - 1];
            if (b < 0x80) break;
            if ((b & 0xC0) == 0xC0) { emit_end--; break; }
            emit_end--;
        }

        if (emit_end > 0) {
            std::string emit = utf8_carry.substr(0, emit_end);
            utf8_carry.erase(0, emit_end);

            jstring js = env->NewStringUTF(emit.c_str());
            jboolean keep_going = env->CallBooleanMethod(callback, on_token, js);
            env->DeleteLocalRef(js);

            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                break;
            }
            if (!keep_going) break;
        }

        llama_batch next_batch = llama_batch_get_one(&next, 1);
        if (llama_decode(s->ctx, next_batch) != 0) {
            LOGE("decode failed at token %d", i);
            break;
        }

        n_generated++;
    }

    if (!utf8_carry.empty()) {
        jstring js = env->NewStringUTF(utf8_carry.c_str());
        env->CallBooleanMethod(callback, on_token, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    return n_generated;
}

} // extern "C"
