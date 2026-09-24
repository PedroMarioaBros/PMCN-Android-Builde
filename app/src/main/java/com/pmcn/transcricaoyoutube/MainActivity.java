package com.pmcn.transcricaoyoutube;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<CaptionTrack> tracks = new ArrayList<>();

    private EditText urlInput;
    private Button analyzeButton;
    private Button extractButton;
    private Button saveButton;
    private Button shareButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView videoTitle;
    private TextView tracksLabel;
    private TextView resultInfo;
    private RadioGroup tracksGroup;
    private View resultButtons;

    private boolean engineReady = false;
    private String currentUrl;
    private String currentTitle;
    private File currentVtt;
    private CaptionTrack selectedTrack;

    private final ActivityResultLauncher<String> saveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/vtt"), uri -> {
                if (uri == null || currentVtt == null) return;
                try (InputStream in = new FileInputStream(currentVtt);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Não foi possível abrir o destino.");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    Toast.makeText(this, "Arquivo VTT salvo.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    showError("Falha ao salvar: " + e.getMessage());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        bindActions();
        initializeEngine();
        handleIncomingShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShare(intent);
    }

    private void bindViews() {
        urlInput = findViewById(R.id.urlInput);
        analyzeButton = findViewById(R.id.analyzeButton);
        extractButton = findViewById(R.id.extractButton);
        saveButton = findViewById(R.id.saveButton);
        shareButton = findViewById(R.id.shareButton);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.statusText);
        videoTitle = findViewById(R.id.videoTitle);
        tracksLabel = findViewById(R.id.tracksLabel);
        resultInfo = findViewById(R.id.resultInfo);
        tracksGroup = findViewById(R.id.tracksGroup);
        resultButtons = findViewById(R.id.resultButtons);
    }

    private void bindActions() {
        analyzeButton.setOnClickListener(v -> analyze());
        extractButton.setOnClickListener(v -> extractSelected());
        saveButton.setOnClickListener(v -> {
            if (currentVtt != null) saveLauncher.launch(currentVtt.getName());
        });
        shareButton.setOnClickListener(v -> shareCurrentFile());
    }

    private void initializeEngine() {
        setBusy(true, "Preparando o mecanismo de compatibilidade...");
        executor.submit(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                engineReady = true;
                runOnUiThread(() -> {
                    setBusy(false, "Pronto. Cole um link ou compartilhe um vídeo do YouTube para este aplicativo.");
                    maybeAutoAnalyze();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Falha ao iniciar o mecanismo: " + e.getMessage()));
            }
        });
    }

    private void handleIncomingShare(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            String url = extractYoutubeUrl(text);
            if (url != null) {
                urlInput.setText(url);
                maybeAutoAnalyze();
            }
        }
    }

    private void maybeAutoAnalyze() {
        if (engineReady && !TextUtils.isEmpty(urlInput.getText().toString().trim())) analyze();
    }

    private String extractYoutubeUrl(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("https?://(?:www\\.)?(?:youtube\\.com/[^\\s]+|youtu\\.be/[^\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
    }

    private void analyze() {
        if (!engineReady) {
            Toast.makeText(this, "O mecanismo ainda está iniciando.", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            urlInput.setError("Cole um link do YouTube.");
            return;
        }

        currentUrl = url;
        currentVtt = null;
        resultButtons.setVisibility(View.GONE);
        resultInfo.setVisibility(View.GONE);
        tracksGroup.removeAllViews();
        tracks.clear();
        selectedTrack = null;
        extractButton.setEnabled(false);
        setBusy(true, "Consultando as faixas que já existem no vídeo...");

        executor.submit(() -> {
            try {
                JSONObject info = getVideoJson(url);
                currentTitle = info.optString("title", "Vídeo do YouTube");
                List<CaptionTrack> parsed = parseTracks(info);
                runOnUiThread(() -> renderTracks(parsed));
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private JSONObject getVideoJson(String url) throws Exception {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--skip-download");
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        request.addOption("--quiet");
        request.addOption("--dump-single-json");
        String out = YoutubeDL.getInstance().execute(request).getOut();
        if (out == null) throw new IllegalStateException("Resposta vazia do YouTube.");
        int start = out.indexOf('{');
        int end = out.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalStateException("Não foi possível interpretar as informações do vídeo.");
        return new JSONObject(out.substring(start, end + 1));
    }

    private List<CaptionTrack> parseTracks(JSONObject info) {
        Map<String, CaptionTrack> unique = new LinkedHashMap<>();
        JSONObject manual = info.optJSONObject("subtitles");
        if (manual != null) addManualTracks(manual, unique);

        JSONObject automatic = info.optJSONObject("automatic_captions");
        if (automatic != null) addOriginalAutomaticTracks(automatic, unique, info.optString("language", ""));

        List<CaptionTrack> out = new ArrayList<>(unique.values());
        Collections.sort(out, Comparator
                .comparing((CaptionTrack t) -> !t.isPortuguese())
                .thenComparing(t -> t.automatic)
                .thenComparing(t -> t.code.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void addManualTracks(JSONObject obj, Map<String, CaptionTrack> out) {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String code = keys.next();
            JSONArray formats = obj.optJSONArray(code);
            if (!hasAnyFormat(formats)) continue;
            out.put("manual:" + code, new CaptionTrack(code, "Legenda enviada pelo canal", false));
        }
    }

    private void addOriginalAutomaticTracks(JSONObject obj, Map<String, CaptionTrack> out, String videoLanguage) {
        List<String> allCodes = new ArrayList<>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) allCodes.add(keys.next());

        boolean hasOrig = false;
        for (String code : allCodes) {
            if (code.endsWith("-orig")) {
                hasOrig = true;
                break;
            }
        }

        if (hasOrig) {
            for (String code : allCodes) {
                if (!code.endsWith("-orig")) continue;
                JSONArray formats = obj.optJSONArray(code);
                if (!hasAnyFormat(formats)) continue;
                out.put("auto:" + code, new CaptionTrack(code, "Transcrição automática original do YouTube", true));
            }
            return;
        }

        if (videoLanguage != null && !videoLanguage.trim().isEmpty()) {
            for (String code : allCodes) {
                if (!code.equalsIgnoreCase(videoLanguage)) continue;
                JSONArray formats = obj.optJSONArray(code);
                if (!hasAnyFormat(formats)) continue;
                out.put("auto:" + code, new CaptionTrack(code, "Transcrição automática original do YouTube", true));
            }
            if (!out.isEmpty()) return;
        }

        for (String code : allCodes) {
            String low = code.toLowerCase(Locale.ROOT);
            if (!(low.equals("pt") || low.startsWith("pt-"))) continue;
            JSONArray formats = obj.optJSONArray(code);
            if (!hasAnyFormat(formats)) continue;
            out.put("auto:" + code, new CaptionTrack(code, "Transcrição automática do YouTube", true));
        }
    }

    private boolean hasAnyFormat(JSONArray formats) {
        return formats != null && formats.length() > 0;
    }

    private void renderTracks(List<CaptionTrack> parsed) {
        setBusy(false, parsed.isEmpty()
                ? "Este vídeo não possui uma transcrição disponível no YouTube."
                : "Transcrições encontradas: " + parsed.size());

        videoTitle.setText(currentTitle);
        videoTitle.setVisibility(View.VISIBLE);
        tracksLabel.setVisibility(parsed.isEmpty() ? View.GONE : View.VISIBLE);
        tracksGroup.removeAllViews();
        tracks.clear();
        tracks.addAll(parsed);

        if (parsed.isEmpty()) {
            extractButton.setEnabled(false);
            return;
        }

        int defaultIndex = 0;
        for (int i = 0; i < parsed.size(); i++) {
            if (parsed.get(i).isPortuguese()) {
                defaultIndex = i;
                break;
            }
        }

        for (int i = 0; i < parsed.size(); i++) {
            CaptionTrack t = parsed.get(i);
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(t.displayName());
            rb.setTag(i);
            rb.setPadding(0, 8, 0, 8);
            tracksGroup.addView(rb);
            if (i == defaultIndex) rb.setChecked(true);
        }

        selectedTrack = parsed.get(defaultIndex);
        extractButton.setEnabled(true);
        tracksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = group.findViewById(checkedId);
            if (rb != null && rb.getTag() instanceof Integer) {
                selectedTrack = tracks.get((Integer) rb.getTag());
            }
        });
    }

    private void extractSelected() {
        if (selectedTrack == null || currentUrl == null) return;
        setBusy(true, "Baixando somente " + selectedTrack.code + " em VTT...");
        resultButtons.setVisibility(View.GONE);
        resultInfo.setVisibility(View.GONE);

        executor.submit(() -> {
            try {
                File dir = new File(getCacheDir(), "captions");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("Não foi possível preparar a pasta temporária.");
                }

                File[] old = dir.listFiles();
                if (old != null) {
                    for (File f : old) f.delete();
                }

                YoutubeDLRequest request = new YoutubeDLRequest(currentUrl);
                request.addOption("--skip-download");
                request.addOption("--no-playlist");
                request.addOption("--write-subs");
                request.addOption("--write-auto-subs");
                request.addOption("--sub-langs", selectedTrack.code);
                request.addOption("--sub-format", "vtt");
                request.addOption("--no-warnings");
                request.addOption("-P", dir.getAbsolutePath());
                request.addOption("-o", "%(title).160B [%(id)s].%(ext)s");

                YoutubeDL.getInstance().execute(request);
                File vtt = newestVtt(dir);
                if (vtt == null) {
                    throw new IllegalStateException("O YouTube informou a faixa, mas não entregou o arquivo VTT.");
                }
                currentVtt = vtt;

                runOnUiThread(() -> {
                    setBusy(false, "Concluído. Nenhum vídeo ou áudio foi baixado.");
                    resultInfo.setText("Arquivo: " + vtt.getName() + "\nTamanho: " + humanSize(vtt.length()));
                    resultInfo.setVisibility(View.VISIBLE);
                    resultButtons.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private File newestVtt(File dir) {
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".vtt"));
        if (files == null || files.length == 0) return null;
        File newest = files[0];
        for (File f : files) {
            if (f.lastModified() > newest.lastModified()) newest = f;
        }
        return newest;
    }

    private void shareCurrentFile() {
        if (currentVtt == null) return;
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", currentVtt);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/vtt");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Compartilhar transcrição"));
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        analyzeButton.setEnabled(!busy && engineReady);
        extractButton.setEnabled(!busy && selectedTrack != null);
        statusText.setText(message);
    }

    private void showError(String message) {
        setBusy(false, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String friendlyError(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.toString();
        String low = m.toLowerCase(Locale.ROOT);
        if (low.contains("unable to resolve host") || low.contains("network")
                || low.contains("urlopen") || low.contains("no address associated")) {
            return "Sem acesso à internet neste momento. Verifique a conexão e tente novamente.";
        }
        if (low.contains("private video")) return "Este vídeo é privado.";
        if (low.contains("sign in") || low.contains("login")) {
            return "Este vídeo exige login no YouTube e não pode ser acessado anonimamente.";
        }
        if (low.contains("unsupported url")) {
            return "O link informado não foi reconhecido como um vídeo compatível.";
        }
        return "Erro ao acessar a transcrição: " + m;
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class CaptionTrack {
        final String code;
        final String source;
        final boolean automatic;

        CaptionTrack(String code, String source, boolean automatic) {
            this.code = code;
            this.source = source;
            this.automatic = automatic;
        }

        boolean isPortuguese() {
            String c = code.toLowerCase(Locale.ROOT).replace("-orig", "");
            return c.equals("pt") || c.startsWith("pt-");
        }

        String displayName() {
            return languageLabel(code) + "  •  " + source + "  •  " + code;
        }

        static String languageLabel(String code) {
            String normalized = code.replace("-orig", "");
            Locale locale = Locale.forLanguageTag(normalized);
            String name = locale.getDisplayName(new Locale("pt", "BR"));
            if (name == null || name.trim().isEmpty() || name.equalsIgnoreCase(normalized)) {
                return normalized;
            }
            return name.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + name.substring(1);
        }
    }
}
