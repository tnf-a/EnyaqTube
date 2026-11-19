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
                CookieManager.getInstance().flush()
            }
        }
        web.webChromeClient = chromeClient ?: WebChromeClient()
        return web
    }

    // --- Player-Steuerung -------------------------------------------------------

    const val JS_PLAY_PAUSE =
        "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused)v.play();else v.pause();})()"
    const val JS_PAUSE =
        "(function(){var v=document.querySelector('video');if(v)v.pause();})()"

    fun jsSeek(seconds: Int) =
        "(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+($seconds));})()"
}
