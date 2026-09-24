#include "llama.h"
#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <mutex>
#include <memory>
#include <android/log.h>

#define LOG_TAG "ShardwaveOfficialLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_sampler* g_smpl = nullptr;
static std::mutex g_llama_mutex;
static std::string g_loaded_model_path = "";

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yashvant_shardwave_inference_LlamaCppBridge_nativeInitModel(
        JNIEnv* env,
        jobject /* this */,
        jstring model_path) {

    std::lock_guard<std::mutex> lock(g_llama_mutex);

    const char* path_cstr = env->GetStringUTFChars(model_path, nullptr);
    if (!path_cstr) return JNI_FALSE;

    std::string path(path_cstr);
    env->ReleaseStringUTFChars(model_path, path_cstr);

    if (g_model && g_loaded_model_path == path) {
        return JNI_TRUE;
    }

    // Free existing model context if active
    if (g_smpl) {
        llama_sampler_free(g_smpl);
        g_smpl = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    LOGI("Initializing 100%% official llama.cpp engine for GGUF model path: %s", path.c_str());

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU / ARM NEON execution

    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        LOGE("Failed to load official GGUF model from path: %s", path.c_str());
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create llama context for model: %s", path.c_str());
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Initialize greedy + temperature sampling chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_smpl, llama_sampler_init_greedy());

    g_loaded_model_path = path;
    LOGI("Official llama.cpp GGUF model loaded successfully: %s", path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yashvant_shardwave_inference_LlamaCppBridge_nativeGenerate(
        JNIEnv* env,
        jobject /* this */,
        jstring prompt) {

    std::lock_guard<std::mutex> lock(g_llama_mutex);

    if (!g_model || !g_ctx || !g_smpl) {
        return env->NewStringUTF("Error: llama.cpp GGUF model not loaded.");
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str = prompt_cstr ? std::string(prompt_cstr) : "";
    if (prompt_cstr) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);
    if (!vocab) {
        return env->NewStringUTF("Error: Could not retrieve GGUF model vocabulary.");
    }

    // Tokenize prompt using GGUF BPE/SentencePiece vocabulary
    int n_prompt_tokens = -llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), nullptr, 0, true, true);
    if (n_prompt_tokens <= 0) {
        return env->NewStringUTF("Error: Tokenization failed.");
    }

    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    if (llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        return env->NewStringUTF("Error: Failed to tokenize input prompt.");
    }

    std::string response_text = "";
    int n_predict = 256;

    // Run llama_decode forward passes over input prompt tokens
    for (size_t i = 0; i < prompt_tokens.size(); ++i) {
        llama_batch batch = llama_batch_get_one(&prompt_tokens[i], 1);
        if (llama_decode(g_ctx, batch)) {
            LOGE("llama_decode failed during prompt token evaluation");
            break;
        }
    }

    // Sample predicted neural tokens from output logits
    for (int i = 0; i < n_predict; ++i) {
        llama_token id = llama_sampler_sample(g_smpl, g_ctx, -1);
        llama_sampler_accept(g_smpl, id);

        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            response_text.append(buf, n);
        }

        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, batch)) {
            LOGE("llama_decode failed during token generation loop");
            break;
        }
    }

    if (response_text.empty()) {
        response_text = "No tokens generated by llama.cpp model.";
    }

    return env->NewStringUTF(response_text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_yashvant_shardwave_inference_LlamaCppBridge_nativeFree(
        JNIEnv* env,
        jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_llama_mutex);

    if (g_smpl) {
        llama_sampler_free(g_smpl);
        g_smpl = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_loaded_model_path = "";
    llama_backend_free();
    LOGI("Official llama.cpp engine freed successfully");
}
