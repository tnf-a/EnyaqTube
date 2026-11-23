package de.enyaqtube

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object YouTubeWeb {

    const val MOBILE_HOME = "https://m.youtube.com/"
    const val DESKTOP_HOME = "https://www.youtube.com/"

    const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    fun home(desktop: Boolean) = if (desktop) DESKTOP_HOME else MOBILE_HOME
    fun subscriptions(desktop: Boolean) = home(desktop) + "feed/subscriptions"
    fun search(desktop: Boolean, query: String) =
        home(desktop) + "results?search_query=" + Uri.encode(query)

    /**
     * Google blockiert die Anmeldung in "eingebetteten" WebViews anhand der
     * Kennung "; wv" im User-Agent und des X-Requested-With-Headers. Beides wird
     * entfernt, damit sich die WebView wie normales Chrome meldet.
     */
    fun mobileUserAgent(context: Context): String =
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")
            .replace(Regex("Version/\\d+(\\.\\d+)* "), "")

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        desktop: Boolean,
        chromeClient: WebChromeClient? = null,
        extraScript: String? = null,
    ): WebView {
        val web = WebView(context)
        web.setBackgroundColor(Color.BLACK)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            setSupportMultipleWindows(false)
            userAgentString = if (desktop) DESKTOP_UA else mobileUserAgent(context)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(web.settings, emptySet())
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        val docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStart) {
            WebViewCompat.addDocumentStartJavaScript(
                web, AdBlocker.SCRIPT, setOf("https://*.youtube.com", "https://youtube.com")
            )
            extraScript?.let {
                WebViewCompat.addDocumentStartJavaScript(
                    web, it, setOf("https://*.youtube.com", "https://youtube.com")
                )
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest,
            ): WebResourceResponse? =
                if (AdBlocker.shouldBlock(request.url)) AdBlocker.emptyResponse() else null

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                // Nur http(s) in der App behalten; intent://, vnd.youtube:// usw. ignorieren.
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!docStart) view.evaluateJavascript(AdBlocker.SCRIPT, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // Sicherheitsnetz, falls das Skript noch nicht läuft (z. B. ältere WebView).
                view.evaluateJavascript(AdBlocker.SCRIPT, null)
                extraScript?.let { view.evaluateJavascript(it, null) }
                CookieManager.getInstance().flush()
            }
        }
        web.webChromeClient = chromeClient ?: WebChromeClient()
        return web
    }

    // --- Player-Steuerung -------------------------------------------------------

    const val JS_PAUSE =
        "(function(){var v=document.querySelector('video');if(v)v.pause();})()"
    const val JS_PLAY =
        "(function(){var v=document.querySelector('video');if(v)v.play();})()"
    const val JS_PLAY_PAUSE =
        "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused)v.play();else v.pause();})()"

    fun jsSeekTo(ms: Long) =
        "(function(){var v=document.querySelector('video');if(v)v.currentTime=${ms / 1000.0};})()"

    /**
     * Meldet den Player-Zustand (läuft?, Video-ID, Titel, Kanal, Position, Länge)
     * an die App, damit die Medienkarte in Android Auto aktuell ist.
     */
    val STATE_SCRIPT = """
(function () {
  if (window.__enyaqState) return;
  window.__enyaqState = true;
  var last = '';
  function report() {
    try {
      if (!window.EnyaqBridge) return;
      var v = document.querySelector('video');
      var id = '';
      try { id = new URL(location.href).searchParams.get('v') || ''; } catch (e) {}
      if (!id) { var m = location.pathname.match(/^\/shorts\/([^\/?]+)/); if (m) id = m[1]; }
      var title = (document.title || '').replace(/ - YouTube$/, '');
      var ch = document.querySelector(
        'ytm-slim-owner-renderer .slim-owner-channel-name, .slim-owner-channel-name, ' +
        '#owner #channel-name a, ytd-video-owner-renderer ytd-channel-name a');
      var channel = ch ? ch.textContent.trim() : '';
      var ad = !!document.querySelector('.ad-showing, .ad-interrupting');
      var playing = !!(v && !v.paused && !v.ended);
      var pos = v ? Math.floor(v.currentTime * 1000) : 0;
      var dur = (v && isFinite(v.duration)) ? Math.floor(v.duration * 1000) : 0;
      var key = [playing, id, title, channel, Math.floor(pos / 1000), Math.floor(dur / 1000), ad].join('|');
      if (key === last) return;
      last = key;
      if (!ad) EnyaqBridge.onState(playing, id, title, channel, pos, dur);
    } catch (e) {}
  }
  document.addEventListener('play', report, true);
  document.addEventListener('pause', report, true);
  document.addEventListener('ended', report, true);
  setInterval(report, 1000);
})();
""".trimIndent()

    fun jsSeek(seconds: Int) =
        "(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+($seconds));})()"
}
