package br.com.pmcn.transcritor

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var result: TextView
    private var audio: Uri? = null
    private val prefs by lazy { getPreferences(MODE_PRIVATE) }
    private val models = arrayOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite")
    @Volatile private var busy = false
    private val green = Color.rgb(23,107,85)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply { this.text=text; textSize=size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245,248,246)
        window.navigationBarColor = Color.rgb(245,248,246)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val page = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL
            setPadding(dp(24),dp(32),dp(24),dp(20)); setBackgroundColor(Color.rgb(245,248,246)) }
        val scroll = ScrollView(this).apply { isFillViewport=true; addView(page) }
        setContentView(scroll)
        val header = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val mark = ImageView(this).apply { setImageResource(R.drawable.ic_brand); background=rounded(green,16) }
        header.addView(mark, LinearLayout.LayoutParams(dp(58),dp(58)))
        val titles = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),0,0,0) }
        titles.addView(label("Voz em Texto",24f,Color.rgb(21,46,39),true))
        titles.addView(label("Transcritor de áudios • PT-BR",13f,Color.rgb(100,119,109)))
        header.addView(titles)
        page.addView(header)
        val intro = label("Suas mensagens, em palavras.",23f,Color.rgb(21,46,39),true)
        intro.setPadding(0,dp(42),0,dp(10)); page.addView(intro)
        page.addView(label("Compartilhe um áudio do WhatsApp para receber sua transcrição. O áudio é enviado ao Google Gemini para transcrição.",15f,Color.rgb(83,105,95)))
        val configure = Button(this).apply {
            text = "⚙ Configurações"; isAllCaps = false
            setOnClickListener { showSettings() }
        }
        page.addView(configure, LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(18) })
        val card = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(22),dp(20),dp(22))
            background=rounded(Color.WHITE,20); elevation=dp(2).toFloat() }
        val params = LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(28) }
        page.addView(card,params)
        card.addView(label("ÁUDIO RECEBIDO",12f,green,true))
        status=label("Nenhum áudio selecionado",16f,Color.rgb(35,60,50),true).apply { setPadding(0,dp(14),0,dp(14)) }
        card.addView(status)
        val choose = Button(this).apply { text="Escolher arquivo de áudio"; setTextColor(Color.WHITE)
            background=rounded(green,14); isAllCaps=false
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type="*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/ogg")) },42) } }
        card.addView(choose,LinearLayout.LayoutParams(-1,dp(52)))
        val output=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(22),dp(20),dp(22))
            background=rounded(Color.WHITE,20); elevation=dp(2).toFloat() }
        page.addView(output,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
        output.addView(label("TRANSCRIÇÃO",12f,green,true))
        result=label("Selecione ou compartilhe um áudio. A transcrição completa aparecerá aqui.",15f,Color.rgb(90,105,98))
        result.setTextIsSelectable(true)
        result.setPadding(0,dp(16),0,dp(16)); output.addView(result)
        val transcribe = Button(this).apply {
            text = "Transcrever áudio"; isAllCaps = false
            setTextColor(Color.WHITE); background = rounded(green,14)
            setOnClickListener { processAudio() }
        }
        output.addView(transcribe,LinearLayout.LayoutParams(-1,dp(52)))
        val copy=Button(this).apply { text="Copiar texto"; isAllCaps=false
            setOnClickListener {
                if (result.tag != true) { Toast.makeText(this@MainActivity,"Ainda não há transcrição para copiar.",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Transcrição",result.text))
                Toast.makeText(this@MainActivity,"Texto copiado",Toast.LENGTH_SHORT).show()
            } }
        output.addView(copy)
        page.addView(label("Português • Requer internet • Sujeito aos limites da API Gemini",12f,Color.rgb(103,121,110)).apply {
            gravity=Gravity.CENTER; setPadding(0,dp(28),0,0) })
        receive(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    @Deprecated("Legacy document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==42 && resultCode==RESULT_OK) { setAudio(data?.data); if (audio != null) processAudio() }
    }
    private fun receive(intent: Intent?) {
        if(intent?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = if(Build.VERSION.SDK_INT>=33)
            intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        setAudio(uri)
        if (uri != null) processAudio()
    }
    private fun processAudio() {
        val selected = audio ?: run {
            Toast.makeText(this,"Selecione um áudio primeiro",Toast.LENGTH_SHORT).show(); return
        }
        if (busy) return
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        busy = true
        result.tag = false
        result.text = "Preparando transcrição…"
        Thread {
            try {
                val keys = (1..3).map { prefs.getString("gemini_key_$it", "")!!.trim() }.filter { it.isNotBlank() }
                if (keys.isEmpty()) error("Cadastre ao menos uma chave em Configurações.")
                val model = prefs.getString("gemini_model","gemini-3.8-flash") ?: "gemini-3.8-flash"
                runOnUiThread { status.text = "Enviando áudio ao Gemini…" }
                var text = ""
                var lastError: Exception? = null
                // A troca automática de credenciais é reservada a falhas de autenticação.
                // Cotas (429) não provocam rotação de contas para contornar limites.
                for ((index,key) in keys.withIndex()) {
                    var authFailure = false
                    for (attempt in 1..4) {
                        try {
                            text = transcribeGemini(selected,key,model)
                            lastError = null
                            break
                        } catch (e: Exception) {
                            lastError = e
                            val message = e.message.orEmpty()
                            if (message.contains("HTTP 401") || message.contains("HTTP 403")) {
                                authFailure = true
                                break
                            }
                            val retryable = message.contains("HTTP 503") || message.contains("HTTP 502") || message.contains("HTTP 429")
                            if (!retryable || attempt == 4) break
                            runOnUiThread { status.text = "Gemini ocupado. Tentativa ${attempt + 1} de 4…" }
                            Thread.sleep(attempt * attempt * 1500L)
                        }
                    }
                    if (lastError == null) break
                    if (!authFailure || index == keys.lastIndex) break
                }
                lastError?.let { throw it }

