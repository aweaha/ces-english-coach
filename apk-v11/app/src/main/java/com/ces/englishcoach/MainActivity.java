package com.ces.englishcoach;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 7001;
    private static final int REQ_FILE = 7002;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Voice femaleVoice;
    private Voice maleVoice;
    private SpeechRequest pendingSpeech;
    private SpeechRequest activeSpeech;
    private boolean paused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        webView.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (hasAudioPermission()) {
                        List<String> allowed = new ArrayList<>();
                        for (String r : request.getResources()) {
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) allowed.add(r);
                        }
                        if (!allowed.isEmpty()) request.grant(allowed.toArray(new String[0]));
                        else request.deny();
                    } else {
                        request.deny();
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "파일 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        initTts();
        if (!hasAudioPermission()) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void initTts() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) {
                Toast.makeText(MainActivity.this, "Android 음성 엔진을 초기화하지 못했습니다.", Toast.LENGTH_LONG).show();
                return;
            }
            tts.setLanguage(Locale.UK);
            pickBritishVoices();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) { finishUtterance(utteranceId, true); }
                @Override public void onError(String utteranceId) { finishUtterance(utteranceId, false); }
                @Override public void onStop(String utteranceId, boolean interrupted) {
                    if (!paused) finishUtterance(utteranceId, false);
                }
            });
            if (pendingSpeech != null) {
                SpeechRequest p = pendingSpeech;
                pendingSpeech = null;
                speakNative(p);
            }
        });
    }

    private void pickBritishVoices() {
        Set<Voice> all = tts.getVoices();
        if (all == null || all.isEmpty()) return;
        List<Voice> gb = new ArrayList<>();
        List<Voice> en = new ArrayList<>();
        for (Voice v : all) {
            Locale l = v.getLocale();
            if (l == null || !"en".equalsIgnoreCase(l.getLanguage())) continue;
            en.add(v);
            if ("GB".equalsIgnoreCase(l.getCountry())) gb.add(v);
        }
        List<Voice> pool = gb.isEmpty() ? en : gb;
        if (pool.isEmpty()) return;
        pool.sort(Comparator.comparing(Voice::getName));
        femaleVoice = bestVoice(pool, true, null);
        maleVoice = bestVoice(pool, false, femaleVoice);
        if (maleVoice == null) maleVoice = femaleVoice;
    }

    private Voice bestVoice(List<Voice> pool, boolean female, Voice exclude) {
        String[] hints = female
                ? new String[]{"female","fem","sonia","libby","susan","serena","kate","amy","gb-a","gba","gb-c","gbc"}
                : new String[]{"male","ryan","george","daniel","arthur","oliver","james","gb-b","gbb","gb-d","gbd"};
        Voice best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Voice v : pool) {
            if (exclude != null && v.getName().equals(exclude.getName()) && pool.size() > 1) continue;
            String n = v.getName().toLowerCase(Locale.ROOT);
            int score = 0;
            for (String h : hints) if (n.contains(h)) score += 50;
            if (!v.isNetworkConnectionRequired()) score += 5;
            if (score > bestScore) { bestScore = score; best = v; }
        }
        if (best == null && !pool.isEmpty()) {
            for (Voice v : pool) {
                if (exclude == null || !v.getName().equals(exclude.getName()) || pool.size() == 1) return v;
            }
        }
        return best;
    }

    private void speakNative(SpeechRequest r) {
        if (!ttsReady) { pendingSpeech = r; return; }
        paused = false;
        activeSpeech = r;
        boolean female = r.voiceKey.toLowerCase(Locale.ROOT).contains("female");
        Voice chosen = female ? femaleVoice : maleVoice;
        if (chosen != null) tts.setVoice(chosen); else tts.setLanguage(Locale.UK);

        boolean sameVoice = femaleVoice == null || maleVoice == null || femaleVoice.getName().equals(maleVoice.getName());
        float pitch = clamp(r.pitch, 0.85f, 1.15f);
        if (sameVoice) pitch = female ? 1.04f : 0.96f;
        tts.setPitch(pitch);
        tts.setSpeechRate(clamp(r.rate, 0.78f, 1.18f));
        tts.speak(r.text, TextToSpeech.QUEUE_FLUSH, null, r.id);
    }

    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private void finishUtterance(String id, boolean ok) {
        if (id == null) return;
        if (activeSpeech != null && id.equals(activeSpeech.id)) activeSpeech = null;
        final String safe = id.replace("\\", "\\\\").replace("'", "\\'");
        runOnUiThread(() -> webView.evaluateJavascript("window.__nativeTtsDone && window.__nativeTtsDone('" + safe + "'," + ok + ");", null));
    }

    private class TtsBridge {
        @JavascriptInterface
        public void speak(String text, String voiceKey, String lang, double rate, double pitch, String id) {
            runOnUiThread(() -> speakNative(new SpeechRequest(text, voiceKey, (float) rate, (float) pitch, id)));
        }

        @JavascriptInterface
        public void cancel() {
            runOnUiThread(() -> {
                paused = false;
                SpeechRequest a = activeSpeech;
                activeSpeech = null;
                if (tts != null) tts.stop();
                if (a != null) finishUtterance(a.id, false);
            });
        }

        @JavascriptInterface
        public void pause() {
            runOnUiThread(() -> {
                if (tts != null && activeSpeech != null) { paused = true; tts.stop(); }
            });
        }

        @JavascriptInterface
        public void resume() {
            runOnUiThread(() -> {
                if (paused && activeSpeech != null) speakNative(activeSpeech);
            });
        }
    }

    private static class SpeechRequest {
        final String text, voiceKey, id;
        final float rate, pitch;
        SpeechRequest(String text, String voiceKey, float rate, float pitch, String id) {
            this.text = text == null ? "" : text;
            this.voiceKey = voiceKey == null ? "" : voiceKey;
            this.rate = rate;
            this.pitch = pitch;
            this.id = id == null ? "native" : id;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (webView != null) { webView.removeJavascriptInterface("AndroidTTS"); webView.destroy(); }
        super.onDestroy();
    }
}
