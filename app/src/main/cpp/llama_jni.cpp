#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// llama.cpp headers (add as submodule in nativelibs/llama.cpp)
// #include "llama.h"

// Placeholder model context
struct ModelContext {
    bool loaded = false;
    std::string path;
    void* model = nullptr;
    void* context = nullptr;
};

static ModelContext g_ctx;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_llama_LlamaJNI_nativeLoadModel(
    JNIEnv* env, jobject /*thiz*/, jstring path) {

    const char* cpath = env->GetStringUTFChars(path, nullptr);
    LOGI("Loading model from: %s", cpath);

    // TODO: actual llama.cpp model loading
    // g_ctx.model = llama_load_model_from_file(cpath, llama_model_default_params());
    // if (!g_ctx.model) { LOGE("Failed to load model"); return JNI_FALSE; }
    // g_ctx.context = llama_new_context_with_model(g_ctx.model, llama_context_default_params());

    g_ctx.loaded = true;
    g_ctx.path = cpath;

    env->ReleaseStringUTFChars(path, cpath);
    LOGI("Model loaded (placeholder)");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_llama_LlamaJNI_nativeGenerate(
    JNIEnv* env, jobject /*thiz*/,
    jstring prompt, jint max_tokens, jfloat temperature,
    jobject callback) {

    if (!g_ctx.loaded) {
        LOGE("Model not loaded");
        return;
    }

    const char* cprompt = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(cprompt);
    env->ReleaseStringUTFChars(prompt, cprompt);

    LOGI("Generating with prompt: %s", prompt_str.c_str());

    // Find the callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID acceptMethod = env->GetMethodID(callbackClass, "accept", "(Ljava/lang/String;)V");
    if (!acceptMethod) {
        LOGE("Callback method not found");
        return;
    }

    // TODO: actual llama.cpp inference loop
    // llama_batch batch = llama_batch_init(512, 0, 1);
    // for (int i = 0; i < max_tokens; i++) {
    //     ... llama_decode, llama_sample_token ...
    //     std::string token_str = llama_token_to_piece(ctx, token);
    //     jstring jtoken = env->NewStringUTF(token_str.c_str());
    //     env->CallVoidMethod(callback, acceptMethod, jtoken);
    //     env->DeleteLocalRef(jtoken);
    // }

    // Placeholder: echo back the prompt
    std::string response = "🤖 [llama.cpp on-device] Echo: " + prompt_str;
    jstring jresp = env->NewStringUTF(response.c_str());
    env->CallVoidMethod(callback, acceptMethod, jresp);
    env->DeleteLocalRef(jresp);
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_llama_LlamaJNI_nativeUnloadModel(
    JNIEnv* env, jobject /*thiz*/) {

    LOGI("Unloading model");
    // TODO: llama_free_model(g_ctx.model);
    // TODO: llama_free(g_ctx.context);
    g_ctx.loaded = false;
    g_ctx.path = "";
    g_ctx.model = nullptr;
    g_ctx.context = nullptr;
}

JNIEXPORT jintArray JNICALL
Java_com_nexus_agent_core_llama_LlamaJNI_nativeTokenize(
    JNIEnv* env, jobject /*thiz*/, jstring text) {

    const char* ctext = env->GetStringUTFChars(text, nullptr);
    // TODO: actual tokenization
    // std::vector<llama_token> tokens = llama_tokenize(ctx, ctext, true);
    std::vector<int> tokens = {1, 2, 3}; // placeholder
    env->ReleaseStringUTFChars(text, ctext);

    jintArray result = env->NewIntArray(tokens.size());
    env->SetIntArrayRegion(result, 0, tokens.size(), tokens.data());
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_llama_LlamaJNI_nativeDetokenize(
    JNIEnv* env, jobject /*thiz*/, jintArray tokens) {

    // TODO: actual detokenization
    std::string result = "detokenized_text";
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
