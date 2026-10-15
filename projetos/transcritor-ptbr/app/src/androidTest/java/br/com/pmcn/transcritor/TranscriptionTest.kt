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
    fun testShareAndCopy() {
        val context = instrumentation.targetContext
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            setClass(context, MainActivity::class.java)
            type = "audio/ogg"
            putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse("content://br.com.pmcn.transcritor.test.audio/sample"))
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val activity = instrumentation.startActivitySync(intent)
        fun labels(view: android.view.View): List<android.widget.TextView> {
            if (view is android.view.ViewGroup) return (0 until view.childCount).flatMap { labels(view.getChildAt(it)) }
            return if (view is android.widget.TextView) listOf(view) else emptyList()
        }
        var complete = false
        val deadline = android.os.SystemClock.elapsedRealtime() + 240000
        while (!complete && android.os.SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                complete = labels(activity.window.decorView).any { it.tag == true && it.text.contains("texto", ignoreCase = true) }
            }
            if (!complete) Thread.sleep(500)
        }
        assertTrue("Compartilhamento deve produzir transcrição", complete)
        instrumentation.runOnMainSync {
            labels(activity.window.decorView).first { it.text.toString() == "Copiar texto" }.performClick()
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertTrue(clipboard.primaryClip!!.getItemAt(0).text.contains("texto", ignoreCase = true))
            activity.finish()
        }
    }
}
