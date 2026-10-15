package br.com.pmcn.transcritor

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelManager {
    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    fun ensure(context: Context, progress: (String) -> Unit): File {
        val model = File(context.filesDir, "ggml-base.bin")
        if (model.exists() && model.length() > 100_000_000) return model
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
            if (partial.length() < 100_000_000) error("Modelo incompleto")
            if (!partial.renameTo(model)) error("Falha ao guardar modelo")
            return model
        } finally { partial.delete() }
    }
}
