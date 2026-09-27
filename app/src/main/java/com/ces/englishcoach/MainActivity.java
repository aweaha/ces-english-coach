package com.ces.englishcoach;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int PICK=1001, BLUE=Color.rgb(43,101,246), ORANGE=Color.rgb(244,139,38),
        BG=Color.rgb(247,249,252), TEXT=Color.rgb(25,31,44), MUTED=Color.rgb(102,112,133);
    final ArrayList<Lesson> lessons=new ArrayList<>();
    SharedPreferences prefs; Uri folder; MediaPlayer player; Lesson current;
    SeekBar seek; TextView time,play,analysis; LinearLayout scriptBox;
    Handler h=new Handler(Looper.getMainLooper()); float speed=1f; long a=-1,b=-1;

    @Override public void onCreate(Bundle s){
        super.onCreate(s); prefs=getSharedPreferences("ces_prefs",MODE_PRIVATE);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=getPackageManager().PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},90);
        String x=prefs.getString("folder",null); if(x!=null) folder=Uri.parse(x);
        showHome(); if(folder!=null) scan(folder);
    }
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int z,boolean bold,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(0,dp(4),0,dp(4));return v;}
    GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(round(Color.WHITE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,dp(7));c.setLayoutParams(p);return c;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}

    void showHome(){
        release(); current=null;
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(BG);
        ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(22),dp(18),dp(22));sc.addView(body);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(tv("CES English Coach",27,true,TEXT));body.addView(tv("Listen · Repeat · Shadow · Review",14,false,MUTED));
        LinearLayout hero=card();hero.addView(tv("오늘의 학습",14,true,BLUE));hero.addView(tv(prefs.getString("last","MP3 폴더를 연결해 주세요").replace(".mp3",""),20,true,TEXT));
        Button resume=btn("▶ 이어서 학습하기");resume.setTextColor(Color.WHITE);resume.setBackground(round(BLUE,14));resume.setOnClickListener(v->resume());hero.addView(resume,new LinearLayout.LayoutParams(-1,dp(52)));body.addView(hero);
        analysis=tv(status(),13,false,MUTED);LinearLayout ac=card();ac.addView(tv("AI 대화 스크립트",16,true,TEXT));ac.addView(analysis);body.addView(ac);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);title.addView(tv("Lessons",22,true,TEXT),new LinearLayout.LayoutParams(0,-2,1));Button pick=btn("＋ 폴더 선택");pick.setOnClickListener(v->pick());title.addView(pick);body.addView(title);
        if(lessons.isEmpty()){LinearLayout c=card();c.addView(tv("MP3 폴더를 선택하면 하위 폴더까지 자동 검색하고, 미분석 MP3의 영어 대본을 생성합니다.",14,false,MUTED));body.addView(c);}
        for(int i=0;i<lessons.size();i++){Lesson l=lessons.get(i);LinearLayout c=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView n=tv(String.format(Locale.US,"%02d",i+1),15,true,BLUE);row.addView(n,new LinearLayout.LayoutParams(dp(44),-2));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.addView(tv(l.clean(),16,true,TEXT));info.addView(tv(ScriptStore.has(this,l.key)?"AI 대본 완료":"대본 분석 대기/진행",12,false,ScriptStore.has(this,l.key)?Color.rgb(31,157,104):MUTED));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));row.addView(tv("›",28,false,MUTED));c.addView(row);c.setOnClickListener(v->open(l));body.addView(c);}
        setContentView(page);
    }

    String status(){if(lessons.isEmpty())return "폴더를 선택해 주세요.";int done=0;for(Lesson l:lessons)if(ScriptStore.has(this,l.key))done++;String m=prefs.getString("analysis_message","");return done+" / "+lessons.size()+" 완료"+(m.isEmpty()?"":" · "+m);}
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==PICK&&c==RESULT_OK&&d!=null&&d.getData()!=null){folder=d.getData();getContentResolver().takePersistableUriPermission(folder,Intent.FLAG_GRANT_READ_URI_PERMISSION);prefs.edit().putString("folder",folder.toString()).apply();scan(folder);}}
    void scan(Uri tree){lessons.clear();try{walk(tree,DocumentsContract.getTreeDocumentId(tree),0);}catch(Exception e){Toast.makeText(this,"폴더 오류: "+e.getMessage(),Toast.LENGTH_LONG).show();}Collections.sort(lessons,Comparator.comparing(x->x.name));showHome();startAnalysis();}
    void walk(Uri tree,String parent,int depth){if(depth>8)return;Uri u=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);String[] p={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};try(android.database.Cursor c=getContentResolver().query(u,p,null,null,null)){if(c==null)return;while(c.moveToNext()){String id=c.getString(0),name=c.getString(1),mime=c.getString(2);if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))walk(tree,id,depth+1);else if(name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".mp3")){Uri f=DocumentsContract.buildDocumentUriUsingTree(tree,id);lessons.add(new Lesson(name,f,ScriptStore.keyFor(f.toString())));}}}}
    void startAnalysis(){ArrayList<String> ns=new ArrayList<>(),us=new ArrayList<>(),ks=new ArrayList<>();for(Lesson l:lessons)if(!ScriptStore.has(this,l.key)){ns.add(l.name);us.add(l.uri.toString());ks.add(l.key);}if(ns.isEmpty())return;Intent i=new Intent(this,TranscriptionService.class);i.putStringArrayListExtra(TranscriptionService.EXTRA_NAMES,ns);i.putStringArrayListExtra(TranscriptionService.EXTRA_URIS,us);i.putStringArrayListExtra(TranscriptionService.EXTRA_KEYS,ks);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    void resume(){String last=prefs.getString("last",null);for(Lesson l:lessons)if(l.name.equals(last)){open(l);return;}if(!lessons.isEmpty())open(lessons.get(0));else pick();}
    void open(Lesson l){
        release();current=l;a=b=-1;
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(BG);
        ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(16),dp(18),dp(24));sc.addView(body);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        TextView back=tv("‹  "+l.clean(),22,true,TEXT);back.setOnClickListener(v->showHome());body.addView(back);
        LinearLayout pc=card();time=tv("00:00 / 00:00",14,false,MUTED);time.setGravity(Gravity.CENTER);pc.addView(time);seek=new SeekBar(this);pc.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){if(f&&player!=null)player.seekTo(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);Button m=btn("−5s"),p=btn("▶"),q=btn("+5s");play=p;controls.addView(m);controls.addView(p,new LinearLayout.LayoutParams(dp(90),dp(52)));controls.addView(q);m.setOnClickListener(v->jump(-5000));q.setOnClickListener(v->jump(5000));p.setOnClickListener(v->toggle());pc.addView(controls);
        LinearLayout speeds=new LinearLayout(this);for(float s:new float[]{.75f,1f,1.25f,1.5f}){Button x=btn(s+"x");x.setOnClickListener(v->{speed=s;applySpeed();});speeds.addView(x,new LinearLayout.LayoutParams(0,dp(45),1));}pc.addView(speeds);
        LinearLayout ab=new LinearLayout(this);Button aa=btn("A 설정"),bb=btn("B 설정"),clear=btn("AB 해제");aa.setOnClickListener(v->{if(player!=null)a=player.getCurrentPosition();});bb.setOnClickListener(v->{if(player!=null)b=player.getCurrentPosition();});clear.setOnClickListener(v->{a=b=-1;});ab.addView(aa,new LinearLayout.LayoutParams(0,dp(44),1));ab.addView(bb,new LinearLayout.LayoutParams(0,dp(44),1));ab.addView(clear,new LinearLayout.LayoutParams(0,dp(44),1));pc.addView(ab);body.addView(pc);
        LinearLayout sh=card();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(tv("대화 스크립트",20,true,TEXT),new LinearLayout.LayoutParams(0,-2,1));Button refresh=btn("새로고침");refresh.setOnClickListener(v->render(l));head.addView(refresh);sh.addView(head);scriptBox=new LinearLayout(this);scriptBox.setOrientation(LinearLayout.VERTICAL);sh.addView(scriptBox);body.addView(sh);setContentView(page);prepare(l);render(l);
    }
    void render(Lesson l){scriptBox.removeAllViews();File f=ScriptStore.fileFor(this,l.key);if(!f.exists()){scriptBox.addView(tv("AI가 MP3를 분석 중입니다. 분석 완료 후 새로고침을 누르세요.",14,false,MUTED));return;}try{JSONObject root=new JSONObject(read(f));JSONArray lines=root.getJSONArray("lines");for(int i=0;i<lines.length();i++){JSONObject o=lines.getJSONObject(i);String sp=o.optString("speaker","A"),en=o.optString("en",""),ko=o.optString("ko","");long start=o.optLong("startMs",-1);LinearLayout bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(dp(14),dp(10),dp(14),dp(10));boolean A=sp.equals("A");bubble.setBackground(round(A?Color.rgb(233,240,255):Color.rgb(255,242,227),16));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(A?0:dp(30),dp(5),A?dp(30):0,dp(5));bubble.setLayoutParams(bp);bubble.addView(tv(sp+"  "+fmt(start),12,true,A?BLUE:ORANGE));bubble.addView(tv(en,16,true,TEXT));if(!ko.isEmpty())bubble.addView(tv(ko,13,false,MUTED));final long go=start;bubble.setOnClickListener(v->{if(player!=null&&go>=0){player.seekTo((int)go);if(!player.isPlaying())toggle();}});final int idx=i;bubble.setOnLongClickListener(v->{toggleSpeaker(f,idx);render(l);return true;});scriptBox.addView(bubble);}}catch(Exception e){scriptBox.addView(tv("대본 읽기 오류: "+e.getMessage(),13,false,Color.RED));}}
    void toggleSpeaker(File f,int idx){try{JSONObject root=new JSONObject(read(f));JSONArray ar=root.getJSONArray("lines");JSONObject o=ar.getJSONObject(idx);o.put("speaker",o.optString("speaker","A").equals("A")?"B":"A");try(FileOutputStream out=new FileOutputStream(f)){out.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}catch(Exception ignored){}}
    String read(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);}return b.toString("UTF-8");}

    void prepare(Lesson l){try{player=new MediaPlayer();player.setDataSource(this,l.uri);player.prepare();seek.setMax(player.getDuration());long pos=prefs.getLong("pos_"+l.name,0);if(pos>0&&pos<player.getDuration())player.seekTo((int)pos);prefs.edit().putString("last",l.name).apply();applySpeed();tick.run();}catch(Exception e){Toast.makeText(this,"재생 오류: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    void toggle(){if(player==null)return;if(player.isPlaying()){player.pause();play.setText("▶");}else{player.start();play.setText("Ⅱ");}}
    void jump(int d){if(player!=null)player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+d)));}
    void applySpeed(){if(player!=null&&Build.VERSION.SDK_INT>=23)try{player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));}catch(Exception ignored){}}
    final Runnable tick=new Runnable(){public void run(){if(player!=null){int p=player.getCurrentPosition();seek.setProgress(p);time.setText(fmt(p)+" / "+fmt(player.getDuration()));if(a>=0&&b>a&&p>=b)player.seekTo((int)a);if(current!=null)prefs.edit().putLong("pos_"+current.name,p).apply();}h.postDelayed(this,500);}};
    String fmt(long ms){if(ms<0)return "--:--";long s=ms/1000;return String.format(Locale.US,"%02d:%02d",s/60,s%60);}
    void release(){h.removeCallbacks(tick);if(player!=null){try{if(current!=null)prefs.edit().putLong("pos_"+current.name,player.getCurrentPosition()).apply();player.release();}catch(Exception ignored){}player=null;}}
    @Override public void onBackPressed(){if(current!=null)showHome();else super.onBackPressed();}
    @Override protected void onDestroy(){release();super.onDestroy();}
    static class Lesson{final String name,key;final Uri uri;Lesson(String n,Uri u,String k){name=n;uri=u;key=k;}String clean(){return name.replace("[IKS] ","").replace(".mp3","");}}
}
