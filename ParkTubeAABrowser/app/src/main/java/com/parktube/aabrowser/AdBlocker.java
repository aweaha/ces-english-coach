package com.parktube.aabrowser;
import android.content.Context;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class AdBlocker{
  final Set<String> hosts=new HashSet<>();
  AdBlocker(Context c){try(BufferedReader b=new BufferedReader(new InputStreamReader(c.getAssets().open("adblock_hosts.txt")))){String s;while((s=b.readLine())!=null){s=s.trim().toLowerCase();if(!s.isEmpty()&&!s.startsWith("#"))hosts.add(s);}}catch(Exception e){}}
  boolean shouldBlock(String url){try{String h=new URI(url).getHost();if(h==null)return false;h=h.toLowerCase();for(String x:hosts)if(h.equals(x)||h.endsWith("."+x))return true;}catch(Exception e){}return false;}
  WebResourceResponse empty(){return new WebResourceResponse("text/plain","utf-8",new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));}
}