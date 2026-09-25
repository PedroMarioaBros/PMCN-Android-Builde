package com.pmcn.transcricaoyoutube;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.button.MaterialButton;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int MODE_ANALYZE = 0;
    private static final int MODE_VIDEO = 1;
    private static final int MODE_AUDIO = 2;
    private static final int MODE_BATCH = 3;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<CaptionTrack> tracks = new ArrayList<>();

    private EditText urlInput;
    private EditText batchInput;
    private MaterialButton modeAnalyze;
    private MaterialButton modeVideo;
    private MaterialButton modeAudio;
    private MaterialButton modeBatch;
    private View urlCard;
    private View analyzePanel;
    private View videoPanel;
    private View audioPanel;
    private View batchPanel;
    private View videoInfoCard;
    private View mediaResultCard;
    private View vttResultButtons;
    private View packageResultButtons;
    private View batchPackageActions;
    private Button analyzeButton;
    private Button extractButton;
    private Button exportPackageButton;
    private Button saveVttButton;
    private Button shareVttButton;
    private Button savePackageButton;
    private Button sharePackageButton;
    private Button downloadVideoButton;
    private Button downloadAudioButton;
    private Button saveMediaButton;
    private Button shareMediaButton;
    private Button processBatchButton;
    private Button shareBatchPackageButton;
    private ProgressBar progress;
    private TextView statusText;
    private TextView videoTitle;
    private TextView metaText;
    private TextView descriptionText;
    private TextView tracksLabel;
    private TextView vttResultInfo;
    private TextView packageResultInfo;
    private TextView mediaResultInfo;
    private TextView batchResultInfo;
    private RadioGroup tracksGroup;
    private ImageView thumbnailView;
    private Spinner videoQualitySpinner;
    private Spinner audioFormatSpinner;
    private Spinner batchTypeSpinner;
    private Spinner batchFormatSpinner;

    private boolean engineReady = false;
    private boolean busy = false;
    private int currentMode = MODE_ANALYZE;

    private String currentUrl;
    private JSONObject currentInfo;
    private CaptionTrack selectedTrack;
    private File currentVtt;
    private File currentPackageZip;
    private File currentMediaFile;
    private String currentMediaMime = "application/octet-stream";
    private boolean currentMediaIsVideo = true;

    private String pendingBatchText;
    private int pendingBatchType;
    private String pendingBatchFormat;

    private final ActivityResultLauncher<String> vttSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/vtt"), uri -> {
                if (uri != null && currentVtt != null) copyToUri(currentVtt, uri, "Transcrição salva.");
            });

    private final ActivityResultLauncher<String> zipSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                if (uri != null && currentPackageZip != null) copyToUri(currentPackageZip, uri, "Pacote ZIP salvo.");
            });

    private final ActivityResultLauncher<String> batchPackageSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                if (uri == null) {
                    setStatus("Seleção de local cancelada.");
                    return;
                }
                processBatchAnalysis(pendingBatchText, uri);
            });

    private final ActivityResultLauncher<String> videoSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("video/*"), uri -> {
                if (uri != null && currentMediaFile != null) copyToUri(currentMediaFile, uri, "Vídeo salvo.");
            });

    private final ActivityResultLauncher<String> audioSaveLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("audio/*"), uri -> {
                if (uri != null && currentMediaFile != null) copyToUri(currentMediaFile, uri, "Áudio salvo.");
            });

    private final ActivityResultLauncher<Uri> batchFolderLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) {
                    setStatus("Seleção de pasta cancelada.");
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                } catch (Exception ignored) { }
                if (pendingBatchType == 1) processBatchTranscripts(uri);
                else processBatchMedia(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupSpinners();
        bindActions();
        showMode(MODE_ANALYZE);
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
        batchInput = findViewById(R.id.batchInput);
        modeAnalyze = findViewById(R.id.modeAnalyze);
        modeVideo = findViewById(R.id.modeVideo);
        modeAudio = findViewById(R.id.modeAudio);
        modeBatch = findViewById(R.id.modeBatch);
        urlCard = findViewById(R.id.urlCard);
        analyzePanel = findViewById(R.id.analyzePanel);
        videoPanel = findViewById(R.id.videoPanel);
        audioPanel = findViewById(R.id.audioPanel);
        batchPanel = findViewById(R.id.batchPanel);
        videoInfoCard = findViewById(R.id.videoInfoCard);
        mediaResultCard = findViewById(R.id.mediaResultCard);
        vttResultButtons = findViewById(R.id.vttResultButtons);
        packageResultButtons = findViewById(R.id.packageResultButtons);
        batchPackageActions = findViewById(R.id.batchPackageActions);
        analyzeButton = findViewById(R.id.analyzeButton);
        extractButton = findViewById(R.id.extractButton);
        exportPackageButton = findViewById(R.id.exportPackageButton);
        saveVttButton = findViewById(R.id.saveVttButton);
        shareVttButton = findViewById(R.id.shareVttButton);
        savePackageButton = findViewById(R.id.savePackageButton);
        sharePackageButton = findViewById(R.id.sharePackageButton);
        downloadVideoButton = findViewById(R.id.downloadVideoButton);
        downloadAudioButton = findViewById(R.id.downloadAudioButton);
        saveMediaButton = findViewById(R.id.saveMediaButton);
        shareMediaButton = findViewById(R.id.shareMediaButton);
        processBatchButton = findViewById(R.id.processBatchButton);
        shareBatchPackageButton = findViewById(R.id.shareBatchPackageButton);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.statusText);
        videoTitle = findViewById(R.id.videoTitle);
        metaText = findViewById(R.id.metaText);
        descriptionText = findViewById(R.id.descriptionText);
        tracksLabel = findViewById(R.id.tracksLabel);
        vttResultInfo = findViewById(R.id.vttResultInfo);
        packageResultInfo = findViewById(R.id.packageResultInfo);
        mediaResultInfo = findViewById(R.id.mediaResultInfo);
        batchResultInfo = findViewById(R.id.batchResultInfo);
        tracksGroup = findViewById(R.id.tracksGroup);
        thumbnailView = findViewById(R.id.thumbnailView);
        videoQualitySpinner = findViewById(R.id.videoQualitySpinner);
        audioFormatSpinner = findViewById(R.id.audioFormatSpinner);
        batchTypeSpinner = findViewById(R.id.batchTypeSpinner);
        batchFormatSpinner = findViewById(R.id.batchFormatSpinner);
    }

    private void setupSpinners() {
        setSpinnerItems(videoQualitySpinner, videoQualityLabels());
        setSpinnerItems(audioFormatSpinner, audioFormatLabels());
        setSpinnerItems(batchTypeSpinner, new String[]{
                "Pacote completo para análise (capa + dados + VTT)",
                "Somente transcrições (.VTT)",
                "Baixar vídeos",
                "Baixar áudios"
        });
        updateBatchFormatSpinner(0);

        batchTypeSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                updateBatchFormatSpinner(position)
        ));
    }

    private void bindActions() {
        modeAnalyze.setOnClickListener(v -> showMode(MODE_ANALYZE));
        modeVideo.setOnClickListener(v -> showMode(MODE_VIDEO));
        modeAudio.setOnClickListener(v -> showMode(MODE_AUDIO));
        modeBatch.setOnClickListener(v -> showMode(MODE_BATCH));

        analyzeButton.setOnClickListener(v -> analyze());
        extractButton.setOnClickListener(v -> extractSelectedVtt());
        exportPackageButton.setOnClickListener(v -> exportAnalysisPackage());

        saveVttButton.setOnClickListener(v -> {
            if (currentVtt != null) vttSaveLauncher.launch(currentVtt.getName());
        });
        shareVttButton.setOnClickListener(v -> {
            if (currentVtt != null) shareFile(currentVtt, "text/vtt", "Compartilhar transcrição");
        });

        savePackageButton.setOnClickListener(v -> {
            if (currentPackageZip != null) zipSaveLauncher.launch(currentPackageZip.getName());
        });
        sharePackageButton.setOnClickListener(v -> {
            if (currentPackageZip != null) shareFile(currentPackageZip, "application/zip", "Compartilhar pacote para análise");
        });

        downloadVideoButton.setOnClickListener(v -> startSingleMediaDownload(true));
        downloadAudioButton.setOnClickListener(v -> startSingleMediaDownload(false));

        saveMediaButton.setOnClickListener(v -> {
            if (currentMediaFile == null) return;
            if (currentMediaIsVideo) videoSaveLauncher.launch(currentMediaFile.getName());
            else audioSaveLauncher.launch(currentMediaFile.getName());
        });
        shareMediaButton.setOnClickListener(v -> {
            if (currentMediaFile != null) shareFile(currentMediaFile, currentMediaMime, "Compartilhar arquivo");
        });

        processBatchButton.setOnClickListener(v -> startBatch());
        shareBatchPackageButton.setOnClickListener(v -> {
            if (currentPackageZip != null) {
                shareFile(currentPackageZip, "application/zip", "Compartilhar pacote de análise");
            }
        });
    }

    private void initializeEngine() {
        setBusy(true, "Preparando yt-dlp e FFmpeg...");
        executor.submit(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());

                String motor = "motor local";
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._NIGHTLY);
                    motor = YoutubeDL.getInstance().versionName(getApplicationContext());
                } catch (Exception ignored) {
                    try {
                        motor = YoutubeDL.getInstance().versionName(getApplicationContext());
                    } catch (Exception ignoredToo) { }
                }

                engineReady = true;
                final String motorFinal = motor;
                runOnUiThread(() -> {
                    setBusy(false, "Pronto • yt-dlp " + motorFinal);
                    maybeAutoAnalyze();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Falha ao iniciar o mecanismo: " + safeMessage(e)));
            }
        });
    }

    private void showMode(int mode) {
        currentMode = mode;
        analyzePanel.setVisibility(mode == MODE_ANALYZE ? View.VISIBLE : View.GONE);
        videoPanel.setVisibility(mode == MODE_VIDEO ? View.VISIBLE : View.GONE);
        audioPanel.setVisibility(mode == MODE_AUDIO ? View.VISIBLE : View.GONE);
        batchPanel.setVisibility(mode == MODE_BATCH ? View.VISIBLE : View.GONE);
        urlCard.setVisibility(mode == MODE_BATCH ? View.GONE : View.VISIBLE);
        mediaResultCard.setVisibility((mode == MODE_VIDEO || mode == MODE_AUDIO) && currentMediaFile != null
                ? View.VISIBLE : View.GONE);
    }

    private void handleIncomingShare(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            String url = extractFirstUrl(text);
            if (url != null) {
                urlInput.setText(url);
                showMode(MODE_ANALYZE);
                modeAnalyze.setChecked(true);
                maybeAutoAnalyze();
            }
        }
    }

    private void maybeAutoAnalyze() {
        if (engineReady && currentMode == MODE_ANALYZE && !TextUtils.isEmpty(urlInput.getText())) analyze();
    }

    private String requireSingleUrl() {
        String text = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
        String url = extractFirstUrl(text);
        if (url == null) {
            urlInput.setError("Cole um link válido.");
            return null;
        }
        return url;
    }

    private void analyze() {
        if (!engineReady || busy) return;
        String url = requireSingleUrl();
        if (url == null) return;

        currentUrl = url;
        currentInfo = null;
        currentVtt = null;
        currentPackageZip = null;
        selectedTrack = null;
        tracks.clear();
        tracksGroup.removeAllViews();
        hideAnalysisResults();
        setBusy(true, "Buscando capa, título, descrição e transcrições...");

        executor.submit(() -> {
            try {
                JSONObject info = getVideoJson(url);
                List<CaptionTrack> parsed = parseTracks(info);
                currentInfo = info;
                runOnUiThread(() -> renderVideoDetails(info, parsed));
                loadThumbnailPreview(info.optString("thumbnail", ""), url);
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private JSONObject getVideoJson(String url) throws Exception {
        Exception last = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--skip-download");
                request.addOption("--no-playlist");
                request.addOption("--no-warnings");
                request.addOption("--quiet");
                request.addOption("--socket-timeout", "20");
                request.addOption("--extractor-retries", "3");
                request.addOption("--dump-single-json");

                String out = YoutubeDL.getInstance().execute(request).getOut();
                return jsonFromOutput(out, "Não foi possível interpretar as informações do vídeo.");
            } catch (Exception e) {
                last = e;
                if (!isTransientNetworkError(e) || attempt == 3) throw e;
                Thread.sleep(1200L * attempt);
            }
        }

        throw last == null ? new IllegalStateException("Falha ao consultar o vídeo.") : last;
    }

    private boolean isTransientNetworkError(Exception e) {
        String low = safeMessage(e).toLowerCase(Locale.ROOT);
        return low.contains("no address associated with hostname")
                || low.contains("temporary failure in name resolution")
                || low.contains("name or service not known")
                || low.contains("unable to download api page")
                || low.contains("connection reset")
                || low.contains("connection aborted")
                || low.contains("timed out")
                || low.contains("timeout");
    }

    private JSONObject jsonFromOutput(String out, String error) throws Exception {
        if (out == null) throw new IllegalStateException(error);
        int start = out.indexOf('{');
        int end = out.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalStateException(error);
        return new JSONObject(out.substring(start, end + 1));
    }

    private void renderVideoDetails(JSONObject info, List<CaptionTrack> parsed) {
        videoInfoCard.setVisibility(View.VISIBLE);
        videoTitle.setText(info.optString("title", "Vídeo do YouTube"));

        StringBuilder meta = new StringBuilder();
        appendMeta(meta, "Canal", firstNonEmpty(info.optString("channel", ""), info.optString("uploader", "")));
        appendMeta(meta, "Data", formatUploadDate(info.optString("upload_date", "")));
        appendMeta(meta, "Duração", formatDuration(info.optLong("duration", -1)));
        appendMeta(meta, "Visualizações", formatNumber(info.optLong("view_count", -1)));
        appendMeta(meta, "Curtidas", formatNumber(info.optLong("like_count", -1)));
        appendMeta(meta, "Comentários", formatNumber(info.optLong("comment_count", -1)));
        metaText.setText(meta.toString().trim());

        String description = info.optString("description", "");
        descriptionText.setText(description.isEmpty() ? "(Sem descrição disponível)" : description);

        renderTracks(parsed);
    }

    private void renderTracks(List<CaptionTrack> parsed) {
        tracks.clear();
        tracks.addAll(parsed);
        tracksGroup.removeAllViews();

        if (parsed.isEmpty()) {
            tracksLabel.setVisibility(View.GONE);
            selectedTrack = null;
            extractButton.setEnabled(false);
            exportPackageButton.setEnabled(true);
            setBusy(false, "Dados carregados. Este vídeo não possui transcrição disponível no YouTube.");
            return;
        }

        tracksLabel.setVisibility(View.VISIBLE);
        int defaultIndex = preferredTrackIndex(parsed);

        for (int i = 0; i < parsed.size(); i++) {
            CaptionTrack track = parsed.get(i);
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setTag(i);
            rb.setText(track.displayName());
            rb.setPadding(0, 7, 0, 7);
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
                vttResultButtons.setVisibility(View.GONE);
                vttResultInfo.setVisibility(View.GONE);
                packageResultButtons.setVisibility(View.GONE);
                packageResultInfo.setVisibility(View.GONE);
            }
        });

        setBusy(false, "Dados carregados. Escolha uma transcrição ou gere o pacote completo.");
    }

    private List<CaptionTrack> parseTracks(JSONObject info) {
        Map<String, CaptionTrack> unique = new LinkedHashMap<>();

        JSONObject manual = info.optJSONObject("subtitles");
        if (manual != null) {
            Iterator<String> keys = manual.keys();
            while (keys.hasNext()) {
                String code = keys.next();
                JSONArray formats = manual.optJSONArray(code);
                if (formats != null && formats.length() > 0) {
                    unique.put("manual:" + code, new CaptionTrack(code, "Legenda enviada pelo canal", false));
                }
            }
        }

        JSONObject automatic = info.optJSONObject("automatic_captions");
        if (automatic != null) addAutomaticOriginalTracks(automatic, unique, info.optString("language", ""));

        List<CaptionTrack> out = new ArrayList<>(unique.values());
        Collections.sort(out, Comparator
                .comparing((CaptionTrack t) -> !t.isPortuguese())
                .thenComparing(t -> t.automatic)
                .thenComparing(t -> t.code.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void addAutomaticOriginalTracks(JSONObject obj, Map<String, CaptionTrack> out, String videoLanguage) {
        List<String> codes = new ArrayList<>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) codes.add(keys.next());

        boolean hasOrig = false;
        for (String code : codes) {
            if (code.endsWith("-orig")) {
                hasOrig = true;
                break;
            }
        }

        if (hasOrig) {
            for (String code : codes) {
                if (!code.endsWith("-orig")) continue;
                JSONArray formats = obj.optJSONArray(code);
                if (formats != null && formats.length() > 0) {
                    out.put("auto:" + code, new CaptionTrack(code, "Transcrição automática original do YouTube", true));
                }
            }
            return;
        }

        boolean matchedLanguage = false;
        if (videoLanguage != null && !videoLanguage.trim().isEmpty()) {
            for (String code : codes) {
                if (!code.equalsIgnoreCase(videoLanguage)) continue;
                JSONArray formats = obj.optJSONArray(code);
                if (formats != null && formats.length() > 0) {
                    out.put("auto:" + code, new CaptionTrack(code, "Transcrição automática original do YouTube", true));
                    matchedLanguage = true;
                }
            }
        }
        if (matchedLanguage) return;

        for (String code : codes) {
            String low = code.toLowerCase(Locale.ROOT);
            if (!(low.equals("pt") || low.startsWith("pt-"))) continue;
            JSONArray formats = obj.optJSONArray(code);
            if (formats != null && formats.length() > 0) {
                out.put("auto:" + code, new CaptionTrack(code, "Transcrição automática do YouTube", true));
            }
        }
    }

    private int preferredTrackIndex(List<CaptionTrack> list) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).isPortuguese()) return i;
        return 0;
    }

    private CaptionTrack preferredTrack(List<CaptionTrack> list) {
        return list.isEmpty() ? null : list.get(preferredTrackIndex(list));
    }

    private void extractSelectedVtt() {
        if (busy || selectedTrack == null || currentUrl == null) return;
        setBusy(true, "Obtendo a transcrição " + selectedTrack.code + "...");

        executor.submit(() -> {
            try {
                File dir = new File(getCacheDir(), "single_vtt");
                recreateDirectory(dir);
                File vtt = downloadCaption(currentUrl, selectedTrack, dir);
                if (vtt == null) throw new IllegalStateException("O YouTube informou a faixa, mas não entregou o VTT.");
                currentVtt = vtt;
                runOnUiThread(() -> {
                    setBusy(false, "Transcrição pronta.");
                    vttResultInfo.setText(vtt.getName() + " • " + humanSize(vtt.length()));
                    vttResultInfo.setVisibility(View.VISIBLE);
                    vttResultButtons.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private File downloadCaption(String url, CaptionTrack track, File finalDir) throws Exception {
        File temp = new File(workRoot(), "caption_temp");
        recreateDirectory(temp);

        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--skip-download");
        request.addOption("--no-playlist");
        request.addOption("--write-subs");
        request.addOption("--write-auto-subs");
        request.addOption("--sub-langs", track.code);
        request.addOption("--sub-format", "vtt");
        request.addOption("--no-warnings");
        request.addOption("-P", temp.getAbsolutePath());
        request.addOption("-o", "%(id)s.%(ext)s");
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                YoutubeDL.getInstance().execute(request);
                last = null;
                break;
            } catch (Exception e) {
                last = e;
                if (!isTransientNetworkError(e) || attempt == 3) throw e;
                Thread.sleep(1200L * attempt);
            }
        }
        if (last != null) throw last;

        File source = findFirstByExtension(temp, ".vtt");
        if (source == null) return null;
        File target = new File(finalDir, "transcricao." + sanitizeCode(track.code) + ".vtt");
        copyFile(source, target);
        deleteRecursive(temp);
        return target;
    }

    private void exportAnalysisPackage() {
        if (busy || currentInfo == null || currentUrl == null) return;
        setBusy(true, "Montando pacote completo para análise...");

        executor.submit(() -> {
            try {
                File packageWork = new File(workRoot(), "single_analysis");
                recreateDirectory(packageWork);
                createAnalysisFolder(currentUrl, currentInfo, selectedTrack, packageWork);

                File packagesDir = new File(getCacheDir(), "packages");
                if (!packagesDir.exists() && !packagesDir.mkdirs()) {
                    throw new IllegalStateException("Não foi possível preparar a pasta de pacotes.");
                }

                String id = currentInfo.optString("id", "video");
                String name = sanitizeFilename(currentInfo.optString("title", "video"), 70)
                        + " [" + id + "] - pacote-analise.zip";
                File zip = new File(packagesDir, name);
                if (zip.exists()) zip.delete();
                zipDirectory(packageWork, zip);
                currentPackageZip = zip;
                deleteRecursive(packageWork);

                runOnUiThread(() -> {
                    setBusy(false, "Pacote completo pronto.");
                    packageResultInfo.setText("ZIP • " + humanSize(zip.length()) + " • pronto para compartilhar");
                    packageResultInfo.setVisibility(View.VISIBLE);
                    packageResultButtons.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private void createAnalysisFolder(String url, JSONObject info, CaptionTrack track, File folder) throws Exception {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta de análise.");
        }

        writeUtf8(new File(folder, "titulo.txt"), info.optString("title", ""));
        writeUtf8(new File(folder, "descricao.txt"), info.optString("description", ""));
        writeUtf8(new File(folder, "dados.json"), info.toString(2));
        writeUtf8(new File(folder, "manifesto.txt"), buildManifest(url, info, track));

        String thumb = info.optString("thumbnail", "");
        if (!thumb.isEmpty()) {
            try {
                downloadRawFile(thumb, new File(folder, "capa" + extensionFromUrl(thumb)));
            } catch (Exception ignored) { }
        }

        if (track != null) {
            File tempCaptionDir = new File(workRoot(), "analysis_caption_" + System.nanoTime());
            recreateDirectory(tempCaptionDir);
            File vtt = downloadCaption(url, track, tempCaptionDir);
            if (vtt != null) {
                copyFile(vtt, new File(folder, "transcricao." + sanitizeCode(track.code) + ".vtt"));
            } else {
                writeUtf8(new File(folder, "sem_transcricao.txt"), "A faixa foi detectada, mas o VTT não pôde ser obtido.");
            }
            deleteRecursive(tempCaptionDir);
        } else {
            writeUtf8(new File(folder, "sem_transcricao.txt"),
                    "Este vídeo não possui uma transcrição disponível no YouTube no momento da extração.");
        }
    }

    private String buildManifest(String url, JSONObject info, CaptionTrack track) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        return "PMCN Studios - Pacote para análise de vídeo\n"
                + "Extraído em: " + sdf.format(new Date()) + "\n"
                + "URL: " + url + "\n"
                + "ID do vídeo: " + info.optString("id", "") + "\n"
                + "Faixa selecionada: " + (track == null ? "nenhuma" : track.code) + "\n"
                + "Tipo: " + (track == null ? "sem transcrição disponível" : track.source) + "\n\n"
                + "Título, descrição, capa, metadados e transcrição são preservados para análise.\n"
                + "Nenhum áudio ou vídeo é baixado para gerar este pacote.\n";
    }

    private void startSingleMediaDownload(boolean video) {
        if (busy || !engineReady) return;
        String url = requireSingleUrl();
        if (url == null) return;

        String option = video
                ? videoQualityKey(videoQualitySpinner.getSelectedItemPosition())
                : audioFormatKey(audioFormatSpinner.getSelectedItemPosition());

        setBusy(true, video ? "Baixando vídeo..." : "Extraindo áudio...");
        currentMediaFile = null;
        mediaResultCard.setVisibility(View.GONE);

        executor.submit(() -> {
            try {
                File dir = new File(workRoot(), "media_single");
                recreateDirectory(dir);
                File file = downloadMedia(url, video, option, dir);
                if (file == null) throw new IllegalStateException("O download terminou sem gerar um arquivo utilizável.");

                currentMediaFile = file;
                currentMediaIsVideo = video;
                currentMediaMime = mimeForFile(file);

                runOnUiThread(() -> {
                    setBusy(false, video ? "Vídeo pronto." : "Áudio pronto.");
                    mediaResultInfo.setText(file.getName() + "\n" + humanSize(file.length()));
                    mediaResultCard.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private File downloadMedia(String url, boolean video, String option, File dir) throws Exception {
        try {
            return downloadMediaAttempt(url, video, option, dir, null, false);
        } catch (Exception first) {
            if (!isHttp403(first)) throw first;
        }

        runOnUiThread(() -> setStatus("HTTP 403 no fluxo padrão. Tentando rota HLS compatível..."));
        recreateDirectory(dir);
        try {
            return downloadMediaAttempt(url, video, option, dir,
                    "youtube:player_client=web_safari,default", true);
        } catch (Exception second) {
            if (!isHttp403(second)) throw second;
        }

        runOnUiThread(() -> setStatus("Tentando segunda rota compatível do YouTube..."));
        recreateDirectory(dir);
        return downloadMediaAttempt(url, video, option, dir,
                "youtube:player_client=web_embedded,default", false);
    }

    private File downloadMediaAttempt(String url, boolean video, String option, File dir,
                                      String extractorArgs, boolean preferMuxed) throws Exception {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        request.addOption("--no-mtime");
        request.addOption("--retries", "5");
        request.addOption("--fragment-retries", "5");
        request.addOption("-P", dir.getAbsolutePath());
        request.addOption("-o", "%(title).100B [%(id)s].%(ext)s");

        if (extractorArgs != null) {
            request.addOption("--extractor-args", extractorArgs);
        }

        if (video) {
            request.addOption("-f", preferMuxed ? fallbackVideoFormatSelector(option) : videoFormatSelector(option));
            request.addOption("--merge-output-format", "mp4");
        } else {
            request.addOption("-f", preferMuxed ? "best/bestaudio" : "bestaudio/best");
            request.addOption("--extract-audio");
            request.addOption("--audio-format", option);
            request.addOption("--audio-quality", "0");
        }

        YoutubeDL.getInstance().execute(request);
        File out = findMediaOutput(dir);
        if (out == null) throw new IllegalStateException("O download terminou sem gerar arquivo final.");
        return out;
    }

    private boolean isHttp403(Exception e) {
        return safeMessage(e).toLowerCase(Locale.ROOT).contains("403");
    }

    private String fallbackVideoFormatSelector(String key) {
        if ("1080".equals(key)) return "best[height<=1080]/best";
        if ("720".equals(key)) return "best[height<=720]/best";
        if ("480".equals(key)) return "best[height<=480]/best";
        return "best";
    }

    private String videoFormatSelector(String key) {
        if ("1080".equals(key)) {
            return "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*[height<=1080]+ba/b[height<=1080]";
        }
        if ("720".equals(key)) {
            return "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]";
        }
        if ("480".equals(key)) {
            return "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/bv*[height<=480]+ba/b[height<=480]";
        }
        return "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b";
    }

    private void startBatch() {
        if (busy || !engineReady) return;
        String text = batchInput.getText() == null ? "" : batchInput.getText().toString().trim();
        if (text.isEmpty()) {
            batchInput.setError("Cole uma playlist ou vários links.");
            return;
        }

        int type = batchTypeSpinner.getSelectedItemPosition();
        String format = batchFormatKey(type, batchFormatSpinner.getSelectedItemPosition());
        batchResultInfo.setVisibility(View.GONE);
        batchPackageActions.setVisibility(View.GONE);

        pendingBatchText = text;
        pendingBatchType = type;
        pendingBatchFormat = format;

        if (type == 0) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
            batchPackageSaveLauncher.launch("Lote-Analise-" + stamp + ".zip");
        } else {
            batchFolderLauncher.launch(null);
        }
    }

    private void processBatchAnalysis(String text, Uri destinationUri) {
        if (text == null || destinationUri == null) return;
        setBusy(true, "Lendo playlist/lote...");
        executor.submit(() -> {
            try {
                List<String> urls = expandBatchLinks(text);
                if (urls.isEmpty()) throw new IllegalStateException("Nenhum vídeo válido foi encontrado.");
                if (urls.size() > 100) {
                    throw new IllegalStateException("Pré-teste limitado a 100 vídeos por lote.");
                }

                File root = new File(workRoot(), "batch_analysis");
                recreateDirectory(root);
                JSONArray indexEntries = new JSONArray();
                int ok = 0;

                for (int i = 0; i < urls.size(); i++) {
                    String url = urls.get(i);
                    final int number = i + 1;
                    runOnUiThread(() -> setStatus("Analisando item " + number + " de " + urls.size() + "..."));

                    JSONObject row = new JSONObject();
                    row.put("ordem", number);
                    row.put("url", url);

                    try {
                        JSONObject info = getVideoJson(url);
                        List<CaptionTrack> available = parseTracks(info);
                        CaptionTrack track = preferredTrack(available);
                        String id = info.optString("id", "video_" + number);
                        String title = info.optString("title", "Vídeo " + number);
                        File folder = new File(root, String.format(Locale.US, "%03d - %s [%s]",
                                number,
                                sanitizeFilename(title, 70),
                                sanitizeFilename(id, 30)));
                        createAnalysisFolder(url, info, track, folder);

                        row.put("id", id);
                        row.put("title", title);
                        row.put("channel", firstNonEmpty(info.optString("channel", ""), info.optString("uploader", "")));
                        row.put("upload_date", info.optString("upload_date", ""));
                        row.put("duration", info.optLong("duration", -1));
                        row.put("transcript_track", track == null ? JSONObject.NULL : track.code);
                        row.put("status", "ok");
                        ok++;
                    } catch (Exception itemError) {
                        row.put("status", "erro");
                        row.put("erro", safeMessage(itemError));
                    }
                    indexEntries.put(row);

                    if (i < urls.size() - 1) {
                        try { Thread.sleep(500L); } catch (InterruptedException ignored) { }
                    }
                }

                JSONObject index = new JSONObject();
                index.put("gerado_em", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));
                index.put("total", urls.size());
                index.put("concluidos", ok);
                index.put("videos", indexEntries);
                writeUtf8(new File(root, "indice.json"), index.toString(2));

                File packagesDir = new File(getCacheDir(), "packages");
                if (!packagesDir.exists() && !packagesDir.mkdirs()) {
                    throw new IllegalStateException("Não foi possível preparar a pasta de pacotes.");
                }

                String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
                File zip = new File(packagesDir, "Lote-Analise-" + stamp + ".zip");
                if (zip.exists()) zip.delete();
                zipDirectory(root, zip);
                currentPackageZip = zip;
                copyToUriBlocking(zip, destinationUri);
                deleteRecursive(root);

                int finalOk = ok;
                runOnUiThread(() -> {
                    setBusy(false, "Lote de análise concluído e salvo.");
                    batchResultInfo.setText(finalOk + " de " + urls.size()
                            + " vídeos preparados.\nZIP salvo • " + humanSize(zip.length()));
                    batchResultInfo.setVisibility(View.VISIBLE);
                    batchPackageActions.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Pacote ZIP salvo.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private void processBatchTranscripts(Uri treeUri) {
        if (pendingBatchText == null) return;
        setBusy(true, "Preparando transcrições do lote...");

        executor.submit(() -> {
            try {
                List<String> urls = expandBatchLinks(pendingBatchText);
                if (urls.isEmpty()) throw new IllegalStateException("Nenhum vídeo válido foi encontrado.");
                if (urls.size() > 100) {
                    throw new IllegalStateException("Pré-teste limitado a 100 vídeos por lote.");
                }

                DocumentFile destination = createTranscriptBatchFolder(treeUri, pendingBatchText);
                JSONArray indexEntries = new JSONArray();
                int ok = 0;
                int withoutTranscript = 0;
                List<String> errors = new ArrayList<>();

                for (int i = 0; i < urls.size(); i++) {
                    String url = urls.get(i);
                    final int number = i + 1;
                    runOnUiThread(() -> setStatus("Transcrição " + number + " de " + urls.size() + "..."));

                    JSONObject row = new JSONObject();
                    row.put("ordem", number);
                    row.put("url", url);

                    File itemDir = new File(workRoot(), "batch_caption_item");
                    recreateDirectory(itemDir);

                    try {
                        File vtt = downloadPreferredPortugueseCaptionDirect(url, itemDir);
                        if (vtt == null) {
                            row.put("status", "sem_transcricao");
                            withoutTranscript++;
                        } else {
                            String fileName = String.format(Locale.US, "%03d - %s",
                                    number, safeDocumentName(vtt.getName()));
                            publishToFolder(vtt, destination, fileName);
                            row.put("status", "ok");
                            row.put("arquivo", fileName);
                            ok++;
                        }
                    } catch (Exception itemError) {
                        row.put("status", "erro");
                        row.put("erro", safeMessage(itemError));
                        errors.add(number + ": " + safeMessage(itemError));
                    } finally {
                        deleteRecursive(itemDir);
                    }

                    indexEntries.put(row);

                    if (i < urls.size() - 1) {
                        try { Thread.sleep(500L); } catch (InterruptedException ignored) { }
                    }
                }

                JSONObject index = new JSONObject();
                index.put("gerado_em", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));
                index.put("total", urls.size());
                index.put("transcricoes_salvas", ok);
                index.put("sem_transcricao", withoutTranscript);
                index.put("videos", indexEntries);

                File indexFile = new File(workRoot(), "indice-transcricoes.json");
                writeUtf8(indexFile, index.toString(2));
                publishToFolder(indexFile, destination, "indice-transcricoes.json");
                indexFile.delete();

                int finalOk = ok;
                int finalWithoutTranscript = withoutTranscript;
                runOnUiThread(() -> {
                    setBusy(false, "Lote de transcrições concluído.");
                    StringBuilder summary = new StringBuilder();
                    summary.append(finalOk).append(" de ").append(urls.size()).append(" VTTs salvos.");
                    if (finalWithoutTranscript > 0) {
                        summary.append("\nSem transcrição disponível: ").append(finalWithoutTranscript);
                    }
                    if (!errors.isEmpty()) {
                        summary.append("\nFalhas: ").append(errors.size());
                        for (int i = 0; i < Math.min(3, errors.size()); i++) {
                            summary.append("\n• ").append(errors.get(i));
                        }
                    }
                    batchResultInfo.setText(summary.toString());
                    batchResultInfo.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private File downloadPreferredPortugueseCaptionDirect(String url, File dir) throws Exception {
        File vtt = downloadCaptionPatternDirect(url, dir, "pt.*-orig");
        if (vtt != null) return vtt;

        recreateDirectory(dir);
        return downloadCaptionPatternDirect(url, dir, "pt,pt-BR,pt-PT");
    }

    private File downloadCaptionPatternDirect(String url, File dir, String langs) throws Exception {
        Exception last = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--skip-download");
                request.addOption("--no-playlist");
                request.addOption("--write-subs");
                request.addOption("--write-auto-subs");
                request.addOption("--sub-langs", langs);
                request.addOption("--sub-format", "vtt");
                request.addOption("--no-warnings");
                request.addOption("--socket-timeout", "20");
                request.addOption("--extractor-retries", "3");
                request.addOption("-P", dir.getAbsolutePath());
                request.addOption("-o", "%(title).100B [%(id)s].%(ext)s");

                YoutubeDL.getInstance().execute(request);
                return findFirstByExtension(dir, ".vtt");
            } catch (Exception e) {
                last = e;
                if (!isTransientNetworkError(e) || attempt == 3) throw e;
                Thread.sleep(1200L * attempt);
            }
        }

        if (last != null) throw last;
        return null;
    }

    private DocumentFile createTranscriptBatchFolder(Uri treeUri, String inputText) throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
        if (root == null || !root.canWrite()) {
            throw new IllegalStateException("A pasta escolhida não permite gravação.");
        }

        String folderName;
        List<String> inputUrls = extractUrls(inputText);
        if (inputUrls.size() == 1 && isLikelyPlaylist(inputUrls.get(0))) {
            String playlistTitle = playlistTitle(inputUrls.get(0));
            folderName = "Playlist - " + sanitizeFilename(
                    playlistTitle.isEmpty() ? "Transcrições" : playlistTitle, 80);
        } else {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
            folderName = "Lote de Transcrições - " + stamp;
        }

        DocumentFile existing = root.findFile(folderName);
        if (existing != null && existing.isDirectory()) return existing;

        DocumentFile created = root.createDirectory(folderName);
        if (created == null) throw new IllegalStateException("Não foi possível criar a pasta " + folderName);
        return created;
    }

    private String playlistTitle(String url) {
        try {
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--flat-playlist");
            request.addOption("--playlist-end", "1");
            request.addOption("--dump-single-json");
            request.addOption("--no-warnings");
            request.addOption("--quiet");
            JSONObject json = jsonFromOutput(
                    YoutubeDL.getInstance().execute(request).getOut(),
                    "Não foi possível ler a playlist."
            );
            return json.optString("title", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void publishToFolder(File source, DocumentFile folder, String desiredName) throws Exception {
        if (folder == null || !folder.canWrite()) {
            throw new IllegalStateException("A pasta escolhida não permite gravação.");
        }

        String name = safeDocumentName(desiredName);
        DocumentFile old = folder.findFile(name);
        if (old != null) old.delete();

        DocumentFile target = folder.createFile(mimeForFile(source), name);
        if (target == null) throw new IllegalStateException("Não foi possível criar " + name);

        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = getContentResolver().openOutputStream(target.getUri())) {
            if (out == null) throw new IllegalStateException("Não foi possível abrir o arquivo de destino.");
            copyStream(in, out);
        }
    }

    private void processBatchMedia(Uri treeUri) {
        if (pendingBatchText == null) return;
        setBusy(true, "Preparando playlist/lote...");

        executor.submit(() -> {
            try {
                List<String> urls = expandBatchLinks(pendingBatchText);
                if (urls.isEmpty()) throw new IllegalStateException("Nenhum vídeo válido foi encontrado.");
                if (urls.size() > 100) {
                    throw new IllegalStateException("Pré-teste limitado a 100 vídeos por lote.");
                }

                int ok = 0;
                List<String> errors = new ArrayList<>();
                boolean video = pendingBatchType == 2;

                for (int i = 0; i < urls.size(); i++) {
                    String url = urls.get(i);
                    final int number = i + 1;
                    runOnUiThread(() -> setStatus((video ? "Baixando vídeo " : "Extraindo áudio ")
                            + number + " de " + urls.size() + "..."));

                    File itemDir = new File(workRoot(), "batch_item");
                    recreateDirectory(itemDir);

                    try {
                        File file = downloadMedia(url, video, pendingBatchFormat, itemDir);
                        if (file == null) throw new IllegalStateException("Arquivo final não encontrado.");
                        DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
                        String numberedName = String.format(Locale.US, "%03d - %s",
                                number, safeDocumentName(file.getName()));
                        publishToFolder(file, root, numberedName);
                        ok++;
                    } catch (Exception itemError) {
                        errors.add(number + ": " + safeMessage(itemError));
                    } finally {
                        deleteRecursive(itemDir);
                    }

                    if (i < urls.size() - 1) {
                        try { Thread.sleep(500L); } catch (InterruptedException ignored) { }
                    }
                }

                int finalOk = ok;
                runOnUiThread(() -> {
                    setBusy(false, "Lote concluído.");
                    StringBuilder summary = new StringBuilder();
                    summary.append(finalOk).append(" de ").append(urls.size()).append(" arquivos salvos.");
                    if (!errors.isEmpty()) {
                        summary.append("\nFalhas: ").append(errors.size());
                        for (int i = 0; i < Math.min(3, errors.size()); i++) {
                            summary.append("\n• ").append(errors.get(i));
                        }
                    }
                    batchResultInfo.setText(summary.toString());
                    batchResultInfo.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(friendlyError(e)));
            }
        });
    }

    private List<String> expandBatchLinks(String text) throws Exception {
        List<String> input = extractUrls(text);
        Set<String> out = new LinkedHashSet<>();

        for (String url : input) {
            if (isLikelyPlaylist(url)) {
                List<String> expanded = expandPlaylist(url);
                if (expanded.isEmpty()) out.add(url);
                else out.addAll(expanded);
            } else {
                out.add(url);
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> expandPlaylist(String url) {
        List<String> out = new ArrayList<>();
        try {
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--flat-playlist");
            request.addOption("--dump-single-json");
            request.addOption("--no-warnings");
            request.addOption("--quiet");
            String result = YoutubeDL.getInstance().execute(request).getOut();
            JSONObject json = jsonFromOutput(result, "Playlist inválida.");
            JSONArray entries = json.optJSONArray("entries");
            if (entries == null) return out;

            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                String page = e.optString("webpage_url", "");
                if (page.startsWith("http")) {
                    out.add(page);
                    continue;
                }
                String raw = e.optString("url", "");
                if (raw.startsWith("http")) {
                    out.add(raw);
                    continue;
                }
                String id = e.optString("id", raw);
                if (!id.isEmpty()) out.add("https://www.youtube.com/watch?v=" + id);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private boolean isLikelyPlaylist(String url) {
        String low = url.toLowerCase(Locale.ROOT);
        return low.contains("list=") || low.contains("/playlist");
    }

    private List<String> extractUrls(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            String url = trimTrailingPunctuation(m.group());
            if (!url.isEmpty()) out.add(url);
        }
        return out;
    }

    private String extractFirstUrl(String text) {
        List<String> urls = extractUrls(text);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private String trimTrailingPunctuation(String url) {
        while (url.endsWith(".") || url.endsWith(",") || url.endsWith(";") || url.endsWith(")")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private void publishToTree(File source, Uri treeUri) throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
        publishToFolder(source, root, source.getName());
    }

    private void loadThumbnailPreview(String thumbnailUrl, String expectedUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) return;
        executor.submit(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(thumbnailUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (expectedUrl.equals(currentUrl)) {
                                thumbnailView.setImageBitmap(bitmap);
                                thumbnailView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) { }
        });
    }

    private void downloadRawFile(String url, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            copyStream(in, out);
        } finally {
            connection.disconnect();
        }
    }

    private String extensionFromUrl(String url) {
        String clean = url;
        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".webp")) return ".webp";
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".jpeg")) return ".jpeg";
        return ".jpg";
    }

    private File workRoot() {
        File external = getExternalCacheDir();
        return external != null ? external : getCacheDir();
    }

    private File findMediaOutput(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File best = null;
        for (File file : files) {
            if (!file.isFile()) continue;
            String n = file.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".part") || n.endsWith(".ytdl") || n.endsWith(".json")
                    || n.endsWith(".description") || n.endsWith(".vtt") || n.endsWith(".srt")
                    || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) {
                continue;
            }
            if (best == null || file.length() > best.length()) best = file;
        }
        return best;
    }

    private File findFirstByExtension(File dir, String extension) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(extension)) return file;
        }
        return null;
    }

    private void hideAnalysisResults() {
        videoInfoCard.setVisibility(View.GONE);
        thumbnailView.setVisibility(View.GONE);
        tracksLabel.setVisibility(View.GONE);
        vttResultButtons.setVisibility(View.GONE);
        vttResultInfo.setVisibility(View.GONE);
        packageResultButtons.setVisibility(View.GONE);
        packageResultInfo.setVisibility(View.GONE);
        extractButton.setEnabled(false);
        exportPackageButton.setEnabled(false);
    }

    private void shareFile(File file, String mimeType, String chooserTitle) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mimeType);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newUri(getContentResolver(), file.getName(), uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, chooserTitle));
        } catch (Exception e) {
            showError("Não foi possível compartilhar o arquivo: " + safeMessage(e));
        }
    }

    private void copyToUriBlocking(File source, Uri uri) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Não foi possível abrir o destino.");
            copyStream(in, out);
        }
    }

    private void copyToUri(File source, Uri uri, String successMessage) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Não foi possível abrir o destino.");
            copyStream(in, out);
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showError("Falha ao salvar: " + safeMessage(e));
        }
    }

    private void copyFile(File source, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            copyStream(in, out);
        }
    }

    private void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[32768];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    private void writeUtf8(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            addToZip(sourceDir, sourceDir, zos);
        }
    }

    private void addToZip(File root, File current, ZipOutputStream zos) throws Exception {
        File[] files = current.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[32768];

        for (File file : files) {
            if (file.isDirectory()) {
                addToZip(root, file, zos);
                continue;
            }
            String relative = root.toURI().relativize(file.toURI()).getPath();
            zos.putNextEntry(new ZipEntry(relative));
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                int read;
                while ((read = in.read(buffer)) != -1) zos.write(buffer, 0, read);
            }
            zos.closeEntry();
        }
    }

    private void recreateDirectory(File dir) {
        deleteRecursive(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Não foi possível preparar o armazenamento temporário.");
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }

    private String mimeForFile(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp4") || n.endsWith(".m4v")) return "video/mp4";
        if (n.endsWith(".webm")) return currentMediaIsVideo ? "video/webm" : "audio/webm";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".opus")) return "audio/ogg";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".vtt")) return "text/vtt";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        setStatus(message);
        refreshEnabledState();
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private void refreshEnabledState() {
        boolean available = engineReady && !busy;
        analyzeButton.setEnabled(available);
        downloadVideoButton.setEnabled(available);
        downloadAudioButton.setEnabled(available);
        processBatchButton.setEnabled(available);
        extractButton.setEnabled(available && selectedTrack != null);
        exportPackageButton.setEnabled(available && currentInfo != null);
    }

    private void showError(String message) {
        setBusy(false, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String friendlyError(Exception e) {
        String m = safeMessage(e);
        String low = m.toLowerCase(Locale.ROOT);

        if (low.contains("unable to resolve host") || low.contains("network")
                || low.contains("urlopen") || low.contains("no address associated")) {
            return "Sem acesso à internet. Verifique a conexão e tente novamente.";
        }
        if (low.contains("private video")) return "Este vídeo é privado.";
        if (low.contains("sign in") || low.contains("login")) {
            return "Este conteúdo exige login no YouTube.";
        }
        if (low.contains("unsupported url")) return "O link não foi reconhecido.";
        if (low.contains("requested format is not available")) {
            return "A qualidade/formato escolhido não está disponível para este vídeo.";
        }
        if (low.contains("403")) {
            return "O YouTube recusou o fluxo de mídia (HTTP 403). O app tentou rotas alternativas, mas este vídeo ainda exige uma autorização de reprodução que o YouTube não forneceu.";
        }
        return "Erro: " + m;
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) m = e.toString();
        return m.length() > 320 ? m.substring(0, 320) : m;
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
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
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

    private String safeDocumentName(String name) {
        String clean = sanitizeFilename(name, 150);
        return clean.isEmpty() ? "arquivo" : clean;
    }

    private void setSpinnerItems(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private String[] videoQualityLabels() {
        return new String[]{"Melhor disponível", "Até 1080p", "Até 720p", "Até 480p"};
    }

    private String[] audioFormatLabels() {
        return new String[]{"MP3 • qualidade máxima", "M4A • qualidade máxima", "Opus • qualidade máxima"};
    }

    private String videoQualityKey(int pos) {
        if (pos == 1) return "1080";
        if (pos == 2) return "720";
        if (pos == 3) return "480";
        return "best";
    }

    private String audioFormatKey(int pos) {
        if (pos == 1) return "m4a";
        if (pos == 2) return "opus";
        return "mp3";
    }

    private void updateBatchFormatSpinner(int batchType) {
        if (batchType == 0) {
            setSpinnerItems(batchFormatSpinner, new String[]{"ZIP único • cada vídeo em uma pasta completa"});
        } else if (batchType == 1) {
            setSpinnerItems(batchFormatSpinner, new String[]{"Português preferido • VTT original"});
        } else if (batchType == 2) {
            setSpinnerItems(batchFormatSpinner, videoQualityLabels());
        } else {
            setSpinnerItems(batchFormatSpinner, audioFormatLabels());
        }
    }

    private String batchFormatKey(int batchType, int pos) {
        if (batchType == 2) return videoQualityKey(pos);
        if (batchType == 3) return audioFormatKey(pos);
        if (batchType == 1) return "vtt";
        return "analysis";
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

    private interface PositionConsumer {
        void accept(int position);
    }

    private static class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionConsumer consumer;

        SimpleItemSelectedListener(PositionConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            consumer.accept(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
