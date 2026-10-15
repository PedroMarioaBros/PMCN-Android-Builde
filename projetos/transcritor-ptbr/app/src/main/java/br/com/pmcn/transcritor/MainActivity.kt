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

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var result: TextView
    private var audio: Uri? = null
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
        val scroll = ScrollView(this).apply { fillViewport=true; addView(page) }
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
        page.addView(label("Compartilhe um áudio do WhatsApp para receber sua transcrição. Seus arquivos permanecem no aparelho.",15f,Color.rgb(83,105,95)))
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
                addCategory(Intent.CATEGORY_OPENABLE); type="audio/*" },42) } }
        card.addView(choose,LinearLayout.LayoutParams(-1,dp(52)))
        val output=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(22),dp(20),dp(22))
            background=rounded(Color.WHITE,20); elevation=dp(2).toFloat() }
        page.addView(output,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
        output.addView(label("TRANSCRIÇÃO",12f,green,true))
        result=label("O texto transcrito aparecerá aqui quando o motor de reconhecimento estiver integrado.",15f,Color.rgb(90,105,98))
        result.setPadding(0,dp(16),0,dp(16)); output.addView(result)
        val copy=Button(this).apply { text="Copiar texto"; isAllCaps=false
            setOnClickListener {
                if (result.tag != true) { Toast.makeText(this@MainActivity,"Ainda não há transcrição para copiar.",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Transcrição",result.text))
                Toast.makeText(this@MainActivity,"Texto copiado",Toast.LENGTH_SHORT).show()
            } }
        output.addView(copy)
        page.addView(label("100% gratuito • Português • Sem envio de mensagens",12f,Color.rgb(103,121,110)).apply {
            gravity=Gravity.CENTER; setPadding(0,dp(28),0,0) })
        receive(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    @Deprecated("Legacy document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==42 && resultCode==RESULT_OK) setAudio(data?.data)
    }
    private fun receive(intent: Intent?) {
        if(intent?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = if(Build.VERSION.SDK_INT>=33)
            intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        setAudio(uri)
    }
    private fun setAudio(uri: Uri?) {
        audio=uri
        if(uri==null) { status.text="Nenhum arquivo de áudio recebido"; return }
        try { contentResolver.openInputStream(uri)?.use { it.read() }
            status.text="Áudio pronto para processar"
        } catch(e:Exception) { status.text="Não foi possível abrir o áudio: ${e.localizedMessage}" }
        // Motor Whisper.cpp e decodificação OPUS ainda não implementados.
    }
}
