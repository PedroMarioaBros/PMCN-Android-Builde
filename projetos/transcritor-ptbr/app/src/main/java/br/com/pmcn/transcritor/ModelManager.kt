package br.com.pmcn.transcritor

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelManager {
    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    private const val MODEL_SHA1 = "465707469ff3a37a2b9b8d8f89f2f99de7299dac"
    private fun valid(file: File): Boolean {
        if (!file.exists() || file.length() < 100_000_000) return false
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA1
    }
    @Synchronized fun ensure(context: Context, progress: (String) -> Unit): File {
        val model = File(context.filesDir, "ggml-base.bin")
        progress("Verificando modelo local…")
        if (valid(model)) return model
        val partial = File(context.filesDir, "ggml-base.bin.part")
        try {
            progress("Baixando modelo gratuito (uma única vez)…")
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) error("Download HTTP ${conn.responseCode}")
                conn.inputStream.use { input -> partial.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    var total = 0L
                    var n: Int
                    while (input.read(buf).also { n=it } >= 0) {
                        if(n==0) continue
                        out.write(buf,0,n); total+=n
                        if(total % (8L*1024*1024) < n) progress("Modelo: ${total/(1024*1024)} MB recebidos")
                    }
                } }
            } finally { conn.disconnect() }
            if (!valid(partial)) error("Modelo incompleto ou corrompido. Verifique a conexão e tente novamente.")
            if (!partial.renameTo(model)) error("Falha ao guardar modelo")
            return model
        } finally { partial.delete() }
    }
}
