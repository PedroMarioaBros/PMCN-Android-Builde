#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_pmcn_transcritor_WhisperBridge_transcribe(JNIEnv *env, jobject, jstring modelPath, jfloatArray pcm) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) return env->NewStringUTF("ERRO: não foi possível abrir o modelo.");
    jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "pt";
    params.translate = false;
    params.no_context = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.n_threads = 4;
    int status = whisper_full(ctx, params, samples.data(), (int)n);
    std::string text;
    if (status == 0) {
        int segments = whisper_full_n_segments(ctx);
        for (int i=0; i<segments; ++i) text += whisper_full_get_segment_text(ctx, i);
    } else text = "ERRO: falha no processamento do áudio.";
    whisper_free(ctx);
    return env->NewStringUTF(text.c_str());
}
