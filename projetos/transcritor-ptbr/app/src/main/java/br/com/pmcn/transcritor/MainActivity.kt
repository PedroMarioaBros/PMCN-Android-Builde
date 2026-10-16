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
    private val models = arrayOf("gemini-3.8-flash")
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
                val keys = (1..3).mapNotNull { i ->
                    prefs.getString("gemini_key_$i", if (i==1) prefs.getString("gemini_key","") else "")
                        ?.trim()?.takeIf { it.isNotEmpty() }?.let { i to it }
                }
                if (keys.isEmpty()) error("Cadastre ao menos uma chave em Configurações.")
                val model = "gemini-3.8-flash"
                var text = ""
                var succeeded = false
                val failures = mutableListOf<String>()
                for ((index,entry) in keys.withIndex()) {
                    val (slot,key) = entry
                    runOnUiThread { status.text = "Transcrevendo: conta $slot (${index+1}/${keys.size})" }
                    try {
                        text = transcribeGemini(selected,key,model)
                        succeeded = true
                        break
                    } catch (e: Exception) {
                        val message=e.message.orEmpty()
                        val code=Regex("HTTP (\\d{3})").find(message)?.groupValues?.get(1) ?: "conexão"
                        failures.add("Conta $slot: $code - ${message.take(130)}")
                        if (code == "429") break
                        if (code !in listOf("401","403","500","502","503","504","conexão")) break
                    }
                }
                if (!succeeded) error("Não foi possível transcrever. Diagnóstico: " + failures.joinToString("; "))
                runOnUiThread {
                    result.text = text.ifBlank { "Nenhuma fala reconhecida." }
                    result.tag = text.isNotBlank()
                    status.text = "Processamento concluído"
                }
            } catch(e:Throwable) {
                runOnUiThread {
                    status.text="Falha ao processar áudio"
                    result.text=e.message ?: "Não foi possível processar o áudio."
                }
            } finally {
                runOnUiThread { busy=false; window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
        }.start()
    }
    private fun showSettings() {
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(12)) }
        val fields=(1..3).map { i ->
            panel.addView(label("CHAVE API GEMINI $i",12f,green,true))
            val field=EditText(this).apply {
                hint="Cole a chave da conta $i"; setSingleLine(true)
                inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(prefs.getString("gemini_key_$i",if(i==1) prefs.getString("gemini_key","") else ""))
            }
            panel.addView(field)
            val feedback=label("Não testada",12f,Color.GRAY)
            val test=Button(this).apply {
                text="Testar chave $i"; isAllCaps=false
                setOnClickListener {
                    val key=field.text.toString().trim()
                    if(key.isBlank()) { feedback.text="Informe uma chave primeiro"; return@setOnClickListener }
                    feedback.text="Testando geração…"; isEnabled=false
                    val button=this
                    val model=prefs.getString("gemini_model","gemini-3.8-flash") ?: "gemini-3.8-flash"
                    Thread {
                        val message=try { checkGeminiKey(key,model) }
                            catch(e:Exception) { "Falha de conexão: ${e.message.orEmpty().take(100)}" }
                        runOnUiThread { feedback.text=message; button.isEnabled=true }
                    }.start()
                }
            }
            panel.addView(test); panel.addView(feedback)
            field
        }
        panel.addView(label("MODELO GEMINI",12f,green,true))
        val spinner=Spinner(this)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,models)
        spinner.setSelection(models.indexOf(prefs.getString("gemini_model","gemini-3.8-flash")).coerceAtLeast(0))
        panel.addView(spinner)
        panel.addView(label("Áudios são enviados ao Google. Os limites de uso da API devem ser respeitados.",12f,Color.GRAY))
        val scroll=ScrollView(this).apply { addView(panel) }
        android.app.AlertDialog.Builder(this).setTitle("Configurações").setView(scroll)
            .setNegativeButton("Cancelar",null).setPositiveButton("Salvar") { _,_ ->
                val editor=prefs.edit()
                fields.forEachIndexed { i,field -> editor.putString("gemini_key_${i+1}",field.text.toString().trim()) }
                editor.putString("gemini_model",spinner.selectedItem.toString()).apply()
                Toast.makeText(this,"Configurações salvas",Toast.LENGTH_SHORT).show()
            }.show()
    }
    private fun checkGeminiKey(key:String,model:String):String {
        val conn=URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST"; conn.connectTimeout=15000; conn.readTimeout=30000; conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json")
            conn.setRequestProperty("x-goog-api-key",key)
            val payload=JSONObject().put("contents",org.json.JSONArray().put(
                JSONObject().put("parts",org.json.JSONArray().put(JSONObject().put("text","Responda apenas OK.")))))
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code=conn.responseCode
            return when(code) {
                200 -> "Teste real aprovado: chave e geração de texto funcionando (áudio não testado)."
                400 -> "Requisição rejeitada (HTTP 400)."
                401,403 -> "Chave inválida ou sem permissão (HTTP $code)."
                404 -> "Modelo indisponível (HTTP 404)."
                429 -> "Chave reconhecida, mas geração limitada por cota (HTTP 429)."
                500,502,503,504 -> "Serviço temporariamente indisponível (HTTP $code)."
                else -> "Falha no teste: HTTP $code."
            }
        } finally { conn.disconnect() }
    }
    private fun transcribeGemini(uri:Uri,key:String,model:String):String {
        val bytes=contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Não foi possível ler o áudio.")
        if(bytes.size>18_000_000) error("Áudio acima do limite de envio direto (18 MB).")
        val mime=contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/ogg"
        val parts=org.json.JSONArray().put(JSONObject().put("text","Transcreva integralmente este áudio em português brasileiro. Responda somente com a transcrição, com pontuação natural. Não resuma, não traduza e não invente palavras. Para trechos impossíveis de compreender, use [inaudível]."))
            .put(JSONObject().put("inline_data",JSONObject().put("mime_type",mime).put("data",Base64.encodeToString(bytes,Base64.NO_WRAP))))
        val payload=JSONObject().put("contents",org.json.JSONArray().put(JSONObject().put("parts",parts)))
        val conn=URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST"; conn.connectTimeout=20000; conn.readTimeout=120000; conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json"); conn.setRequestProperty("x-goog-api-key",key)
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code=conn.responseCode
            val response=(if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if(code !in 200..299) error("Gemini HTTP $code: "+JSONObject(response).optJSONObject("error")?.optString("message").orEmpty().take(240))
            val returned=JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            return (0 until returned.length()).joinToString("\\n") { returned.getJSONObject(it).optString("text") }.trim()
        } finally { conn.disconnect() }
    }
    private fun setAudio(uri:Uri?) {
        if(busy) { Toast.makeText(this,"Aguarde a transcrição atual terminar.",Toast.LENGTH_SHORT).show(); return }
        audio=uri
        if(uri==null) { status.text="Nenhum arquivo de áudio recebido"; return }
        try { contentResolver.openInputStream(uri)?.use { }; status.text="Áudio pronto para processar" }
        catch(e:Exception) { status.text="Não foi possível abrir o áudio: ${e.localizedMessage}" }
    }
}
