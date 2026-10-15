#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <memory>
#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_pmcn_transcritor_WhisperBridge_transcribe(JNIEnv *env, jobject, jstring modelPath, jstring pcmPath) {
    try {
        const char *path = env->GetStringUTFChars(modelPath, nullptr);
        if (!path) return nullptr;
        whisper_context_params cp = whisper_context_default_params();
        cp.use_gpu = false;
        std::unique_ptr<whisper_context, decltype(&whisper_free)> ctx(whisper_init_from_file_with_params(path, cp), whisper_free);
        env->ReleaseStringUTFChars(modelPath, path);
        if (!ctx) return env->NewStringUTF("ERRO: não foi possível abrir o modelo.");
        const char *audio = env->GetStringUTFChars(pcmPath, nullptr);
        if (!audio) return nullptr;
        std::ifstream input(audio, std::ios::binary);
        env->ReleaseStringUTFChars(pcmPath, audio);
        if (!input) return env->NewStringUTF("ERRO: não foi possível ler o áudio decodificado.");
        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.language = "pt";
        params.translate = false;
        params.no_context = true;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.n_threads = 4;
        // Bounded working memory: five minutes of mono 16 kHz PCM at a time.
        std::vector<float> samples(16000 * 300);
        std::string text;
        while (input) {
            input.read(reinterpret_cast<char *>(samples.data()), samples.size() * sizeof(float));
            const int count = static_cast<int>(input.gcount() / sizeof(float));
            if (!count) break;
            if (whisper_full(ctx.get(), params, samples.data(), count) != 0)
                return env->NewStringUTF("ERRO: falha no processamento do áudio.");
            for (int i = 0; i < whisper_full_n_segments(ctx.get()); ++i)
                text += whisper_full_get_segment_text(ctx.get(), i);
            text += "\n";
        }
        // Whisper emits UTF-8; Java String(byte[], UTF-8) handles supplementary characters.
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
        if (!bytes) return nullptr;
        env->SetByteArrayRegion(bytes, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
        jclass str = env->FindClass("java/lang/String");
        jmethodID ctor = env->GetMethodID(str, "<init>", "([BLjava/lang/String;)V");
        jstring utf = env->NewStringUTF("UTF-8");
        return static_cast<jstring>(env->NewObject(str, ctor, bytes, utf));
    } catch (const std::bad_alloc &) {
        return env->NewStringUTF("ERRO: memória insuficiente. Feche outros aplicativos e tente novamente.");
    } catch (...) {
        return env->NewStringUTF("ERRO: falha no motor de transcrição.");
    }
}
