package de.enyaqtube

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Werbeblocker in zwei Stufen:
 *  1. Netzwerk: bekannte Werbe-/Tracking-Server werden gar nicht erst geladen.
 *  2. Seite: ein Skript entfernt die Werbe-Einträge aus den Player-Daten von YouTube,
 *     bevor der Player sie sieht, und überspringt trotzdem auftauchende Werbung.
 */
object AdBlocker {

    private val blockedHosts = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "imasdk.googleapis.com",
    )

    private val blockedYouTubePaths = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_info",
        "/youtubei/v1/player/ad_break",
    )

    fun shouldBlock(uri: Uri): Boolean {
        val host = uri.host ?: return false
        if (blockedHosts.any { host == it || host.endsWith(".$it") }) return true
        if (host.endsWith("youtube.com")) {
            val path = uri.path ?: return false
            if (blockedYouTubePaths.any { path.startsWith(it) }) return true
        }
        return false
    }

    fun emptyResponse() = WebResourceResponse(
        "text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    /** Wird möglichst vor allen Seitenskripten ausgeführt (document start). */
    val SCRIPT = """
(function () {
  if (window.__enyaqAdBlock) return;
  window.__enyaqAdBlock = true;

  var KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'adBreakParams'];
  function prune(o) {
    try {
      if (o && typeof o === 'object') {
        for (var i = 0; i < KEYS.length; i++) { if (KEYS[i] in o) delete o[KEYS[i]]; }
        if (o.playerResponse) prune(o.playerResponse);
        if (Array.isArray(o)) o.forEach(function (x) { if (x && x.playerResponse) prune(x.playerResponse); });
      }
    } catch (e) {}
    return o;
  }

  var origParse = JSON.parse;
  JSON.parse = function () { return prune(origParse.apply(this, arguments)); };

  if (window.Response && Response.prototype.json) {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () { return origJson.apply(this, arguments).then(prune); };
  }

  try {
    var initial;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return initial; },
      set: function (v) { initial = prune(v); }
    });
  } catch (e) {}

  var CSS = [
    'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer',
    'ad-slot-renderer', 'ytm-ad-slot-renderer', 'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer',
    'ytd-promoted-sparkles-web-renderer', 'ytd-banner-promo-renderer', 'ytd-display-ad-renderer',
    'ytm-rich-item-renderer:has(ad-slot-renderer)', 'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)',
    '.ytp-ad-overlay-container', '.ytp-ad-image-overlay', '#player-ads', '#masthead-ad',
    'ytm-statement-banner-renderer', 'ytd-statement-banner-renderer', 'ytm-mealbar-promo-renderer'
  ].join(',') + '{display:none !important;}';

  function addCss() {
    if (document.getElementById('enyaq-adblock-css')) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    var s = document.createElement('style');
    s.id = 'enyaq-adblock-css';
    s.textContent = CSS;
    root.appendChild(s);
  }

  var mutedByUs = false;
  setInterval(function () {
    addCss();
    var v = document.querySelector('video');
    var ad = document.querySelector('.ad-showing, .ad-interrupting');
    if (v && ad) {
      if (!v.muted) { v.muted = true; mutedByUs = true; }
      if (isFinite(v.duration) && v.duration > 0) v.currentTime = v.duration;
      try { v.playbackRate = 16; } catch (e) {}
    } else if (v && mutedByUs) {
      v.muted = false; mutedByUs = false;
      try { v.playbackRate = 1; } catch (e) {}
    }
    var skip = document.querySelectorAll(
      '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-container button');
    for (var i = 0; i < skip.length; i++) { try { skip[i].click(); } catch (e) {} }
  }, 300);
})();
""".trimIndent()
}
