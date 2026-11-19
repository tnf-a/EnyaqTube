package de.enyaqtube.car

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import de.enyaqtube.Prefs
import de.enyaqtube.YouTubeWeb

/**
 * Zeigt eine WebView auf dem Autodisplay an.
 *
 * Android Auto gibt Navigations-Apps eine freie Zeichenfläche (Surface). Darauf legen
 * wir ein virtuelles Display, auf dem eine Presentation mit der YouTube-WebView läuft.
 * Berührungen am Autodisplay kommen als Callbacks an und werden als Touch-Events an die
 * WebView weitergereicht.
 */
class CarBrowser(private val carContext: CarContext) : SurfaceCallback, MediaKeys.Target {

    private val prefs = Prefs(carContext)
    private val handler = Handler(Looper.getMainLooper())
    private val desktop = prefs.desktopInCar

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: BrowserPresentation? = null
    private var pendingUrl: String? = YouTubeWeb.subscriptions(desktop)
    private var width = 0
    private var height = 0
    private var visibleArea: Rect? = null

    /** Bildschirm per Lenkradtaste schwarz geschaltet und Video pausiert. */
    private var blanked = false

    init {
        MediaKeys.target = this
        MediaKeys.activate(carContext)
        MediaKeys.setPlaying(true)
    }

    // ---------------------------------------------------------------- Surface

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        width = container.width
        height = container.height
        val dpi = (container.dpi * prefs.carZoom / 100).coerceAtLeast(80)

        val vd = virtualDisplay
        if (vd == null) {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            val created = dm.createVirtualDisplay(
                "EnyaqTube", width, height, dpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )
            virtualDisplay = created
            presentation = BrowserPresentation(carContext, created.display, desktop).also {
                it.show()
                it.setBlanked(blanked)
                pendingUrl?.let { url -> it.web.loadUrl(url) }
                pendingUrl = null
            }
        } else {
            vd.resize(width, height, dpi)
            vd.surface = surface
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        virtualDisplay?.surface = null
    }

    fun release() {
        MediaKeys.deactivate()
        handler.removeCallbacksAndMessages(null)
        presentation?.let {
            it.web.stopLoading()
            it.web.destroy()
            it.dismiss()
        }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    // ---------------------------------------------------------------- Touch

    override fun onClick(x: Float, y: Float) {
        if (blanked) { onPlayKey(); return } // Tippen auf den schwarzen Bildschirm = weiter
        val t = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_DOWN, t, t, x, y)
        dispatch(MotionEvent.ACTION_UP, t, t + 60, x, y)
    }

    private var dragging = false
    private var dragDownTime = 0L
    private var dragX = 0f
    private var dragY = 0f
    private val endDrag = Runnable { finishDrag() }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (blanked) return
        val now = SystemClock.uptimeMillis()
        if (!dragging) {
            val area = visibleArea ?: Rect(0, 0, width, height)
            dragging = true
            dragDownTime = now
            dragX = area.exactCenterX()
            dragY = area.exactCenterY()
            dispatch(MotionEvent.ACTION_DOWN, dragDownTime, now, dragX, dragY)
        }
        dragX = (dragX - distanceX).coerceIn(1f, width - 1f)
        dragY = (dragY - distanceY).coerceIn(1f, height - 1f)
        dispatch(MotionEvent.ACTION_MOVE, dragDownTime, now, dragX, dragY)
        handler.removeCallbacks(endDrag)
        handler.postDelayed(endDrag, 150)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        // Sofort loslassen, damit die WebView aus den letzten Bewegungen einen Fling macht.
        handler.removeCallbacks(endDrag)
        finishDrag()
    }

    private fun finishDrag() {
        if (!dragging) return
        dragging = false
        dispatch(MotionEvent.ACTION_UP, dragDownTime, SystemClock.uptimeMillis(), dragX, dragY)
    }

    private fun dispatch(action: Int, downTime: Long, time: Long, x: Float, y: Float) {
        val target = presentation?.window?.decorView ?: return
        val ev = MotionEvent.obtain(downTime, time, action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(ev)
        ev.recycle()
    }

    // ---------------------------------------------------------------- Steuerung

    fun load(url: String) {
        val p = presentation
        if (p == null) pendingUrl = url else {
            p.exitFullscreen()
            p.web.loadUrl(url)
        }
    }

    fun home() = load(YouTubeWeb.home(desktop))
    fun subscriptions() = load(YouTubeWeb.subscriptions(desktop))
    fun search(query: String) = load(YouTubeWeb.search(desktop, query))

    fun back() {
        val p = presentation ?: return
        if (p.exitFullscreen()) return
        if (p.web.canGoBack()) p.web.goBack()
    }

    fun playPause() {
        presentation?.web?.evaluateJavascript(YouTubeWeb.JS_PLAY_PAUSE, null)
    }

    fun seek(seconds: Int) {
        presentation?.web?.evaluateJavascript(YouTubeWeb.jsSeek(seconds), null)
    }

    // ---------------------------------------------------------------- Lenkradtaste

    /** Pause: Video anhalten und Bildschirm schwarz. */
    override fun onPauseKey() {
        blanked = true
        finishDrag()
        presentation?.let {
            it.web.evaluateJavascript(YouTubeWeb.JS_PAUSE, null)
            it.setBlanked(true)
        }
        MediaKeys.setPlaying(false)
    }

    /** Nochmal drücken: Bild wieder an und weiterspielen. */
    override fun onPlayKey() {
        blanked = false
        presentation?.let {
            it.setBlanked(false)
            it.web.evaluateJavascript(YouTubeWeb.JS_PLAY, null)
        }
        MediaKeys.setPlaying(true)
    }

}

/** Fenster auf dem virtuellen Display: WebView + Vollbild-Container. */
private class BrowserPresentation(
    outer: Context,
    display: Display,
    private val desktop: Boolean,
) : Presentation(outer, display, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    lateinit var web: WebView
        private set
    private lateinit var fullscreen: FrameLayout
    private lateinit var blackout: View
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }

        web = YouTubeWeb.create(context, desktop, object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                exitFullscreen()
                customView = view
                customCallback = callback
                fullscreen.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                fullscreen.visibility = View.VISIBLE
            }

            override fun onHideCustomView() {
                removeCustomView()
            }
        })
        root.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        fullscreen = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(fullscreen, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        blackout = View(context).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(blackout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setContentView(root, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    fun setBlanked(value: Boolean) {
        blackout.visibility = if (value) View.VISIBLE else View.GONE
    }

    /** @return true, wenn ein Vollbild-Video geschlossen wurde. */
    fun exitFullscreen(): Boolean {
        val cb = customCallback ?: return false
        removeCustomView()
        cb.onCustomViewHidden()
        return true
    }

    private fun removeCustomView() {
        customView?.let { fullscreen.removeView(it) }
        customView = null
        customCallback = null
        fullscreen.visibility = View.GONE
    }
}
