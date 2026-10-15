package br.com.pmcn.transcritor

import android.test.InstrumentationTestCase
import android.net.Uri
import java.io.File

class TranscriptionTest : InstrumentationTestCase() {
    fun testOpusPortugueseOffline() {
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "teste.opus")
        instrumentation.context.assets.open("teste.opus").use { input -> source.outputStream().use { input.copyTo(it) } }
        val decoded = AudioDecoder.decode(context, Uri.fromFile(source))
        try {
            assertTrue("PCM foi decodificado", decoded.length() > 16000 * 4)
            val model = ModelManager.ensure(context) { }
            val text = WhisperBridge.transcribe(model.absolutePath, decoded.absolutePath)
            android.util.Log.i("VozEmTextoTest", "TRANSCRICAO: " + text)
            assertFalse(text.startsWith("ERRO:"))
            assertTrue("Texto reconhecido: " + text, text.lowercase().contains("texto"))
            assertTrue("Texto reconhecido: " + text, text.lowercase().contains("portugu"))
        } finally { decoded.delete(); source.delete() }
    }
    fun testInvalidAudio() {
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "invalid.bin")
        source.writeText("Este arquivo não é um áudio")
        try { AudioDecoder.decode(context, Uri.fromFile(source)); fail("Arquivo inválido foi aceito") }
        catch (_: Exception) { } finally { source.delete() }
    }
}
