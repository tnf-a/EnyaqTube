package de.enyaqtube

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Handy-Bildschirm: hier meldest du dich einmal bei YouTube an und stellst die App ein.
 * Die Anmeldung (Cookies) wird automatisch auch im Auto verwendet.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        status = TextView(this).apply {
            setPadding(dp(12), dp(10), dp(12), dp(4))
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }
        root.addView(status)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            buttons.addView(this, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        button("Anmelden") { web.loadUrl(YouTubeWeb.LOGIN_URL) }
        button("Abos") { web.loadUrl(YouTubeWeb.subscriptions(false)) }
        button("Startseite") { web.loadUrl(YouTubeWeb.MOBILE_HOME) }
        button("Zoom −") { changeZoom(-10) }
        button("Zoom +") { changeZoom(+10) }
        button("Abmelden") { logout() }
        root.addView(HorizontalScrollView(this).apply { addView(buttons) })

        fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
            text = label
            isChecked = checked
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnCheckedChangeListener { _, v -> onChange(v) }
            root.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        switch("Desktop-Ansicht im Auto", prefs.desktopInCar) {
            prefs.desktopInCar = it
            toast("Wird beim nächsten Start im Auto übernommen")
        }

        web = YouTubeWeb.create(this, desktop = false)
        root.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setContentView(root)
        root.fitsSystemWindows = true

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(YouTubeWeb.MOBILE_HOME)

        updateStatus()
    }

    private fun changeZoom(delta: Int) {
        prefs.carZoom = (prefs.carZoom + delta).coerceIn(50, 200)
        updateStatus()
        toast("Zoom im Auto: ${prefs.carZoom} %")
    }

    private fun logout() {
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        web.clearCache(true)
        web.loadUrl(YouTubeWeb.MOBILE_HOME)
        toast("Abgemeldet")
    }

    private fun updateStatus() {
        status.text = "Hier einmal anmelden – danach in Android Auto „EnyaqTube“ öffnen. " +
            "Zoom im Auto: ${prefs.carZoom} %."
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
