package com.parktube.aabrowser;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
  static final String HOME="https://m.youtube.com/";
  WebView web; EditText search; ProgressBar progress; FrameLayout root,full,drivePanel; View custom;
  WebChromeClient.CustomViewCallback customCb; AdBlocker blocker; SharedPreferences prefs;
  boolean adblock=true,driveMode=false;
  int orientationMode=0;
  Button orientationButton,fullscreenButton,driveButton,playPauseButton; LinearLayout topBar; boolean appFullscreen=false;

  final String cleanJs="(function(){try{var css='ytm-promoted-video-renderer,ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,.ytp-ad-overlay-container,.ytp-ad-image-overlay,.video-ads,.ytp-ad-text-overlay{display:none!important;}';var s=document.getElementById('pt-style');if(!s){s=document.createElement('style');s.id='pt-style';s.textContent=css;document.documentElement.appendChild(s);}function c(){try{var q=['.ytp-ad-skip-button','.ytp-ad-skip-button-modern','.ytp-skip-ad-button','button[class*=skip]'];for(var i=0;i<q.length;i++){var b=document.querySelector(q[i]);if(b){b.click();break;}}document.querySelectorAll('ytm-promoted-video-renderer,ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,.ytp-ad-overlay-container').forEach(function(e){e.remove();});}catch(e){}}c();if(!window.__ptTimer)window.__ptTimer=setInterval(c,700);}catch(e){}})();";

  int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  boolean portrait(){return getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT;}

  GradientDrawable rounded(int color,int radius){
    GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
  }

  Button btn(String t){
    Button b=new Button(this);b.setText(t);b.setTextColor(Color.WHITE);b.setTextSize(17);b.setAllCaps(false);
    b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinHeight(0);b.setBackground(rounded(Color.rgb(27,35,43),10));return b;
  }

  TextView label(String text,int size){
    TextView t=new TextView(this);t.setText(text);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setGravity(Gravity.CENTER);return t;
  }

  TextView brand(){
    TextView t=label("▶ ParkTube",18);t.setTypeface(null,1);t.setGravity(Gravity.CENTER_VERTICAL);
    t.setPadding(dp(8),0,dp(8),0);return t;
  }

  @Override public void onCreate(Bundle state){
    super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
    prefs=getSharedPreferences("parktube",MODE_PRIVATE);
    adblock=prefs.getBoolean("adblock",true);
    orientationMode=prefs.getInt("orientation_mode",0);
    driveMode=prefs.getBoolean("drive_mode",false);
    applyOrientationMode();
    keepScreen(prefs.getBoolean("keep",true));
    blocker=new AdBlocker(this);
    buildUi();configure();
    if(state==null)web.loadUrl(HOME);else web.restoreState(state);
    updateDriveModeUi();
  }

  void applyOrientationMode(){
    int requested=ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
    if(orientationMode==1)requested=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    if(orientationMode==2)requested=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    if(getRequestedOrientation()!=requested)setRequestedOrientation(requested);
  }

  String orientationLabel(){
    if(orientationMode==1)return "세로";
    if(orientationMode==2)return "가로";
    return "자동";
  }

  void cycleOrientation(){
    orientationMode=(orientationMode+1)%3;
    prefs.edit().putInt("orientation_mode",orientationMode).apply();
    if(orientationButton!=null)orientationButton.setText(orientationLabel());
    applyOrientationMode();
  }

  void buildUi(){
    root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);

    LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackgroundColor(Color.BLACK);
    root.addView(outer,new FrameLayout.LayoutParams(-1,-1));

    topBar=new LinearLayout(this); LinearLayout top=topBar;top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(5),dp(4),dp(5),dp(4));top.setBackgroundColor(Color.rgb(12,18,24));
    outer.addView(top,new LinearLayout.LayoutParams(-1,dp(portrait()?54:56)));

    if(!portrait())top.addView(brand(),new LinearLayout.LayoutParams(dp(118),-1));

    Button back=btn("‹"),fwd=btn("›");
    top.addView(back,new LinearLayout.LayoutParams(dp(portrait()?38:46),dp(46)));
    LinearLayout.LayoutParams fwdLp=new LinearLayout.LayoutParams(dp(portrait()?38:46),dp(46));
    fwdLp.leftMargin=dp(3);top.addView(fwd,fwdLp);

    search=new EditText(this);search.setSingleLine(true);search.setHint(portrait()?"YouTube 검색":"YouTube 검색 또는 URL 입력");
    search.setHintTextColor(Color.LTGRAY);search.setTextColor(Color.WHITE);search.setTextSize(portrait()?15:16);
    search.setPadding(dp(12),0,dp(12),0);search.setBackground(rounded(Color.rgb(38,47,57),10));
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(46),1);
    sp.leftMargin=dp(5);sp.rightMargin=dp(5);top.addView(search,sp);

    Button home=null,reload=null;
    if(!portrait()){
      home=btn("⌂");reload=btn("↻");
      top.addView(home,new LinearLayout.LayoutParams(dp(46),dp(46)));
      LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(46),dp(46));rp.leftMargin=dp(3);top.addView(reload,rp);
    }

    driveButton=btn("주행");driveButton.setTextSize(13);
    LinearLayout.LayoutParams dpb=new LinearLayout.LayoutParams(dp(portrait()?46:52),dp(46));
    dpb.leftMargin=dp(3);top.addView(driveButton,dpb);

    orientationButton=btn(orientationLabel());orientationButton.setTextSize(13);
    LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(dp(portrait()?46:54),dp(46));
    op.leftMargin=dp(3);top.addView(orientationButton,op);

    Button settings=btn("⚙");settings.setTextSize(18);
    LinearLayout.LayoutParams sg=new LinearLayout.LayoutParams(dp(portrait()?40:48),dp(46));
    sg.leftMargin=dp(3);top.addView(settings,sg);

    progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
    progress.setVisibility(View.GONE);outer.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));

    web=new WebView(this);web.setBackgroundColor(Color.BLACK);outer.addView(web,new LinearLayout.LayoutParams(-1,0,1));

    full=new FrameLayout(this);full.setBackgroundColor(Color.BLACK);full.setVisibility(View.GONE);
    root.addView(full,new FrameLayout.LayoutParams(-1,-1));

    buildDrivePanel();

    fullscreenButton=btn("⛶");fullscreenButton.setTextSize(23);fullscreenButton.setAlpha(0.82f);
    fullscreenButton.setContentDescription("전체화면");
    FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.BOTTOM|Gravity.RIGHT);
    fp.setMargins(0,0,dp(12),dp(12));root.addView(fullscreenButton,fp);

    back.setOnClickListener(v->{if(!driveMode&&web.canGoBack())web.goBack();});
    fwd.setOnClickListener(v->{if(!driveMode&&web.canGoForward())web.goForward();});
    if(home!=null)home.setOnClickListener(v->{if(!driveMode)web.loadUrl(HOME);});
    if(reload!=null)reload.setOnClickListener(v->{if(!driveMode)web.reload();});
    driveButton.setOnClickListener(v->setDriveMode(!driveMode));
    orientationButton.setOnClickListener(v->cycleOrientation());
    settings.setOnClickListener(v->showSettings());
    fullscreenButton.setOnClickListener(v->toggleFullscreen());

    search.setOnEditorActionListener((v,id,e)->{
      if(id==EditorInfo.IME_ACTION_SEARCH||(e!=null&&e.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
        navigate(search.getText().toString());return true;
      }
      return false;
    });
  }

  void buildDrivePanel(){
    drivePanel=new FrameLayout(this);drivePanel.setBackgroundColor(Color.rgb(6,9,12));
    root.addView(drivePanel,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);
    box.setPadding(dp(22),dp(28),dp(22),dp(28));drivePanel.addView(box,new FrameLayout.LayoutParams(-1,-1));

    TextView title=label("주행 모드",portrait()?28:32);title.setTypeface(null,1);
    box.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));

    TextView sub=label("영상은 표시하지 않고 오디오만 재생합니다",portrait()?14:16);
    sub.setTextColor(Color.LTGRAY);box.addView(sub,new LinearLayout.LayoutParams(-1,dp(38)));

    LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(portrait()?92:104));cp.topMargin=dp(20);box.addView(controls,cp);

    Button minus=btn("↶ 10초");minus.setTextSize(portrait()?18:22);
    playPauseButton=btn("▶ / Ⅱ");playPauseButton.setTextSize(portrait()?20:24);
    Button plus=btn("10초 ↷");plus.setTextSize(portrait()?18:22);

    LinearLayout.LayoutParams c1=new LinearLayout.LayoutParams(0,-1,1);c1.rightMargin=dp(8);controls.addView(minus,c1);
    LinearLayout.LayoutParams c2=new LinearLayout.LayoutParams(0,-1,1.15f);controls.addView(playPauseButton,c2);
    LinearLayout.LayoutParams c3=new LinearLayout.LayoutParams(0,-1,1);c3.leftMargin=dp(8);controls.addView(plus,c3);

    Button exit=btn("일반 화면으로 돌아가기");exit.setTextSize(portrait()?16:18);
    LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(58));ep.topMargin=dp(26);box.addView(exit,ep);

    TextView hint=label("검색은 위 검색창에서 할 수 있습니다. 주행 전에 선택해 두는 것을 권장합니다.",13);
    hint.setTextColor(Color.GRAY);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(64));hp.topMargin=dp(12);box.addView(hint,hp);

    minus.setOnClickListener(v->web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){v.currentTime=Math.max(0,v.currentTime-10);}})()",null));
    plus.setOnClickListener(v->web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){v.currentTime=Math.min(v.duration||1e9,v.currentTime+10);}})()",null));
    playPauseButton.setOnClickListener(v->web.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return 'none';if(v.paused){v.play();return 'play';}v.pause();return 'pause';})()",r->{}));
    exit.setOnClickListener(v->setDriveMode(false));
  }

  void setDriveMode(boolean on){
    driveMode=on;prefs.edit().putBoolean("drive_mode",driveMode).apply();updateDriveModeUi();
    if(driveMode){
      if(custom!=null)hideCustom();
      web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&!v.paused){return 'playing';}return 'ready';})()",null);
    }
  }

  void updateDriveModeUi(){
    if(drivePanel==null)return;
    drivePanel.setVisibility(driveMode?View.VISIBLE:View.GONE);
    drivePanel.bringToFront();
    if(fullscreenButton!=null)fullscreenButton.setVisibility(driveMode?View.GONE:View.VISIBLE);
    if(driveButton!=null){
      driveButton.setText(driveMode?"일반":"주행");
      driveButton.setBackground(rounded(driveMode?Color.rgb(29,84,123):Color.rgb(27,35,43),10));
    }
  }

  void toggleFullscreen(){
    if(driveMode)return;
    if(custom!=null){hideCustom();return;}
    if(appFullscreen){exitAppFullscreen();return;}
    tryNativeFullscreen();
  }

  void tryNativeFullscreen(){
    String js="(function(){try{var q=['.ytp-fullscreen-button','button.ytp-fullscreen-button','button[aria-label*=\\\"full screen\\\" i]','button[aria-label*=\\\"전체 화면\\\"]','.fullscreen-icon'];for(var i=0;i<q.length;i++){var b=document.querySelector(q[i]);if(b){var r=b.getBoundingClientRect();if(r.width>0&&r.height>0)return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});}}var v=document.querySelector('video');if(v&&v.webkitEnterFullscreen){try{v.webkitEnterFullscreen();return 'webkit';}catch(e){}}return 'fallback';}catch(e){return 'fallback';}})()";
    web.evaluateJavascript(js,result->{
      boolean touched=false;
      try{
        if(result!=null&&result.startsWith("\\"{")&&result.endsWith("}\\"")){
          String json=result.substring(1,result.length()-1).replace("\\\\"","\\\"");
          org.json.JSONObject o=new org.json.JSONObject(json);
          float scale=web.getScale();
          float x=(float)o.getDouble("x")*scale;
          float y=(float)o.getDouble("y")*scale;
          long now=android.os.SystemClock.uptimeMillis();
          MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
          MotionEvent up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);
          web.dispatchTouchEvent(down);web.dispatchTouchEvent(up);down.recycle();up.recycle();
          touched=true;
        }
      }catch(Exception ignored){}
      final boolean attemptedTouch=touched;
      web.postDelayed(()->{
        if(custom==null&&!appFullscreen)enterAppFullscreen();
      },attemptedTouch?550:120);
    });
  }

  void enterAppFullscreen(){
    if(driveMode||custom!=null)return;
    appFullscreen=true;
    if(topBar!=null)topBar.setVisibility(View.GONE);
    if(progress!=null)progress.setVisibility(View.GONE);
    String js="(function(){try{var v=document.querySelector('video');if(!v)return 'none';v.dataset.ptHadControls=v.hasAttribute('controls')?'1':'0';v.setAttribute('controls','controls');var p=v.closest('.html5-video-player,#player-container-id,ytm-player,.player-container')||v.parentElement||v;p.setAttribute('data-ptfs','1');var s=document.getElementById('pt-fs-style');if(!s){s=document.createElement('style');s.id='pt-fs-style';s.textContent='[data-ptfs=\\\"1\\\"]{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;z-index:2147483646!important;background:#000!important;margin:0!important;padding:0!important;}[data-ptfs=\\\"1\\\"] video{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;object-fit:contain!important;background:#000!important;}';document.documentElement.appendChild(s);}return 'ok';}catch(e){return 'error';}})()";
    web.evaluateJavascript(js,null);
    fullscreenButton.setText("×");fullscreenButton.bringToFront();immersive();
  }

  void exitAppFullscreen(){
    if(!appFullscreen)return;
    appFullscreen=false;
    String js="(function(){try{document.querySelectorAll('[data-ptfs]').forEach(function(e){e.removeAttribute('data-ptfs');});var s=document.getElementById('pt-fs-style');if(s)s.remove();var v=document.querySelector('video');if(v&&v.dataset.ptHadControls==='0')v.removeAttribute('controls');if(v)delete v.dataset.ptHadControls;}catch(e){}})()";
    web.evaluateJavascript(js,null);
    if(topBar!=null)topBar.setVisibility(View.VISIBLE);
    fullscreenButton.setText("⛶");
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
  }

  @SuppressLint("SetJavaScriptEnabled") void configure(){
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);
    s.setSupportZoom(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setCacheMode(WebSettings.LOAD_DEFAULT);
    s.setUserAgentString(s.getUserAgentString().replace("; wv",""));

    CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);

    web.setWebViewClient(new WebViewClient(){
      public void onPageStarted(WebView v,String u,Bitmap f){progress.setVisibility(View.VISIBLE);search.setText(u);}
      public void onPageFinished(WebView v,String u){search.setText(u);inject(u);}
      public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
        return adblock&&blocker.shouldBlock(r.getUrl().toString())?blocker.empty():super.shouldInterceptRequest(v,r);
      }
      public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
        Uri u=r.getUrl();String sc=u.getScheme();
        if("http".equalsIgnoreCase(sc)||"https".equalsIgnoreCase(sc))return false;
        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception x){}
        return true;
      }
    });

    web.setWebChromeClient(new WebChromeClient(){
      public void onProgressChanged(WebView v,int p){
        progress.setProgress(p);progress.setVisibility(p>=100?View.GONE:View.VISIBLE);if(p>30)inject(v.getUrl());
      }
      public void onShowCustomView(View v,CustomViewCallback cb){
        if(driveMode){cb.onCustomViewHidden();return;}
        if(custom!=null){cb.onCustomViewHidden();return;}
        custom=v;customCb=cb;full.addView(v,new FrameLayout.LayoutParams(-1,-1));full.setVisibility(View.VISIBLE);
        fullscreenButton.bringToFront();fullscreenButton.setText("×");immersive();
      }
      public void onHideCustomView(){hideCustom();}
    });
  }

  void inject(String u){
    if(adblock&&u!=null&&(u.contains("youtube.com")||u.contains("youtu.be")))web.evaluateJavascript(cleanJs,null);
  }

  void navigate(String raw){
    String q=raw==null?"":raw.trim();if(q.isEmpty())return;
    if(q.startsWith("http://")||q.startsWith("https://"))web.loadUrl(q);
    else if(q.contains(".")&&!q.contains(" "))web.loadUrl("https://"+q);
    else web.loadUrl("https://m.youtube.com/results?search_query="+Uri.encode(q));
  }

  void showSettings(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(8),dp(24),0);

    Switch ad=new Switch(this);ad.setText("광고 차단");ad.setTextSize(18);ad.setChecked(adblock);
    box.addView(ad,new LinearLayout.LayoutParams(-1,dp(58)));

    Switch keep=new Switch(this);keep.setText("화면 꺼짐 방지");keep.setTextSize(18);keep.setChecked(prefs.getBoolean("keep",true));
    box.addView(keep,new LinearLayout.LayoutParams(-1,dp(58)));

    Switch drive=new Switch(this);drive.setText("주행 모드(오디오 전용)");drive.setTextSize(18);drive.setChecked(driveMode);
    box.addView(drive,new LinearLayout.LayoutParams(-1,dp(58)));

    TextView orient=label("화면 방향: 자동 → 세로 → 가로\n현재: "+orientationLabel(),15);
    orient.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);orient.setTextColor(Color.DKGRAY);
    box.addView(orient,new LinearLayout.LayoutParams(-1,dp(64)));

    TextView info=label("Android 11(API 30) 이상\n주행 모드에서는 영상/전체화면이 차단되고 오디오 제어만 표시됩니다.",14);
    info.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);info.setTextColor(Color.DKGRAY);
    box.addView(info,new LinearLayout.LayoutParams(-1,dp(70)));

    new AlertDialog.Builder(this).setTitle("⚙ ParkTube 설정").setView(box)
      .setPositiveButton("저장",(d,w)->{
        adblock=ad.isChecked();
        prefs.edit().putBoolean("adblock",adblock).putBoolean("keep",keep.isChecked()).apply();
        keepScreen(keep.isChecked());setDriveMode(drive.isChecked());web.reload();
      }).setNegativeButton("닫기",null).show();
  }

  void keepScreen(boolean on){
    if(on)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  void immersive(){
    getWindow().getDecorView().setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
      View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  void hideCustom(){
    if(custom==null)return;
    full.removeView(custom);full.setVisibility(View.GONE);custom=null;
    if(customCb!=null)customCb.onCustomViewHidden();customCb=null;
    fullscreenButton.setText("⛶");
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
  }

  @Override public void onBackPressed(){
    if(driveMode){setDriveMode(false);return;}
    if(custom!=null)hideCustom();else if(web.canGoBack())web.goBack();else super.onBackPressed();
  }

  @Override protected void onSaveInstanceState(Bundle out){
    if(web!=null)web.saveState(out);super.onSaveInstanceState(out);
  }

  @Override protected void onDestroy(){
    if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.removeAllViews();web.destroy();}
    super.onDestroy();
  }
}