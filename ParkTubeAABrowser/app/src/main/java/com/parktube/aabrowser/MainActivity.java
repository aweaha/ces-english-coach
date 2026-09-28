package com.parktube.aabrowser;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
  static final String HOME = "https://m.youtube.com/";
  static final int BG = Color.rgb(4, 10, 16);
  static final int PANEL = Color.rgb(10, 20, 30);
  static final int PANEL_2 = Color.rgb(18, 31, 44);
  static final int TEXT = Color.rgb(245, 248, 252);
  static final int MUTED = Color.rgb(155, 171, 188);
  static final int BLUE = Color.rgb(0, 139, 255);
  static final int PINK = Color.rgb(255, 45, 139);

  FrameLayout root, normalRoot, webFrame, customFrame, drivePanel;
  LinearLayout chromeTop, bottomNav;
  WebView web;
  EditText search;
  ProgressBar progress;
  Button playPauseButton;
  TextView driveTitle, driveTime;
  SeekBar driveSeek;
  WebChromeClient.CustomViewCallback customViewCallback;
  View customView;
  SharedPreferences prefs;
  AdBlocker blocker;
  String adScript = "";
  boolean driveMode = false;
  boolean appFullscreen = false;
  int orientationMode = 0;
  long lastInject = 0L;
  boolean carReceiverRegistered = false;
  final BroadcastReceiver carReceiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      if (intent == null || web == null) return;
      String action = intent.getAction();
      if ("com.pwk.parktube.ACTION_PLAY".equals(action)) {
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.play();})()", null);
      } else if ("com.pwk.parktube.ACTION_PAUSE".equals(action)) {
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause();})()", null);
      } else if ("com.pwk.parktube.ACTION_TOGGLE".equals(action)) {
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})()", null);
      } else if ("com.pwk.parktube.ACTION_NEXT".equals(action)) {
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.min(v.duration||1e9,(v.currentTime||0)+10);})()", null);
      } else if ("com.pwk.parktube.ACTION_PREV".equals(action)) {
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,(v.currentTime||0)-10);})()", null);
      }
    }
  };
  final Handler handler = new Handler(Looper.getMainLooper());

  void broadcastCarState(String title, boolean paused, double position, double duration) {
    Intent i = new Intent("com.pwk.parktube.ACTION_SYNC");
    i.setPackage(getPackageName());
    i.putExtra("title", title == null || title.isEmpty() ? "ParkTube PWK" : title);
    i.putExtra("paused", paused);
    i.putExtra("position", Math.max(0L, (long)(position * 1000)));
    i.putExtra("duration", Math.max(0L, (long)(duration * 1000)));
    sendBroadcast(i);
  }

  final Runnable carStateTicker = new Runnable() {
    @Override public void run() {
      if (web != null) {
        String js = "(function(){var v=document.querySelector('video');return v?{t:v.currentTime||0,d:isFinite(v.duration)?v.duration:0,p:!!v.paused,title:(document.title||'').replace(/ - YouTube$/,'')}:{};})()";
        web.evaluateJavascript(js, value -> {
          try {
            if (value != null && value.startsWith("{")) {
              JSONObject o = new JSONObject(value);
              broadcastCarState(
                o.optString("title", "ParkTube PWK"),
                o.optBoolean("p", true),
                o.optDouble("t", 0),
                o.optDouble("d", 0)
              );
            }
          } catch (Exception ignored) {}
        });
      }
      handler.postDelayed(this, 1000);
    }
  };

  final Runnable driveTicker = new Runnable() {
    @Override public void run() {
      if (!driveMode || web == null) return;
      String js = "(function(){var v=document.querySelector('video');return v?{t:v.currentTime||0,d:isFinite(v.duration)?v.duration:0,p:!!v.paused,title:(document.title||'').replace(/ - YouTube$/,'')}:{};})()";
      web.evaluateJavascript(js, value -> {
        try {
          if (value != null && value.startsWith("{")) {
            JSONObject o = new JSONObject(value);
            double t = o.optDouble("t", 0);
            double d = o.optDouble("d", 0);
            boolean paused = o.optBoolean("p", true);
            String title = o.optString("title", "현재 재생 중");
            driveTitle.setText(title.isEmpty() ? "현재 재생 중" : title);
            playPauseButton.setText(paused ? "▶" : "Ⅱ");
            if (d > 0) {
              driveSeek.setMax(1000);
              driveSeek.setProgress((int)Math.min(1000, Math.max(0, (t / d) * 1000)));
              driveTime.setText(formatTime(t) + " / " + formatTime(d));
            }
          }
        } catch (Exception ignored) {}
      });
      handler.postDelayed(this, 1000);
    }
  };

  int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
  boolean portrait() { return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT; }

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

    prefs = getSharedPreferences("parktube", MODE_PRIVATE);
    orientationMode = prefs.getInt("orientation_mode", 0);
    applyOrientationMode();
    blocker = new AdBlocker(this);
    adScript = readAsset("adblock.js");
    keepScreen(prefs.getBoolean("keep_screen", true));

    buildUi();
    configureWebView();

    registerCarReceiverForAppLifetime();
    handler.removeCallbacks(carStateTicker);
    handler.post(carStateTicker);

    if (state == null) web.loadUrl(HOME); else web.restoreState(state);
  }

  String readAsset(String name) {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(name)))) {
      String line;
      while ((line = r.readLine()) != null) sb.append(line).append('\n');
    } catch (Exception ignored) {}
    return sb.toString();
  }

  GradientDrawable rounded(int color, int radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    return g;
  }

  GradientDrawable outlined(int color, int radius, int stroke, int strokeColor) {
    GradientDrawable g = rounded(color, radius);
    g.setStroke(dp(stroke), strokeColor);
    return g;
  }

  GradientDrawable gradient(int start, int end, int radius) {
    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
    g.setCornerRadius(dp(radius));
    return g;
  }

  TextView txt(String s, int sp, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(sp);
    t.setTextColor(color);
    t.setGravity(Gravity.CENTER_VERTICAL);
    return t;
  }

  Button button(String label) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(TEXT);
    b.setTextSize(16);
    b.setAllCaps(false);
    b.setPadding(0, 0, 0, 0);
    b.setMinWidth(0);
    b.setMinHeight(0);
    b.setBackground(rounded(PANEL_2, 12));
    return b;
  }

  Button chip(String label, boolean selected) {
    Button b = button(label);
    b.setTextSize(13);
    b.setTextColor(selected ? Color.BLACK : TEXT);
    b.setBackground(rounded(selected ? Color.WHITE : Color.rgb(24, 35, 47), 10));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
    lp.rightMargin = dp(7);
    b.setLayoutParams(lp);
    b.setPadding(dp(14), 0, dp(14), 0);
    return b;
  }

  void buildUi() {
    root = new FrameLayout(this);
    root.setBackgroundColor(BG);
    setContentView(root);

    normalRoot = new FrameLayout(this);
    root.addView(normalRoot, new FrameLayout.LayoutParams(-1, -1));

    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setBackgroundColor(BG);
    normalRoot.addView(page, new FrameLayout.LayoutParams(-1, -1));

    chromeTop = new LinearLayout(this);
    chromeTop.setOrientation(LinearLayout.VERTICAL);
    chromeTop.setBackgroundColor(BG);
    page.addView(chromeTop, new LinearLayout.LayoutParams(-1, -2));

    if (portrait()) {
      chromeTop.addView(buildPortraitHeader(), new LinearLayout.LayoutParams(-1, dp(54)));
      chromeTop.addView(buildSearchRow(false), new LinearLayout.LayoutParams(-1, dp(52)));
    } else {
      chromeTop.addView(buildLandscapeHeader(), new LinearLayout.LayoutParams(-1, dp(58)));
    }
    chromeTop.addView(buildChipRow(), new LinearLayout.LayoutParams(-1, dp(42)));

    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    progress.setVisibility(View.GONE);
    page.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

    webFrame = new FrameLayout(this);
    page.addView(webFrame, new LinearLayout.LayoutParams(-1, 0, 1));

    web = new WebView(this);
    web.setBackgroundColor(BG);
    webFrame.addView(web, new FrameLayout.LayoutParams(-1, -1));

    bottomNav = buildBottomNav();
    page.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(58)));

    customFrame = new FrameLayout(this);
    customFrame.setBackgroundColor(Color.BLACK);
    customFrame.setVisibility(View.GONE);
    root.addView(customFrame, new FrameLayout.LayoutParams(-1, -1));

    buildDrivePanel();
  }

  View buildPortraitHeader() {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), dp(5), dp(10), dp(5));

    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.pwk_launcher);
    logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
    row.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));

    TextView brand = txt("PWK", 20, TEXT);
    brand.setTypeface(null, 1);
    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -1);
    blp.leftMargin = dp(8);
    row.addView(brand, blp);

    Space space = new Space(this);
    row.addView(space, new LinearLayout.LayoutParams(0, 1, 1));

    row.addView(buildOrientationButton(), new LinearLayout.LayoutParams(dp(46), dp(46)));
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(46), dp(46));
    slp.leftMargin = dp(6);
    row.addView(buildSettingsButton(), slp);
    return row;
  }

  View buildLandscapeHeader() {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), dp(6), dp(10), dp(6));

    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.pwk_launcher);
    logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
    row.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));

    TextView brand = txt("PWK", 19, TEXT);
    brand.setTypeface(null, 1);
    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(58), -1);
    blp.leftMargin = dp(5);
    row.addView(brand, blp);

    EditText box = createSearchBox();
    search = box;
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(46), 1);
    sp.leftMargin = dp(8);
    sp.rightMargin = dp(8);
    row.addView(box, sp);

    ImageButton searchBtn = buildSearchButton();
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(46), dp(46));
    searchLp.rightMargin = dp(6);
    row.addView(searchBtn, searchLp);

    row.addView(buildOrientationButton(), new LinearLayout.LayoutParams(dp(46), dp(46)));
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(46), dp(46));
    slp.leftMargin = dp(6);
    row.addView(buildSettingsButton(), slp);
    return row;
  }

  View buildSearchRow(boolean compact) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), dp(2), dp(12), dp(4));
    search = createSearchBox();
    row.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
    ImageButton searchBtn = buildSearchButton();
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(46), dp(46));
    searchLp.leftMargin = dp(7);
    row.addView(searchBtn, searchLp);
    return row;
  }

  EditText createSearchBox() {
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setHint("YouTube 검색 또는 URL 입력");
    e.setTextSize(15);
    e.setPadding(dp(14), 0, dp(14), 0);
    e.setBackground(outlined(Color.rgb(28, 41, 54), 14, 1, Color.rgb(47, 64, 82)));
    e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    e.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
        navigate(e.getText().toString());
        return true;
      }
      return false;
    });
    return e;
  }

  ImageButton buildSearchButton() {
    ImageButton b = new ImageButton(this);
    b.setImageResource(android.R.drawable.ic_menu_search);
    b.setColorFilter(TEXT);
    b.setBackground(gradient(Color.rgb(0, 103, 188), Color.rgb(0, 154, 255), 12));
    b.setPadding(dp(10), dp(10), dp(10), dp(10));
    b.setContentDescription("검색");
    b.setOnClickListener(v -> {
      if (search != null) {
        navigate(search.getText().toString());
        try {
          android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
          imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } catch (Exception ignored) {}
      }
    });
    return b;
  }

  Button buildOrientationButton() {
    Button b = button("▯");
    b.setTextSize(24);
    b.setBackground(gradient(Color.rgb(0, 103, 188), Color.rgb(0, 154, 255), 12));
    b.setContentDescription("화면 방향");
    b.setOnClickListener(v -> showOrientationDialog());
    return b;
  }

  Button buildSettingsButton() {
    Button b = button("⚙");
    b.setTextSize(21);
    b.setContentDescription("설정");
    b.setOnClickListener(v -> showSettings());
    return b;
  }

  View buildChipRow() {
    HorizontalScrollView sc = new HorizontalScrollView(this);
    sc.setHorizontalScrollBarEnabled(false);
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), dp(3), dp(4), dp(5));
    sc.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));

    Button all = chip("전체", true);
    Button shorts = chip("Shorts", false);
    Button music = chip("음악", false);
    Button games = chip("게임", false);
    Button news = chip("뉴스", false);

    row.addView(all); row.addView(shorts); row.addView(music); row.addView(games); row.addView(news);

    all.setOnClickListener(v -> web.loadUrl(HOME));
    shorts.setOnClickListener(v -> web.loadUrl("https://m.youtube.com/shorts/"));
    music.setOnClickListener(v -> web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode("음악")));
    games.setOnClickListener(v -> web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode("게임")));
    news.setOnClickListener(v -> web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode("뉴스")));
    return sc;
  }

  LinearLayout buildBottomNav() {
    LinearLayout nav = new LinearLayout(this);
    nav.setGravity(Gravity.CENTER);
    nav.setPadding(dp(8), dp(5), dp(8), dp(6));
    nav.setBackgroundColor(Color.rgb(7, 15, 23));

    Button back = button("‹");
    Button forward = button("›");
    Button home = button("⌂");
    Button refresh = button("↻");

    Button[] buttons = {back, forward, home, refresh};
    for (Button b : buttons) {
      b.setTextSize(27);
      b.setBackgroundColor(Color.TRANSPARENT);
      nav.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
    }

    back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
    forward.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
    home.setOnClickListener(v -> web.loadUrl(HOME));
    refresh.setOnClickListener(v -> web.reload());
    return nav;
  }

  void buildDrivePanel() {
    drivePanel = new FrameLayout(this);
    drivePanel.setBackgroundColor(Color.rgb(5, 11, 18));
    drivePanel.setVisibility(View.GONE);
    root.addView(drivePanel, new FrameLayout.LayoutParams(-1, -1));

    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setGravity(Gravity.CENTER_HORIZONTAL);
    col.setPadding(dp(24), dp(34), dp(24), dp(24));
    drivePanel.addView(col, new FrameLayout.LayoutParams(-1, -1));

    TextView icon = txt("◉", 42, Color.rgb(184, 75, 255));
    icon.setGravity(Gravity.CENTER);
    col.addView(icon, new LinearLayout.LayoutParams(-1, dp(60)));

    TextView title = txt("주행 모드", 28, TEXT);
    title.setGravity(Gravity.CENTER);
    title.setTypeface(null, 1);
    col.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

    TextView sub = txt("현재는 오디오 전용 모드입니다", 15, MUTED);
    sub.setGravity(Gravity.CENTER);
    col.addView(sub, new LinearLayout.LayoutParams(-1, dp(36)));

    ImageView art = new ImageView(this);
    art.setImageResource(R.drawable.pwk_launcher);
    art.setScaleType(ImageView.ScaleType.CENTER_CROP);
    LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(portrait() ? 150 : 115), dp(portrait() ? 150 : 115));
    alp.topMargin = dp(14);
    col.addView(art, alp);

    driveTitle = txt("현재 재생 중", 18, TEXT);
    driveTitle.setGravity(Gravity.CENTER);
    driveTitle.setMaxLines(2);
    LinearLayout.LayoutParams dtp = new LinearLayout.LayoutParams(-1, dp(62));
    dtp.topMargin = dp(12);
    col.addView(driveTitle, dtp);

    driveSeek = new SeekBar(this);
    LinearLayout.LayoutParams skp = new LinearLayout.LayoutParams(-1, dp(40));
    col.addView(driveSeek, skp);

    driveTime = txt("0:00 / 0:00", 14, MUTED);
    driveTime.setGravity(Gravity.CENTER);
    col.addView(driveTime, new LinearLayout.LayoutParams(-1, dp(34)));

    LinearLayout controls = new LinearLayout(this);
    controls.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(86));
    clp.topMargin = dp(8);
    col.addView(controls, clp);

    Button minus = button("↶\n10");
    playPauseButton = button("Ⅱ");
    Button plus = button("10\n↷");
    minus.setTextSize(18);
    playPauseButton.setTextSize(28);
    plus.setTextSize(18);
    playPauseButton.setBackground(rounded(Color.rgb(33, 48, 64), 42));

    controls.addView(minus, new LinearLayout.LayoutParams(0, -1, 1));
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(86), -1);
    pp.leftMargin = dp(12); pp.rightMargin = dp(12);
    controls.addView(playPauseButton, pp);
    controls.addView(plus, new LinearLayout.LayoutParams(0, -1, 1));

    Button exit = button("▣  영상 화면으로 돌아가기");
    exit.setTextSize(16);
    exit.setBackground(outlined(Color.rgb(26, 40, 56), 18, 1, Color.rgb(92, 119, 150)));
    LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(58));
    ep.topMargin = dp(20);
    col.addView(exit, ep);

    minus.setOnClickListener(v -> web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,(v.currentTime||0)-10);})()", null));
    plus.setOnClickListener(v -> web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.min(v.duration||1e9,(v.currentTime||0)+10);})()", null));
    playPauseButton.setOnClickListener(v -> web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})()", null));
    exit.setOnClickListener(v -> setDriveMode(false));

    driveSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser) return;
        double ratio = progress / 1000.0;
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&isFinite(v.duration))v.currentTime=v.duration*" + ratio + ";})()", null);
      }
      @Override public void onStartTrackingTouch(SeekBar seekBar) {}
      @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    });
  }

  @SuppressLint({"SetJavaScriptEnabled", "RequiresFeature"})
  void configureWebView() {
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setLoadWithOverviewMode(true);
    s.setUseWideViewPort(true);
    s.setSupportZoom(false);
    s.setBuiltInZoomControls(false);
    s.setDisplayZoomControls(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);
    s.setTextZoom(100);
    if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_ON);
    s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

    web.setWebViewClient(new WebViewClient() {
      @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        progress.setVisibility(View.VISIBLE);
        updateUrlUi(url);
      }

      @Override public void onPageFinished(WebView view, String url) {
        progress.setVisibility(View.GONE);
        updateUrlUi(url);
        injectAdBlock(true);
      }

      @Override public void onLoadResource(WebView view, String url) {
        injectAdBlock(false);
      }

      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (blocker.shouldBlock(request.getUrl().toString())) return blocker.empty();
        return super.shouldInterceptRequest(view, request);
      }

      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri u = request.getUrl();
        String scheme = u.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
        return true;
      }
    });

    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onProgressChanged(WebView view, int newProgress) {
        progress.setProgress(newProgress);
        progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
      }

      @Override public void onShowCustomView(View view, CustomViewCallback callback) {
        if (driveMode) { callback.onCustomViewHidden(); return; }
        if (customView != null) { callback.onCustomViewHidden(); return; }
        customView = view;
        customViewCallback = callback;
        customFrame.removeAllViews();
        customFrame.addView(view, new FrameLayout.LayoutParams(-1, -1));
        customFrame.setVisibility(View.VISIBLE);
        normalRoot.setVisibility(View.GONE);
        enterImmersive();
      }

      @Override public void onHideCustomView() {
        hideCustomView();
      }
    });
  }

  void injectAdBlock(boolean force) {
    if (adScript.isEmpty() || web == null) return;
    long now = SystemClock.elapsedRealtime();
    if (!force && now - lastInject < 700) return;
    lastInject = now;
    web.evaluateJavascript(adScript, null);
  }

  void updateUrlUi(String url) {
    if (search != null && url != null) search.setText(url);
  }

  void navigate(String raw) {
    String q = raw == null ? "" : raw.trim();
    if (q.isEmpty()) return;
    if (q.startsWith("http://") || q.startsWith("https://")) web.loadUrl(q);
    else if (q.contains(".") && !q.contains(" ")) web.loadUrl("https://" + q);
    else web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(q));
  }

  void hideCustomView() {
    if (customView == null) return;
    customFrame.removeAllViews();
    customFrame.setVisibility(View.GONE);
    customView = null;
    if (customViewCallback != null) customViewCallback.onCustomViewHidden();
    customViewCallback = null;
    normalRoot.setVisibility(View.VISIBLE);
    exitImmersive();
  }

  void enterImmersive() {
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    getWindow().getDecorView().setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
      View.SYSTEM_UI_FLAG_FULLSCREEN |
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    );
  }

  void exitImmersive() {
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
  }

  void showOrientationDialog() {
    String[] items = {"자동", "세로", "가로"};
    new AlertDialog.Builder(this)
      .setTitle("화면 방향")
      .setSingleChoiceItems(items, orientationMode, (d, which) -> {
        orientationMode = which;
        prefs.edit().putInt("orientation_mode", which).apply();
        d.dismiss();
        applyOrientationMode();
      })
      .setNegativeButton("닫기", null)
      .show();
  }

  void applyOrientationMode() {
    int mode = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
    if (orientationMode == 1) mode = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    if (orientationMode == 2) mode = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    if (getRequestedOrientation() != mode) setRequestedOrientation(mode);
  }

  void showSettings() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(22), dp(10), dp(22), dp(10));

    Switch keep = new Switch(this);
    keep.setText("화면 꺼짐 방지");
    keep.setTextSize(17);
    keep.setChecked(prefs.getBoolean("keep_screen", true));
    box.addView(keep, new LinearLayout.LayoutParams(-1, dp(58)));

    Button drive = button("주행 모드 시작 (오디오 전용)");
    drive.setTextSize(16);
    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(54));
    dlp.topMargin = dp(8);
    box.addView(drive, dlp);
    drive.setOnClickListener(v -> {
      setDriveMode(true);
      if (v.getParent() != null) {
        // dialog closes through the positive path below if user taps outside later
      }
    });

    TextView note = txt("광고 제거는 자동으로 동작합니다. YouTube 페이지 구조가 바뀌면 일부 광고가 잠시 보일 수 있습니다.", 13, Color.DKGRAY);
    note.setPadding(0, dp(12), 0, 0);
    box.addView(note, new LinearLayout.LayoutParams(-1, dp(74)));

    AlertDialog dlg = new AlertDialog.Builder(this)
      .setTitle("⚙ ParkTube 설정")
      .setView(box)
      .setPositiveButton("저장", null)
      .setNegativeButton("닫기", null)
      .create();

    drive.setOnClickListener(v -> {
      setDriveMode(true);
      dlg.dismiss();
    });

    dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      prefs.edit().putBoolean("keep_screen", keep.isChecked()).apply();
      keepScreen(keep.isChecked());
      dlg.dismiss();
    }));
    dlg.show();
  }

  void setDriveMode(boolean on) {
    driveMode = on;
    if (on) {
      if (customView != null) hideCustomView();
      normalRoot.setVisibility(View.GONE);
      drivePanel.setVisibility(View.VISIBLE);
      handler.removeCallbacks(driveTicker);
      handler.post(driveTicker);
    } else {
      handler.removeCallbacks(driveTicker);
      drivePanel.setVisibility(View.GONE);
      normalRoot.setVisibility(View.VISIBLE);
      }
  }

  void keepScreen(boolean on) {
    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  String formatTime(double seconds) {
    int s = Math.max(0, (int)seconds);
    int h = s / 3600;
    int m = (s % 3600) / 60;
    int r = s % 60;
    return h > 0 ? String.format("%d:%02d:%02d", h, m, r) : String.format("%d:%02d", m, r);
  }

  @Override public void onBackPressed() {
    if (driveMode) { setDriveMode(false); return; }
    if (customView != null) { hideCustomView(); return; }
    if (web.canGoBack()) web.goBack(); else super.onBackPressed();
  }

  void registerCarReceiverForAppLifetime() {
    if (carReceiverRegistered) return;
    IntentFilter f = new IntentFilter();
    f.addAction("com.pwk.parktube.ACTION_PLAY");
    f.addAction("com.pwk.parktube.ACTION_PAUSE");
    f.addAction("com.pwk.parktube.ACTION_TOGGLE");
    f.addAction("com.pwk.parktube.ACTION_NEXT");
    f.addAction("com.pwk.parktube.ACTION_PREV");
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(carReceiver, f, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(carReceiver, f);
    carReceiverRegistered = true;
  }

  @Override protected void onSaveInstanceState(Bundle outState) {
    if (web != null) web.saveState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override protected void onDestroy() {
    if (carReceiverRegistered) {
      try { unregisterReceiver(carReceiver); } catch (Exception ignored) {}
      carReceiverRegistered = false;
    }
    handler.removeCallbacksAndMessages(null);
    if (web != null) {
      web.stopLoading();
      web.loadUrl("about:blank");
      web.removeAllViews();
      web.destroy();
    }
    super.onDestroy();
  }
}
