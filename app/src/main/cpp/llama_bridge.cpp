#include <jni.h>
#include <string>
#include <android/log.h>
#include <thread>
#include <chrono>

#define TAG "LlamaBridgeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_rag_app_data_llm_LlamaBridge_initModelNative(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPathStr) {
    const char* model_path = env->GetStringUTFChars(modelPathStr, nullptr);
    LOGI("Initializing model from path: %s", model_path);

    // Dummy opaque pointer representing model handle
    // When compiling full llama.cpp, this instantiates llama_model_params & llama_load_model_from_file
    uintptr_t handle = 0xDEADBEEF;

    env->ReleaseStringUTFChars(modelPathStr, model_path);
    return (jlong)handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rag_app_data_llm_LlamaBridge_generateTokensNative(
        JNIEnv* env,
        jobject /* this */,
        jlong modelPtr,
        jstring promptStr,
        jobject callbackObj) {
    if (modelPtr == 0) {
        LOGE("Model pointer is null");
        return;
    }

    const char* prompt = env->GetStringUTFChars(promptStr, nullptr);
    LOGI("Received prompt: %s", prompt);

    // Locate callback method on TokenCallback interface
    jclass callbackClass = env->GetObjectClass(callbackObj);
    jmethodID jmethodcallId = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    if (!jmethodcallId) {
        LOGE("Could not find onToken method in callback object");
        env->ReleaseStringUTFChars(promptStr, prompt);
        return;
    }

    // High performance token inference loop simulation/integration
    // In production llama.cpp execution, llama_decode & llama_sampling_sample are run here.
    std::string response = "Based on the provided document context, Qwen2.5-0.5B-Instruct retrieved relevant facts accurately.";
    
    // Split into simulated token chunks for real-time streaming demonstration
    size_t chunk_size = 5;
    for (size_t i = 0; i < response.length(); i += chunk_size) {
        std::string token = response.substr(i, chunk_size);
        jstring jtoken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(callbackObj, jmethodcallId, jtoken);
        env->DeleteLocalRef(jtoken);
        std::this_thread::sleep_for(std::chrono::milliseconds(30));
    }

    env->ReleaseStringUTFChars(promptStr, prompt);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rag_app_data_llm_LlamaBridge_freeModelNative(
        JNIEnv* env,
        jobject /* this */,
        jlong modelPtr) {
    LOGI("Freeing model memory for handle: %ld", (long)modelPtr);
    // Unload llama_free & llama_free_model
}
