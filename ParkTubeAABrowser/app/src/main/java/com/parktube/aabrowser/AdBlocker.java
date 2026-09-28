package com.parktube.aabrowser;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AdBlocker {
  final Set<String> hosts = new HashSet<>();

  AdBlocker(Context c) {
    try (BufferedReader b = new BufferedReader(new InputStreamReader(c.getAssets().open("adblock_hosts.txt")))) {
      String s;
      while ((s = b.readLine()) != null) {
        s = s.trim().toLowerCase(Locale.US);
        if (!s.isEmpty() && !s.startsWith("#")) hosts.add(s);
      }
    } catch (Exception ignored) {}
  }

  boolean shouldBlock(String url) {
    if (url == null) return false;
    String u = url.toLowerCase(Locale.US);

    String[] pathRules = {
      "/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info",
      "doubleclick.net", "googlesyndication.com", "googleadservices.com",
      "pagead2.googlesyndication.com", "adservice.google.", "securepubads.",
      "imasdk.googleapis.com", "googleads.g.doubleclick.net"
    };
    for (String r : pathRules) if (u.contains(r)) return true;

    try {
      String h = new URI(url).getHost();
      if (h == null) return false;
      h = h.toLowerCase(Locale.US);
      for (String x : hosts) if (h.equals(x) || h.endsWith("." + x)) return true;
    } catch (Exception ignored) {}
    return false;
  }

  WebResourceResponse empty() {
    return new WebResourceResponse(
      "text/plain",
      "utf-8",
      new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
    );
  }
}
