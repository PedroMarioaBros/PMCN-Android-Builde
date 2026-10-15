package br.com.pmcn.transcritor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Entrada de compartilhamento. O motor de reconhecimento ainda será integrado. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var result: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 48, 36, 24) }
        layout.addView(TextView(this).apply { text = "Transcritor PT-BR"; textSize = 25f; setTypeface(null, Typeface.BOLD) })
        status = TextView(this).apply { textSize = 16f; text = "Compartilhe um áudio do WhatsApp para começar."; setPadding(0,24,0,24) }
        result = TextView(this).apply { text = "Transcrição ainda indisponível nesta versão inicial."; textSize = 16f }
        layout.addView(status)
        layout.addView(result)
        layout.addView(Button(this).apply { text = "Copiar transcrição"; setOnClickListener {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Transcrição", result.text))
        } })
        setContentView(layout)
        receive(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    private fun receive(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        status.text = if (uri == null) "Nenhum arquivo de áudio foi recebido." else "Áudio recebido: ${uri.lastPathSegment ?: "arquivo"}"
        // Próxima etapa: abrir ContentResolver.openInputStream(uri), decodificar OPUS e transcrever com whisper.cpp.
    }
}
