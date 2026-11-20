package de.enyaqtube.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService

/**
 * Empfängt die Medientasten am Lenkrad (Play/Pause).
 *
 * Android Auto schickt Lenkradtasten an die aktive Mediensitzung. Dafür hat EnyaqTube
 * eine eigene MediaSession und holt sich den Audiofokus. Der Zustand der Sitzung bestimmt, was die Taste auslöst:
 * steht sie auf PLAYING, kommt "Pause", sonst "Play".
 */
object MediaKeys {

    interface Target {
        fun onPauseKey()
        fun onPlayKey()
        /** Eine andere App (z. B. Musik) hat übernommen. */
        fun onFocusLost()
    }

    var target: Target? = null

    private var session: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    fun session(context: Context): MediaSession =
        session ?: MediaSession(context.applicationContext, "EnyaqTube").also { s ->
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { target?.onPlayKey() }
                override fun onPause() { target?.onPauseKey() }
                override fun onStop() { target?.onPauseKey() }
            })
            s.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "EnyaqTube")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "YouTube")
                    .build()
            )
            session = s
            setPlaying(true)
        }

    fun activate(context: Context) {
        session(context).isActive = true
        requestFocus(context)
    }

    /**
     * Android Auto schickt die Lenkradtasten an die App, die zuletzt den Audiofokus
     * geholt hat und "spielt". Die WebView holt sich den Fokus nicht selbst, deshalb
     * machen wir das hier – dabei pausiert auch die Musik-App im Hintergrund.
     */
    fun requestFocus(context: Context) {
        val am = audioManager ?: context.applicationContext
            .getSystemService(AudioManager::class.java).also { audioManager = it }
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    focusRequest?.let { am.abandonAudioFocusRequest(it) }
                    target?.onFocusLost()
                }
            }
            .build().also { focusRequest = it }
        am.requestAudioFocus(req)
    }

    fun setPlaying(playing: Boolean) {
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f
                )
                .build()
        )
    }

    /** Sitzung bleibt bestehen (der Media-Dienst hält ihr Token), wird nur inaktiv. */
    fun deactivate() {
        target = null
        session?.isActive = false
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }
}

/**
 * Meldet EnyaqTube bei Android Auto als Mediaquelle an, damit die Lenkradtasten bei
 * dieser App ankommen. Eine eigene Medienbibliothek gibt es nicht (leere Liste).
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
