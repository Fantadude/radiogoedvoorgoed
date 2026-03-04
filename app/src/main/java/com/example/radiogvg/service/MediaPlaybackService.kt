package com.example.radiogvg.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.radiogvg.MainActivity
import com.example.radiogvg.R
import com.example.radiogvg.network.RadioApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MediaPlaybackService - Foreground service for radio playback with:
 * - MediaSession for lock screen controls and Bluetooth
 * - Notification with play/pause controls
 * - Audio focus handling
 * - Background playback (won't stop when app is swiped away)
 */
class MediaPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY = "com.example.radiogvg.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.radiogvg.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.radiogvg.ACTION_STOP"
        const val NOTIFICATION_CHANNEL_ID = "radio_playback_channel"
        const val NOTIFICATION_ID = 1
    }

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlayingState = false
    private var currentTitle = "radiogoedvoorgoed"
    private var currentArtist = ""
    private var currentArtUrl: String? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Pause when headphones are disconnected
                pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Register for headphone disconnect events
        registerReceiver(
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
        }
        return START_STICKY // Keep service running even if app is killed
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "RadioPlaybackSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    play()
                }

                override fun onPause() {
                    pause()
                }

                override fun onStop() {
                    stop()
                }
            })

            // Enable callbacks from Bluetooth, lock screen, etc.
            @Suppress("DEPRECATION")
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            isActive = true
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            // Can resume if needed
                        }
                    }
                }
                .build()
            audioFocusRequest = request
            audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            pause()
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    fun play() {
        if (!requestAudioFocus()) return

        if (mediaPlayer == null) {
            // Create MediaPlayer on background thread to avoid blocking main thread
            CoroutineScope(Dispatchers.IO).launch {
                createMediaPlayer()
            }
        } else {
            mediaPlayer?.start()
            isPlayingState = true
            updatePlaybackState()
            updateNotification()
            updateMediaSessionMetadata()
        }
    }

    fun pause() {
        // Completely stop and release the MediaPlayer to prevent stale buffer issues
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingState = false
        abandonAudioFocus()
        updatePlaybackState()
        updateNotification()
        updateMediaSessionMetadata()
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingState = false
        abandonAudioFocus()
        updatePlaybackState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun isPlaying(): Boolean = isPlayingState

    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    fun updateMetadata(title: String, artist: String, artUrl: String?) {
        currentTitle = title
        currentArtist = artist
        currentArtUrl = artUrl
        updateMediaSessionMetadata()
        if (isPlayingState) {
            updateNotification()
        }
    }

    private fun createMediaPlayer() {
        try {
            mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(RadioApiClient.STREAM_URL)
                setOnPreparedListener {
                    it.start()
                    isPlayingState = true
                    // UI updates must be on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        updatePlaybackState()
                        startForeground(NOTIFICATION_ID, createNotification())
                        updateMediaSessionMetadata()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    isPlayingState = false
                    CoroutineScope(Dispatchers.Main).launch {
                        updatePlaybackState()
                    }
                    true
                }
                setOnCompletionListener {
                    // For live stream, this shouldn't happen, but restart if it does
                    createMediaPlayer()
                }
                // Use sync prepare on IO thread - it's faster for live streams
                prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If prepare fails, try async as fallback on main thread
            try {
                CoroutineScope(Dispatchers.Main).launch {
                    mediaPlayer?.prepareAsync()
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun updatePlaybackState() {
        val state = if (isPlayingState) {
            PlaybackState.STATE_PLAYING
        } else {
            PlaybackState.STATE_PAUSED
        }

        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_PLAY_PAUSE
                )
                .build()
        )
    }

    private fun updateMediaSessionMetadata() {
        mediaSession?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "radiogoedvoorgoed")
                .putBitmap(
                    MediaMetadata.METADATA_KEY_ART,
                    BitmapFactory.decodeResource(resources, R.drawable.radio_logo)
                )
                .build()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows radio playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = ACTION_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlayingState) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlayingState) "Pause" else "Play"
        val playPauseIntent = if (isPlayingState) pauseIntent else playIntent

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist.ifBlank { "Live Stream" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.radio_logo))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(android.support.v4.media.session.MediaSessionCompat.Token.fromToken(mediaSession?.sessionToken))
                    .setShowActionsInCompactView(0)
            )

        return builder.build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        if (isPlayingState) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver wasn't registered
        }
        abandonAudioFocus()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
    }
}
