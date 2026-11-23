package de.enyaqtube.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.media.MediaBrowserService
import java.net.URL
import kotlin.concurrent.thread

/**
 * Mediensitzung von EnyaqTube: Lenkradtasten und die Medienkarte in Android Auto.
 *
 * Der Zustand (läuft/pausiert, Titel, Vorschaubild, Position) kommt direkt aus dem
 * YouTube-Player. Er bestimmt, was die Lenkradtaste auslöst: läuft das Video, kommt
 * "Pause", sonst "Play".
 *
 * Audiofokus: Den holt sich die WebView beim Abspielen selbst. Wir fordern ihn nur
 * einmal beim Start an, damit die Musik-App im Hintergrund pausiert und Android Auto
 * EnyaqTube als Mediaquelle übernimmt. Danach greifen wir nicht mehr ein, sonst
 * nehmen wir der WebView den Fokus weg und sie stoppt das Video.
 */
object MediaKeys {

    interface Target {
        fun onPauseKey()
        fun onPlayKey()
        fun onSeekBy(seconds: Int)
        fun onSeekTo(ms: Long)
    }

    var target: Target? = null

    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    private var playing = false
    private var positionMs = 0L
    private var durationMs = 0L
    private var videoId = ""
    private var title = "EnyaqTube"
    private var channel = "YouTube"
    private var artwork: Bitmap? = null
    private var artworkFor = ""

    fun session(context: Context): MediaSession =
        session ?: MediaSession(context.applicationContext, "EnyaqTube").also { s ->
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { target?.onPlayKey() }
                override fun onPause() { target?.onPauseKey() }
                override fun onStop() { target?.onPauseKey() }
                override fun onSkipToNext() { target?.onSeekBy(10) }
                override fun onSkipToPrevious() { target?.onSeekBy(-10) }
                override fun onFastForward() { target?.onSeekBy(10) }
                override fun onRewind() { target?.onSeekBy(-10) }
                override fun onSeekTo(pos: Long) { target?.onSeekTo(pos) }
            })
            session = s
            publishMetadata()
            publishState()
        }

    fun activate(context: Context) {
        session(context).isActive = true
        val am = context.applicationContext.getSystemService(AudioManager::class.java)
        audioManager = am
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    fun deactivate() {
        target = null
        session?.isActive = false
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Sofortige Rückmeldung bei Tastendruck, bevor der Player selbst meldet. */
    fun setPlaying(value: Boolean) {
        playing = value
        publishState()
    }

    /** Vom YouTube-Player (über die JS-Brücke, schon auf dem Main-Thread). */
    fun onPlayerState(
        isPlaying: Boolean, id: String, newTitle: String, newChannel: String, pos: Long, dur: Long,
    ) {
        playing = isPlaying
        positionMs = pos
        durationMs = dur
        val metaChanged = id != videoId || newTitle != title || newChannel != channel
        videoId = id
        title = newTitle.ifBlank { "EnyaqTube" }
        channel = newChannel.ifBlank { "YouTube" }
        if (metaChanged) {
            publishMetadata()
            loadArtwork(id)
        }
        publishState()
    }

    private fun loadArtwork(id: String) {
        if (id.isEmpty()) {
            artwork = null; artworkFor = ""; publishMetadata(); return
        }
        if (id == artworkFor) return
        artworkFor = id
        thread(isDaemon = true) {
            val bmp = try {
                URL("https://i.ytimg.com/vi/$id/hqdefault.jpg").openStream().use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                null
            }
            main.post {
                if (artworkFor == id) {
                    artwork = bmp
                    publishMetadata()
                }
            }
        }
    }

    private fun publishMetadata() {
        val s = session ?: return
        val b = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, channel)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, channel)
        if (durationMs > 0) b.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        artwork?.let {
            b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            b.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
        }
        s.setMetadata(b.build())
    }

    private var publishedDuration = 0L

    private fun publishState() {
        val s = session ?: return
        if (durationMs != publishedDuration) {
            publishedDuration = durationMs
            publishMetadata()
        }
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or
                        PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs, if (playing) 1f else 0f, SystemClock.elapsedRealtime()
                )
                .build()
        )
    }
}

/**
 * Meldet EnyaqTube bei Android Auto als Mediaquelle an (Lenkradtasten, Medienkarte).
 * Eine eigene Medienbibliothek gibt es nicht (leere Liste).
 */
class MediaKeyService : MediaBrowserService() {

    override fun onCreate() {
        super.onCreate()
        sessionToken = MediaKeys.session(this).sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) =
        BrowserRoot("root", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.sendResult(mutableListOf())
    }
}
