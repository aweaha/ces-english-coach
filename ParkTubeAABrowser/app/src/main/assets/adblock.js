(function(){
  try {
    if (window.__pwkAdBlockInstalled) {
      if (window.__pwkSweep) window.__pwkSweep();
      return;
    }
    window.__pwkAdBlockInstalled = true;

    var css = [
      'ytm-mobile-topbar-renderer',
      'ytm-pivot-bar-renderer',
      'ytm-promoted-video-renderer',
      'ytm-promoted-sparkles-web-renderer',
      'ytm-promoted-sparkles-text-search-renderer',
      'ytm-display-ad-renderer',
      'ytm-ad-slot-renderer',
      'ytm-in-feed-ad-layout-renderer',
      'ytm-companion-ad-renderer',
      'ytd-display-ad-renderer',
      'ytd-promoted-video-renderer',
      'ytd-promoted-sparkles-web-renderer',
      'ytd-in-feed-ad-layout-renderer',
      'ytd-ad-slot-renderer',
      'ytd-companion-slot-renderer',
      'ytd-companion-ad-renderer',
      'ytd-action-companion-ad-renderer',
      'ytd-video-masthead-ad-v3-renderer',
      'ytd-video-masthead-ad-renderer',
      '.ytp-ad-overlay-container',
      '.ytp-ad-image-overlay',
      '.ytp-ad-player-overlay',
      '.ytp-ad-text-overlay'
    ].join(',') + '{display:none!important;}';

    var style = document.getElementById('pwk-clean-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'pwk-clean-style';
      style.textContent = css;
      (document.documentElement || document.head || document.body).appendChild(style);
    }

    var skipSelectors = [
      '.ytp-ad-skip-button',
      '.ytp-ad-skip-button-modern',
      '.ytp-skip-ad-button',
      '.ytp-ad-skip-button-slot button',
      'button[aria-label^="Skip"]',
      'button[aria-label*="Skip ad"]',
      'button[aria-label*="건너뛰기"]',
      'button[aria-label*="광고 건너뛰기"]'
    ];

    function visible(el) {
      if (!el) return false;
      var r = el.getBoundingClientRect();
      var s = getComputedStyle(el);
      return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden';
    }

    function clickSkip() {
      for (var i = 0; i < skipSelectors.length; i++) {
        var nodes = document.querySelectorAll(skipSelectors[i]);
        for (var j = 0; j < nodes.length; j++) {
          if (visible(nodes[j])) {
            try { nodes[j].click(); return true; } catch (e) {}
          }
        }
      }
      return false;
    }

    function removeStaticAds() {
      var q = [
        'ytm-promoted-video-renderer',
        'ytm-promoted-sparkles-web-renderer',
        'ytm-promoted-sparkles-text-search-renderer',
        'ytm-display-ad-renderer',
        'ytm-ad-slot-renderer',
        'ytm-in-feed-ad-layout-renderer',
        'ytm-companion-ad-renderer',
        'ytd-display-ad-renderer',
        'ytd-promoted-video-renderer',
        'ytd-promoted-sparkles-web-renderer',
        'ytd-in-feed-ad-layout-renderer',
        'ytd-ad-slot-renderer',
        'ytd-companion-slot-renderer',
        'ytd-companion-ad-renderer'
      ];
      for (var i = 0; i < q.length; i++) {
        document.querySelectorAll(q[i]).forEach(function(e){ try { e.remove(); } catch (x) {} });
      }
    }

    function handleVideoAd() {
      var player = document.querySelector('.html5-video-player, #movie_player');
      var adState = player && (
        player.classList.contains('ad-showing') ||
        player.classList.contains('ad-interrupting')
      );

      var overlay = document.querySelector('.ytp-ad-player-overlay-layout__ad-info-container, .ytp-ad-preview-container');
      if (!adState && overlay && visible(overlay)) adState = true;

      var v = document.querySelector('video.video-stream, video');
      if (adState && v) {
        clickSkip();
        if (window.__pwkSavedMuted === undefined) window.__pwkSavedMuted = !!v.muted;
        if (window.__pwkSavedRate === undefined) window.__pwkSavedRate = v.playbackRate || 1;
        try { v.muted = true; } catch (e) {}
        try { v.playbackRate = 16; } catch (e) {}
        try {
          if (isFinite(v.duration) && v.duration > 0 && v.duration < 600) {
            v.currentTime = Math.max(v.currentTime || 0, v.duration - 0.15);
          }
        } catch (e) {}
      } else if (v) {
        if (window.__pwkSavedMuted !== undefined) {
          try { v.muted = window.__pwkSavedMuted; } catch (e) {}
          delete window.__pwkSavedMuted;
        }
        if (window.__pwkSavedRate !== undefined) {
          try { v.playbackRate = window.__pwkSavedRate; } catch (e) {}
          delete window.__pwkSavedRate;
        }
      }
    }

    window.__pwkSweep = function() {
      try {
        removeStaticAds();
        clickSkip();
        handleVideoAd();
      } catch (e) {}
    };

    var observer = new MutationObserver(function(){ window.__pwkSweep(); });
    observer.observe(document.documentElement || document, {subtree:true, childList:true, attributes:true});
    window.__pwkAdTimer = setInterval(window.__pwkSweep, 250);
    window.__pwkSweep();
  } catch (e) {}
})();