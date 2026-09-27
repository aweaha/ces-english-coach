package com.parktube.aabrowser;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
  static final String HOME="https://m.youtube.com/";
  WebView web; EditText search; ProgressBar progress; FrameLayout root,full; View custom;
  WebChromeClient.CustomViewCallback customCb; AdBlocker blocker; SharedPreferences prefs; boolean adblock=true;
  final String cleanJs="(function(){try{var css='ytm-promoted-video-renderer,ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,.ytp-ad-overlay-container,.ytp-ad-image-overlay,.video-ads,.ytp-ad-text-overlay{display:none!important;}';var s=document.getElementById('pt-style');if(!s){s=document.createElement('style');s.id='pt-style';s.textContent=css;document.documentElement.appendChild(s);}function c(){try{var q=['.ytp-ad-skip-button','.ytp-ad-skip-button-modern','.ytp-skip-ad-button','button[class*=skip]'];for(var i=0;i<q.length;i++){var b=document.querySelector(q[i]);if(b){b.click();break;}}document.querySelectorAll('ytm-promoted-video-renderer,ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,.ytp-ad-overlay-container').forEach(function(e){e.remove();});}catch(e){}}c();if(!window.__ptTimer)window.__ptTimer=setInterval(c,700);}catch(e){}})();";

  int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  Button btn(String t){Button b=new Button(this);b.setText(t);b.setTextColor(Color.WHITE);b.setTextSize(18);b.setAllCaps(false);b.setBackgroundColor(Color.rgb(27,35,43));return b;}
  TextView brand(){TextView t=new TextView(this);t.setText("▶  ParkTube");t.setTextColor(Color.WHITE);t.setTextSize(20);t.setTypeface(null,1);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(12),0,0,0);return t;}

  @SuppressLint("SetJavaScriptEnabled") @Override public void onCreate(Bundle state){
    super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
    prefs=getSharedPreferences("parktube",MODE_PRIVATE);adblock=prefs.getBoolean("adblock",true);
    keepScreen(prefs.getBoolean("keep",true));blocker=new AdBlocker(this);buildUi();configure();
    if(state==null)web.loadUrl(HOME);else web.restoreState(state);
  }

  void buildUi(){
    root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
    LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackgroundColor(Color.rgb(7,12,17));
    root.addView(outer,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(6),dp(5),dp(6),dp(5));top.setBackgroundColor(Color.rgb(13,20,27));
    outer.addView(top,new LinearLayout.LayoutParams(-1,dp(66)));
    top.addView(brand(),new LinearLayout.LayoutParams(dp(168),-1));
    Button back=btn("‹"),fwd=btn("›");top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));
    LinearLayout.LayoutParams lp52=new LinearLayout.LayoutParams(dp(52),dp(52));lp52.leftMargin=dp(4);top.addView(fwd,lp52);
    search=new EditText(this);search.setSingleLine(true);search.setHint("YouTube 검색 또는 URL 입력");search.setHintTextColor(Color.LTGRAY);search.setTextColor(Color.WHITE);search.setTextSize(16);search.setPadding(dp(16),0,dp(16),0);search.setBackgroundColor(Color.rgb(38,47,57));search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(52),1);sp.leftMargin=dp(8);sp.rightMargin=dp(8);top.addView(search,sp);
    Button home=btn("⌂"),reload=btn("↻"),settings=btn("⚙");top.addView(home,new LinearLayout.LayoutParams(dp(52),dp(52)));top.addView(reload,lp52);
    LinearLayout.LayoutParams sg=new LinearLayout.LayoutParams(dp(58),dp(52));sg.leftMargin=dp(4);top.addView(settings,sg);
    progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);outer.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
    web=new WebView(this);outer.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);bottom.setPadding(dp(12),0,dp(12),0);bottom.setBackgroundColor(Color.rgb(13,19,25));
    TextView status=new TextView(this);status.setText("● 광고 차단 ON   •   정차/주차 중 사용");status.setTextColor(Color.rgb(210,217,224));status.setTextSize(13);bottom.addView(status,new LinearLayout.LayoutParams(0,dp(50),1));
    Button fs=btn("⛶ 전체화면");fs.setTextSize(14);bottom.addView(fs,new LinearLayout.LayoutParams(dp(120),dp(42)));outer.addView(bottom,new LinearLayout.LayoutParams(-1,dp(54)));
    full=new FrameLayout(this);full.setBackgroundColor(Color.BLACK);full.setVisibility(View.GONE);root.addView(full,new FrameLayout.LayoutParams(-1,-1));

    back.setOnClickListener(v->{if(web.canGoBack())web.goBack();});fwd.setOnClickListener(v->{if(web.canGoForward())web.goForward();});
    home.setOnClickListener(v->web.loadUrl(HOME));reload.setOnClickListener(v->web.reload());settings.setOnClickListener(v->showSettings());
    fs.setOnClickListener(v->web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&v.requestFullscreen){v.requestFullscreen();}})()",null));
    search.setOnEditorActionListener((v,id,e)->{if(id==EditorInfo.IME_ACTION_SEARCH||(e!=null&&e.getKeyCode()==KeyEvent.KEYCODE_ENTER)){navigate(search.getText().toString());return true;}return false;});
  }

  @SuppressLint("SetJavaScriptEnabled") void configure(){
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);s.setSupportZoom(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setCacheMode(WebSettings.LOAD_DEFAULT);s.setUserAgentString(s.getUserAgentString().replace("; wv",""));
    CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
    web.setWebViewClient(new WebViewClient(){
      public void onPageStarted(WebView v,String u,Bitmap f){progress.setVisibility(View.VISIBLE);search.setText(u);}
      public void onPageFinished(WebView v,String u){search.setText(u);inject(u);}
      public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return adblock&&blocker.shouldBlock(r.getUrl().toString())?blocker.empty():super.shouldInterceptRequest(v,r);}
      public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();String sc=u.getScheme();if("http".equalsIgnoreCase(sc)||"https".equalsIgnoreCase(sc))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception x){}return true;}
    });
    web.setWebChromeClient(new WebChromeClient(){
      public void onProgressChanged(WebView v,int p){progress.setProgress(p);progress.setVisibility(p>=100?View.GONE:View.VISIBLE);if(p>30)inject(v.getUrl());}
      public void onShowCustomView(View v,CustomViewCallback cb){if(custom!=null){cb.onCustomViewHidden();return;}custom=v;customCb=cb;full.addView(v,new FrameLayout.LayoutParams(-1,-1));full.setVisibility(View.VISIBLE);immersive();}
      public void onHideCustomView(){hideCustom();}
    });
  }

  void inject(String u){if(adblock&&u!=null&&(u.contains("youtube.com")||u.contains("youtu.be")))web.evaluateJavascript(cleanJs,null);}
  void navigate(String raw){String q=raw==null?"":raw.trim();if(q.isEmpty())return;if(q.startsWith("http://")||q.startsWith("https://"))web.loadUrl(q);else if(q.contains(".")&&!q.contains(" "))web.loadUrl("https://"+q);else web.loadUrl("https://m.youtube.com/results?search_query="+Uri.encode(q));}
  void showSettings(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(8),dp(24),0);
    Switch ad=new Switch(this);ad.setText("광고 차단");ad.setTextSize(18);ad.setChecked(adblock);box.addView(ad,new LinearLayout.LayoutParams(-1,dp(58)));
    Switch keep=new Switch(this);keep.setText("화면 꺼짐 방지");keep.setTextSize(18);keep.setChecked(prefs.getBoolean("keep",true));box.addView(keep,new LinearLayout.LayoutParams(-1,dp(58)));
    TextView info=new TextView(this);info.setText("기본 화질: YouTube 자동\n홈 페이지: YouTube 모바일\nAndroid 11(API 30) 이상\n\n※ YouTube 변경에 따라 광고 차단 규칙 업데이트가 필요할 수 있습니다.");info.setTextSize(15);info.setPadding(0,dp(8),0,dp(8));box.addView(info);
    new AlertDialog.Builder(this).setTitle("⚙ ParkTube 설정").setView(box).setPositiveButton("저장",(d,w)->{adblock=ad.isChecked();prefs.edit().putBoolean("adblock",adblock).putBoolean("keep",keep.isChecked()).apply();keepScreen(keep.isChecked());web.reload();}).setNegativeButton("닫기",null).show();
  }
  void keepScreen(boolean on){if(on)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
  void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
  void hideCustom(){if(custom==null)return;full.removeView(custom);full.setVisibility(View.GONE);custom=null;if(customCb!=null)customCb.onCustomViewHidden();customCb=null;getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);}
  @Override public void onBackPressed(){if(custom!=null)hideCustom();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onSaveInstanceState(Bundle out){web.saveState(out);super.onSaveInstanceState(out);}
  @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.removeAllViews();web.destroy();}super.onDestroy();}
}