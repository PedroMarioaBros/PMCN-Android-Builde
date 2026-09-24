package com.pmcn.transcricaoyoutube;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<CaptionTrack> tracks = new ArrayList<>();

    private EditText urlInput;
    private Button analyzeButton;
    private Button extractButton;
    private Button exportPackageButton;
    private Button saveButton;
    private Button shareButton;
    private Button savePackageButton;
    private Button sharePackageButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView videoTitle;
    private TextView metaText;
    private TextView descriptionLabel;
    private TextView descriptionText;
    private TextView tracksLabel;
    private TextView resultInfo;
    private TextView packageInfo;
    private RadioGroup tracksGroup;
    private ImageView thumbnailView;
    private View resultButtons;
    private View packageButtons;

    private boolean engineReady = false;
    private String currentUrl;
    private String currentTitle;
    private JSONObject currentInfo;
    private File currentVtt;
    private File currentPackageZip;
    private CaptionTrack selectedTrack;

    private final ActivityResultLauncher<String> vttSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/vtt"), uri -> {
                if (uri != null && currentVtt != null) copyToUri(currentVtt, uri, "Arquivo VTT salvo.");
            });

    private final ActivityResultLauncher<String> zipSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                if (uri != null && currentPackageZip != null) copyToUri(currentPackageZip, uri, "Pacote ZIP salvo.");
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
        exportPackageButton = findViewById(R.id.exportPackageButton);
        saveButton = findViewById(R.id.saveButton);
        shareButton = findViewById(R.id.shareButton);
        savePackageButton = findViewById(R.id.savePackageButton);
        sharePackageButton = findViewById(R.id.sharePackageButton);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.statusText);
        videoTitle = findViewById(R.id.videoTitle);
        metaText = findViewById(R.id.metaText);
        descriptionLabel = findViewById(R.id.descriptionLabel);
        descriptionText = findViewById(R.id.descriptionText);
        tracksLabel = findViewById(R.id.tracksLabel);
        resultInfo = findViewById(R.id.resultInfo);
        packageInfo = findViewById(R.id.packageInfo);
        tracksGroup = findViewById(R.id.tracksGroup);
        thumbnailView = findViewById(R.id.thumbnailView);
        resultButtons = findViewById(R.id.resultButtons);
        packageButtons = findViewById(R.id.packageButtons);
    }

    private void bindActions() {
        analyzeButton.setOnClickListener(v -> analyze());
        extractButton.setOnClickListener(v -> extractSelected());
        exportPackageButton.setOnClickListener(v -> exportAnalysisPackage());
        saveButton.setOnClickListener(v -> {
            if (currentVtt != null) vttSaveLauncher.launch(currentVtt.getName());
        });
        shareButton.setOnClickListener(v -> {
            if (currentVtt != null) shareFile(currentVtt, "text/vtt", "Compartilhar transcrição");
        });
        savePackageButton.setOnClickListener(v -> {
            if (currentPackageZip != null) zipSaveLauncher.launch(currentPackageZip.getName());
        });
        sharePackageButton.setOnClickListener(v -> {
            if (currentPackageZip != null) shareFile(currentPackageZip, "application/zip", "Compartilhar pacote para análise");
        });
    }

    private void initializeEngine() {
        setBusy(true, "Preparando o mecanismo...");
        executor.submit(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                engineReady = true;
                runOnUiThread(() -> {
                    setBusy(false, "Pronto. Cole um link ou compartilhe um vídeo do YouTube.");
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
        currentPackageZip = null;
        currentInfo = null;
        selectedTrack = null;
        tracks.clear();
        tracksGroup.removeAllViews();
        hideResults();
        setBusy(true, "Buscando título, capa, descrição, metadados e transcrições...");

        executor.submit(() -> {
            try {
                JSONObject info = getVideoJson(url);
                currentInfo = info;
                currentTitle = info.optString("title", "Vídeo do YouTube");
                List<CaptionTrack> parsed = parseTracks(info);
                runOnUiThread(() -> {
                    renderVideoDetails(info);
                    renderTracks(parsed);
                });
                loadThumbnail(info.optString("thumbnail", ""), url);
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
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Não foi possível interpretar as informações do vídeo.");
        }
        return new JSONObject(out.substring(start, end + 1));
    }

    private void renderVideoDetails(JSONObject info) {
        videoTitle.setText(info.optString("title", "Vídeo do YouTube"));
        videoTitle.setVisibility(View.VISIBLE);

        StringBuilder meta = new StringBuilder();
        appendMeta(meta, "Canal", firstNonEmpty(info.optString("channel", ""), info.optString("uploader", "")));
        appendMeta(meta, "Data", formatUploadDate(info.optString("upload_date", "")));
        appendMeta(meta, "Duração", formatDuration(info.optLong("duration", -1)));
        appendMeta(meta, "Visualizações", formatNumber(info.optLong("view_count", -1)));
        appendMeta(meta, "Curtidas", formatNumber(info.optLong("like_count", -1)));
        appendMeta(meta, "Comentários", formatNumber(info.optLong("comment_count", -1)));
        appendMeta(meta, "ID", info.optString("id", ""));

        metaText.setText(meta.toString().trim());
        metaText.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);

        String description = info.optString("description", "");
        descriptionLabel.setVisibility(View.VISIBLE);
        descriptionText.setText(description.isEmpty() ? "(Sem descrição disponível)" : description);
        descriptionText.setVisibility(View.VISIBLE);
    }

    private void loadThumbnail(String thumbnailUrl, String expectedUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) return;

        executor.submit(() -> {
            try (InputStream input = new BufferedInputStream(new URL(thumbnailUrl).openStream())) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        if (expectedUrl.equals(currentUrl)) {
                            thumbnailView.setImageBitmap(bitmap);
                            thumbnailView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } catch (Exception ignored) {
                // A ausência da prévia não impede a extração do pacote.
            }
        });
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
                : "Dados carregados. Transcrições encontradas: " + parsed.size());

        tracksLabel.setVisibility(parsed.isEmpty() ? View.GONE : View.VISIBLE);
        tracksGroup.removeAllViews();
        tracks.clear();
        tracks.addAll(parsed);

        if (parsed.isEmpty()) {
            extractButton.setEnabled(false);
            exportPackageButton.setEnabled(false);
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
        exportPackageButton.setEnabled(true);

        tracksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = group.findViewById(checkedId);
            if (rb != null && rb.getTag() instanceof Integer) {
                selectedTrack = tracks.get((Integer) rb.getTag());
                currentVtt = null;
                currentPackageZip = null;
                resultButtons.setVisibility(View.GONE);
                resultInfo.setVisibility(View.GONE);
                packageButtons.setVisibility(View.GONE);
                packageInfo.setVisibility(View.GONE);
            }
        });
    }

    private void extractSelected() {
        if (selectedTrack == null || currentUrl == null) return;

        setBusy(true, "Baixando somente a faixa " + selectedTrack.code + " em VTT...");
        resultButtons.setVisibility(View.GONE);
        resultInfo.setVisibility(View.GONE);

        executor.submit(() -> {
            try {
                File dir = new File(getCacheDir(), "single_vtt");
                recreateDirectory(dir);

                runYtDlpForFiles(dir, false);
                File vtt = findFirstByExtension(dir, ".vtt");
                if (vtt == null) {
                    throw new IllegalStateException("O YouTube informou a faixa, mas não entregou o arquivo VTT.");
                }

                currentVtt = vtt;

                runOnUiThread(() -> {
                    setBusy(false, "VTT pronto. Nenhum vídeo ou áudio foi baixado.");
                    resultInfo.setText("Arquivo: " + vtt.getName() + "\nTamanho: " + humanSize(vtt.length()));
                    resultInfo.setVisibility(View.VISIBLE);
                    resultButtons.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private void exportAnalysisPackage() {
        if (selectedTrack == null || currentUrl == null || currentInfo == null) return;

        setBusy(true, "Montando pacote: VTT + capa + título + descrição + metadados...");
        packageButtons.setVisibility(View.GONE);
        packageInfo.setVisibility(View.GONE);

        executor.submit(() -> {
            try {
                File workDir = new File(getCacheDir(), "package_work");
                File packageDir = new File(getCacheDir(), "package_ready");
                recreateDirectory(workDir);
                recreateDirectory(packageDir);

                runYtDlpForFiles(workDir, true);

                File sourceVtt = findFirstByExtension(workDir, ".vtt");
                if (sourceVtt == null) {
                    throw new IllegalStateException("Não foi possível obter a transcrição selecionada.");
                }

                String code = sanitizeCode(selectedTrack.code);
                copyFile(sourceVtt, new File(packageDir, "transcricao." + code + ".vtt"));

                File thumbnail = findThumbnail(workDir);
                if (thumbnail != null) {
                    String ext = extensionOf(thumbnail.getName());
                    copyFile(thumbnail, new File(packageDir, "capa" + ext));
                }

                writeUtf8(new File(packageDir, "titulo.txt"), currentInfo.optString("title", ""));
                writeUtf8(new File(packageDir, "descricao.txt"), currentInfo.optString("description", ""));

                File infoJson = findFileEnding(workDir, ".info.json");
                if (infoJson != null) {
                    copyFile(infoJson, new File(packageDir, "dados.json"));
                } else {
                    writeUtf8(new File(packageDir, "dados.json"), currentInfo.toString(2));
                }

                writeUtf8(new File(packageDir, "manifesto.txt"), buildManifest());

                String id = currentInfo.optString("id", "video");
                String zipName = sanitizeFilename(currentInfo.optString("title", "video"), 70)
                        + " [" + id + "] - pacote-analise.zip";
                File zip = new File(getCacheDir(), zipName);
                if (zip.exists()) zip.delete();
                zipDirectory(packageDir, zip);

                currentPackageZip = zip;

                runOnUiThread(() -> {
                    setBusy(false, "Pacote completo pronto para análise.");
                    packageInfo.setText(
                            "Incluído no ZIP:\n"
                                    + "• transcrição VTT selecionada\n"
                                    + "• capa/thumbnail\n"
                                    + "• titulo.txt\n"
                                    + "• descricao.txt\n"
                                    + "• dados.json\n"
                                    + "• manifesto.txt\n\n"
                                    + "Tamanho: " + humanSize(zip.length())
                    );
                    packageInfo.setVisibility(View.VISIBLE);
                    packageButtons.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private void runYtDlpForFiles(File dir, boolean fullPackage) throws Exception {
        YoutubeDLRequest request = new YoutubeDLRequest(currentUrl);
        request.addOption("--skip-download");
        request.addOption("--no-playlist");
        request.addOption("--write-subs");
        request.addOption("--write-auto-subs");
        request.addOption("--sub-langs", selectedTrack.code);
        request.addOption("--sub-format", "vtt");
        request.addOption("--no-warnings");

        if (fullPackage) {
            request.addOption("--write-thumbnail");
            request.addOption("--write-description");
            request.addOption("--write-info-json");
            request.addOption("--clean-info-json");
            request.addOption("--no-write-playlist-metafiles");
        }

        request.addOption("-P", dir.getAbsolutePath());
        request.addOption("-o", "%(id)s.%(ext)s");
        YoutubeDL.getInstance().execute(request);
    }

    private String buildManifest() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        String extractedAt = sdf.format(new Date());

        return "PMCN Studios - Pacote para análise de vídeo\n"
                + "Extraído em: " + extractedAt + "\n"
                + "URL: " + currentUrl + "\n"
                + "ID do vídeo: " + currentInfo.optString("id", "") + "\n"
                + "Faixa selecionada: " + selectedTrack.code + "\n"
                + "Tipo: " + selectedTrack.source + "\n"
                + "\n"
                + "O pacote preserva os dados públicos obtidos do vídeo no momento da extração.\n"
                + "Nenhum áudio ou vídeo foi baixado para gerar a transcrição.\n";
    }

    private void hideResults() {
        thumbnailView.setVisibility(View.GONE);
        videoTitle.setVisibility(View.GONE);
        metaText.setVisibility(View.GONE);
        descriptionLabel.setVisibility(View.GONE);
        descriptionText.setVisibility(View.GONE);
        tracksLabel.setVisibility(View.GONE);
        resultButtons.setVisibility(View.GONE);
        resultInfo.setVisibility(View.GONE);
        packageButtons.setVisibility(View.GONE);
        packageInfo.setVisibility(View.GONE);
        extractButton.setEnabled(false);
        exportPackageButton.setEnabled(false);
    }

    private void recreateDirectory(File dir) {
        deleteRecursive(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Não foi possível preparar a pasta temporária.");
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    private File findFirstByExtension(File dir, String extension) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(extension)) {
                return file;
            }
        }
        return null;
    }

    private File findFileEnding(File dir, String ending) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(ending)) {
                return file;
            }
        }
        return null;
    }

    private File findThumbnail(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        String[] extensions = {".webp", ".jpg", ".jpeg", ".png"};
        for (String ext : extensions) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(ext)) return file;
            }
        }
        return null;
    }

    private void copyFile(File source, File destination) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private void writeUtf8(File file, String content) throws Exception {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            File[] files = sourceDir.listFiles();
            if (files == null) return;

            byte[] buffer = new byte[8192];
            for (File file : files) {
                if (!file.isFile()) continue;
                zos.putNextEntry(new ZipEntry(file.getName()));
                try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    int read;
                    while ((read = in.read(buffer)) != -1) zos.write(buffer, 0, read);
                }
                zos.closeEntry();
            }
        }
    }

    private void copyToUri(File source, Uri uri, String successMessage) {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Não foi possível abrir o destino.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showError("Falha ao salvar: " + e.getMessage());
        }
    }

    private void shareFile(File file, String mimeType, String chooserTitle) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mimeType);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, chooserTitle));
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        analyzeButton.setEnabled(!busy && engineReady);
        extractButton.setEnabled(!busy && selectedTrack != null);
        exportPackageButton.setEnabled(!busy && selectedTrack != null);
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
        return "Erro: " + m;
    }

    private void appendMeta(StringBuilder sb, String label, String value) {
        if (value != null && !value.trim().isEmpty()) sb.append(label).append(": ").append(value).append("\n");
    }

    private String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        return b == null ? "" : b;
    }

    private String formatUploadDate(String raw) {
        if (raw == null || raw.length() != 8) return raw == null ? "" : raw;
        return raw.substring(6, 8) + "/" + raw.substring(4, 6) + "/" + raw.substring(0, 4);
    }

    private String formatDuration(long seconds) {
        if (seconds < 0) return "";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private String formatNumber(long value) {
        if (value < 0) return "";
        return String.format(new Locale("pt", "BR"), "%,d", value);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String sanitizeCode(String code) {
        if (code == null || code.isEmpty()) return "legenda";
        return code.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sanitizeFilename(String name, int max) {
        if (name == null || name.trim().isEmpty()) name = "video";
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.length() > max) clean = clean.substring(0, max).trim();
        return clean;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : ".img";
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
            if (name == null || name.trim().isEmpty() || name.equalsIgnoreCase(normalized)) return normalized;
            return name.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + name.substring(1);
        }
    }
}
