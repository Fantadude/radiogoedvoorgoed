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
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.radiogvg.MainActivity
import com.example.radiogvg.R
import com.example.radiogvg.network.RadioApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val PREFS_NAME = "MediaPlaybackPrefs"
private const val KEY_PODCAST_URL = "podcast_url"
private const val KEY_PODCAST_TITLE = "podcast_title"
private const val KEY_PODCAST_ARTIST = "podcast_artist"
private const val KEY_PODCAST_COVER = "podcast_cover"
private const val KEY_PODCAST_POSITION = "podcast_position"
private const val KEY_PODCAST_WAS_PLAYING = "podcast_was_playing"
private const val KEY_PLAYBACK_MODE = "playback_mode"

/**
 * MediaPlaybackService - Unified foreground service for both radio and podcast playback:
 * - Handles radio stream and podcast episodes
 * - Only one audio source plays at a time (mutual exclusivity)
 * - MediaSession for lock screen controls and Bluetooth
 * - Notification with play/pause controls
 * - Audio focus handling
 * - Background playback (won't stop when app is swiped away or navigating)
 */
class MediaPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY_RADIO = "com.example.radiogvg.ACTION_PLAY_RADIO"
        const val ACTION_PLAY_PODCAST = "com.example.radiogvg.ACTION_PLAY_PODCAST"
        const val ACTION_PAUSE = "com.example.radiogvg.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.radiogvg.ACTION_STOP"
        const val ACTION_SEEK_PODCAST = "com.example.radiogvg.ACTION_SEEK_PODCAST"
        const val EXTRA_PODCAST_URL = "podcast_url"
        const val EXTRA_PODCAST_TITLE = "podcast_title"
        const val EXTRA_PODCAST_ARTIST = "podcast_artist"
        const val EXTRA_PODCAST_COVER = "podcast_cover"
        const val EXTRA_SEEK_POSITION = "seek_position"
        const val NOTIFICATION_CHANNEL_ID = "audio_playback_channel"
        const val NOTIFICATION_ID = 1
    }

    enum class PlaybackMode { NONE, RADIO, PODCAST }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlayingState = false
    private var playbackMode = PlaybackMode.NONE
    private var isForegroundService = false

    // SharedPreferences for saving playback state
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    // Radio metadata
    private var currentTitle = "radiogoedvoorgoed"
    private var currentArtist = "Live Stream"
    private var currentArtUrl: String? = null

    // Podcast metadata
    private var podcastTitle = ""
    private var podcastArtist = ""
    private var podcastCoverUrl: String? = null
    private var podcastDuration = 0
    private var podcastPosition = 0

    private var progressUpdateJob: Job? = null

    // Radio retry management for battery optimization
    private var radioRetryCount = 0
    private var radioRetryJob: Job? = null
    private val MAX_RADIO_RETRIES = 5
    private val BASE_RETRY_DELAY_MS = 2000L // 2 seconds base delay

    // Wake locks to prevent CPU and Wi-Fi from sleeping during playback
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    // Callback interface for UI updates
    interface PlaybackCallback {
        fun onPlaybackStateChanged(isPlaying: Boolean, mode: PlaybackMode)
        fun onProgressUpdate(position: Int, duration: Int)
        fun onMetadataUpdate(title: String, artist: String, coverUrl: String?)
    }
    private var playbackCallback: PlaybackCallback? = null

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Initialize wake locks
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RadioGvG::MediaPlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "RadioGvG::MediaPlaybackWifiLock"
        ).apply {
            setReferenceCounted(false)
        }

        // Register for headphone disconnect events
        registerReceiver(
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle null intent (service restarted by system) - restore if needed
        if (intent == null) {
            restorePlaybackStateIfNeeded()
            return START_STICKY
        }

        // Ensure service is foreground immediately to avoid ANR on Android 12+
        // We'll update the notification later when playback actually starts
        if (!isForegroundService) {
            startForeground(NOTIFICATION_ID, createNotification())
            isForegroundService = true
        }

        when (intent.action) {
            ACTION_PLAY_RADIO -> playRadio()
            ACTION_PLAY_PODCAST -> {
                val url = intent.getStringExtra(EXTRA_PODCAST_URL) ?: return START_STICKY
                val title = intent.getStringExtra(EXTRA_PODCAST_TITLE) ?: "Podcast"
                val artist = intent.getStringExtra(EXTRA_PODCAST_ARTIST) ?: ""
                val cover = intent.getStringExtra(EXTRA_PODCAST_COVER)
                playPodcast(url, title, artist, cover)
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
            ACTION_SEEK_PODCAST -> {
                val position = intent.getIntExtra(EXTRA_SEEK_POSITION, 0)
                seekTo(position)
            }
        }
        return START_STICKY
    }

    private fun savePlaybackState() {
        prefs.edit().apply {
            putString(KEY_PODCAST_URL, podcastUrl)
            putString(KEY_PODCAST_TITLE, podcastTitle)
            putString(KEY_PODCAST_ARTIST, podcastArtist)
            putString(KEY_PODCAST_COVER, podcastCoverUrl)
            putInt(KEY_PODCAST_POSITION, podcastPosition)
            putBoolean(KEY_PODCAST_WAS_PLAYING, isPlayingState)
            putString(KEY_PLAYBACK_MODE, playbackMode.name)
            apply()
        }
    }

    private fun clearPlaybackState() {
        prefs.edit().apply {
            remove(KEY_PODCAST_URL)
            remove(KEY_PODCAST_TITLE)
            remove(KEY_PODCAST_ARTIST)
            remove(KEY_PODCAST_COVER)
            remove(KEY_PODCAST_POSITION)
            remove(KEY_PODCAST_WAS_PLAYING)
            remove(KEY_PLAYBACK_MODE)
            apply()
        }
    }

    private fun restorePlaybackStateIfNeeded() {
        val wasPlaying = prefs.getBoolean(KEY_PODCAST_WAS_PLAYING, false)
        if (!wasPlaying) return

        val savedMode = prefs.getString(KEY_PLAYBACK_MODE, PlaybackMode.NONE.name)
        val savedUrl = prefs.getString(KEY_PODCAST_URL, null)

        // Restore podcast only if there was an actual podcast URL
        if (savedMode == PlaybackMode.PODCAST.name && !savedUrl.isNullOrEmpty()) {
            val title = prefs.getString(KEY_PODCAST_TITLE, "Podcast") ?: "Podcast"
            val artist = prefs.getString(KEY_PODCAST_ARTIST, "") ?: ""
            val cover = prefs.getString(KEY_PODCAST_COVER, null)
            val position = prefs.getInt(KEY_PODCAST_POSITION, 0)

            // Restore and seek to saved position
            playPodcast(savedUrl, title, artist, cover, position)
        } else if (savedMode == PlaybackMode.RADIO.name) {
            // Restore radio playback
            playRadio()
        }
    }

    // Add this property to track current podcast URL
    private var podcastUrl: String = ""

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "RadioPlaybackSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    when (playbackMode) {
                        PlaybackMode.RADIO -> {
                            // Live radio should always reconnect to the live edge
                            playRadio()
                        }
                        PlaybackMode.PODCAST -> {
                            mediaPlayer?.start()
                            isPlayingState = true
                            updatePlaybackState()
                            updateNotification()
                            playbackCallback?.onPlaybackStateChanged(true, playbackMode)
                        }
                        PlaybackMode.NONE -> Unit
                    }
                }

                override fun onPause() {
                    pause()
                }

                override fun onStop() {
                    stop()
                }

                override fun onSeekTo(pos: Long) {
                    if (playbackMode == PlaybackMode.PODCAST) {
                        seekTo(pos.toInt())
                    }
                }

                override fun onSkipToNext() {
                    // Could implement skip to next podcast
                }

                override fun onSkipToPrevious() {
                    // Could implement skip to previous podcast
                }
            })

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
                        .setContentType(
                            if (playbackMode == PlaybackMode.PODCAST)
                                AudioAttributes.CONTENT_TYPE_SPEECH
                            else
                                AudioAttributes.CONTENT_TYPE_MUSIC
                        )
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

    private fun acquireWakeLocks() {
        try {
            wakeLock?.takeIf { !it.isHeld }?.acquire(10 * 60 * 1000L) // 10 minute timeout, auto-renews
            wifiLock?.takeIf { !it.isHeld }?.acquire()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLocks() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playRadio() {
        // Reset retry counter on manual play
        radioRetryCount = 0
        radioRetryJob?.cancel()
        radioRetryJob = null

        // Stop any existing podcast playback
        stopPlaybackInternal()

        if (!requestAudioFocus()) return

        playbackMode = PlaybackMode.RADIO
        currentTitle = "radiogoedvoorgoed"
        currentArtist = "Live Stream"

        // Save radio state for restoration
        savePlaybackState()

        // Acquire wake locks to prevent CPU and Wi-Fi sleep during playback
        acquireWakeLocks()

        // Ensure we're a foreground service with notification
        if (!isForegroundService) {
            startForeground(NOTIFICATION_ID, createNotification())
            isForegroundService = true
        }

        // Create MediaPlayer on background thread
        CoroutineScope(Dispatchers.IO).launch {
            createRadioMediaPlayer()
        }
    }

    private fun createRadioMediaPlayer() {
        try {
            mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
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
                    // Reset retry count on successful playback
                    radioRetryCount = 0
                    radioRetryJob?.cancel()
                    radioRetryJob = null
                    savePlaybackState()
                    CoroutineScope(Dispatchers.Main).launch {
                        updatePlaybackState()
                        updateNotification()
                        updateMediaSessionMetadata()
                        playbackCallback?.onPlaybackStateChanged(true, PlaybackMode.RADIO)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    isPlayingState = false
                    CoroutineScope(Dispatchers.Main).launch {
                        updatePlaybackState()
                        playbackCallback?.onPlaybackStateChanged(false, PlaybackMode.RADIO)
                    }
                    // Smart retry with exponential backoff for battery optimization
                    if (playbackMode == PlaybackMode.RADIO) {
                        radioRetryJob?.cancel()
                        if (radioRetryCount < MAX_RADIO_RETRIES) {
                            radioRetryCount++
                            // Exponential backoff: 2s, 4s, 8s, 16s, 32s
                            val delayMs = BASE_RETRY_DELAY_MS * (1 shl (radioRetryCount - 1))
                            radioRetryJob = CoroutineScope(Dispatchers.IO).launch {
                                delay(delayMs)
                                if (playbackMode == PlaybackMode.RADIO && !isPlayingState) {
                                    createRadioMediaPlayer()
                                }
                            }
                        }
                        // After MAX_RETRIES, stop trying to save battery - user can retry manually
                    }
                    true
                }
                setOnCompletionListener {
                    // For live stream, restart if it stops unexpectedly (with retry limit)
                    if (playbackMode == PlaybackMode.RADIO) {
                        radioRetryJob?.cancel()
                        if (radioRetryCount < MAX_RADIO_RETRIES) {
                            radioRetryCount++
                            val delayMs = BASE_RETRY_DELAY_MS * (1 shl (radioRetryCount - 1))
                            radioRetryJob = CoroutineScope(Dispatchers.IO).launch {
                                delay(delayMs)
                                if (playbackMode == PlaybackMode.RADIO && !isPlayingState) {
                                    createRadioMediaPlayer()
                                }
                            }
                        }
                    }
                }
                prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playPodcast(url: String, title: String, artist: String, coverUrl: String?, seekToPosition: Int = 0) {
        // Stop any existing radio playback
        stopPlaybackInternal()

        if (!requestAudioFocus()) return

        playbackMode = PlaybackMode.PODCAST
        podcastUrl = url
        podcastTitle = title
        podcastArtist = artist
        podcastCoverUrl = coverUrl
        podcastPosition = seekToPosition

        // Save state immediately for restoration if service is killed
        savePlaybackState()

        // Acquire wake locks to prevent CPU and Wi-Fi sleep during playback
        acquireWakeLocks()

        // Ensure we're a foreground service with notification
        if (!isForegroundService) {
            startForeground(NOTIFICATION_ID, createNotification())
            isForegroundService = true
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(url)
                    setOnPreparedListener { mp ->
                        podcastDuration = mp.duration
                        // Seek to saved position if restoring (only if duration is valid)
                        if (podcastDuration > 0 && seekToPosition > 0 && seekToPosition < podcastDuration) {
                            mp.seekTo(seekToPosition)
                        }
                        mp.start()
                        isPlayingState = true
                        // Save state now that we're playing
                        savePlaybackState()
                        CoroutineScope(Dispatchers.Main).launch {
                            updatePlaybackState()
                            updateNotification()
                            updateMediaSessionMetadata()
                            startProgressUpdates()
                            playbackCallback?.onPlaybackStateChanged(true, PlaybackMode.PODCAST)
                            playbackCallback?.onMetadataUpdate(title, artist, coverUrl)
                        }
                    }
                    setOnCompletionListener {
                        isPlayingState = false
                        stopProgressUpdates()
                        clearPlaybackState()
                        CoroutineScope(Dispatchers.Main).launch {
                            updatePlaybackState()
                            updateNotification()
                            playbackCallback?.onPlaybackStateChanged(false, PlaybackMode.PODCAST)
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlayingState = false
                        stopProgressUpdates()
                        CoroutineScope(Dispatchers.Main).launch {
                            updatePlaybackState()
                            playbackCallback?.onPlaybackStateChanged(false, PlaybackMode.PODCAST)
                        }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    playbackCallback?.onPlaybackStateChanged(false, PlaybackMode.PODCAST)
                }
            }
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            var saveCounter = 0
            while (isActive && playbackMode == PlaybackMode.PODCAST) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        podcastPosition = player.currentPosition
                        playbackCallback?.onProgressUpdate(podcastPosition, podcastDuration)
                        // Save position every 5 seconds
                        saveCounter++
                        if (saveCounter >= 5) {
                            savePlaybackState()
                            saveCounter = 0
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    fun seekTo(position: Int) {
        if (playbackMode == PlaybackMode.PODCAST) {
            mediaPlayer?.seekTo(position)
            podcastPosition = position
        }
    }

    private fun stopPlaybackInternal() {
        stopProgressUpdates()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingState = false
        playbackMode = PlaybackMode.NONE
        abandonAudioFocus()
        releaseWakeLocks()
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlayingState = false
        stopProgressUpdates()
        // Save state so we can resume later
        savePlaybackState()
        updatePlaybackState()
        updateNotification()
        playbackCallback?.onPlaybackStateChanged(false, playbackMode)
        releaseWakeLocks()
    }

    fun resume() {
        when (playbackMode) {
            PlaybackMode.RADIO -> {
                // Live radio should always resume from "now", not from a paused buffer
                playRadio()
            }
            PlaybackMode.PODCAST -> {
                if (mediaPlayer != null) {
                    // Re-acquire wake locks when resuming playback
                    acquireWakeLocks()
                    mediaPlayer?.start()
                    isPlayingState = true
                    startProgressUpdates()
                    updatePlaybackState()
                    updateNotification()
                    playbackCallback?.onPlaybackStateChanged(true, playbackMode)
                }
            }
            PlaybackMode.NONE -> Unit
        }
    }

    fun stop() {
        stopPlaybackInternal()
        // Clear saved state since user explicitly stopped
        clearPlaybackState()
        updatePlaybackState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundService = false
        stopSelf()
        playbackCallback?.onPlaybackStateChanged(false, PlaybackMode.NONE)
    }

    fun isPlaying(): Boolean = isPlayingState
    fun getPlaybackMode(): PlaybackMode = playbackMode
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    // Getters for podcast metadata
    fun getPodcastUrl(): String = podcastUrl
    fun getPodcastTitle(): String = podcastTitle
    fun getPodcastCoverUrl(): String? = podcastCoverUrl

    fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(safeVolume, safeVolume)
    }

    fun setPlaybackCallback(callback: PlaybackCallback?) {
        playbackCallback = callback
    }

    fun updateRadioMetadata(title: String, artist: String, artUrl: String?) {
        if (playbackMode == PlaybackMode.RADIO) {
            currentTitle = title
            currentArtist = artist
            currentArtUrl = artUrl
            updateMediaSessionMetadata()
            if (isPlayingState) {
                updateNotification()
            }
        }
    }

    private fun updatePlaybackState() {
        val state = if (isPlayingState) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED

        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_PLAY_PAUSE

        // Add seek actions for podcasts
        val finalActions = if (playbackMode == PlaybackMode.PODCAST) {
            actions or PlaybackState.ACTION_SEEK_TO
        } else {
            actions
        }

        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, getCurrentPosition().toLong(), 1.0f)
                .setActions(finalActions)
                .build()
        )
    }

    private fun updateMediaSessionMetadata() {
        val (title, artist) = when (playbackMode) {
            PlaybackMode.RADIO -> currentTitle to currentArtist
            PlaybackMode.PODCAST -> podcastTitle to podcastArtist
            PlaybackMode.NONE -> "radiogoedvoorgoed" to ""
        }

        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "radiogoedvoorgoed")

        // Add duration for podcasts
        if (playbackMode == PlaybackMode.PODCAST && podcastDuration > 0) {
            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, podcastDuration.toLong())
        }

        // Use cover image if available, otherwise fallback to radio logo
        builder.putBitmap(
            MediaMetadata.METADATA_KEY_ART,
            BitmapFactory.decodeResource(resources, R.drawable.radio_logo)
        )

        mediaSession?.setMetadata(builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows audio playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val (title, artist) = when (playbackMode) {
            PlaybackMode.RADIO -> currentTitle to currentArtist
            PlaybackMode.PODCAST -> podcastTitle to podcastArtist
            PlaybackMode.NONE -> "radiogoedvoorgoed" to "Paused"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = ACTION_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlayingState) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlayingState) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.radio_logo))
            .setContentIntent(contentIntent)
            .setOngoing(isPlayingState)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, playPauseTitle, pauseIntent)
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
        isForegroundService = false
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: IllegalArgumentException) {
        }
        stopProgressUpdates()
        abandonAudioFocus()
        releaseWakeLocks()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
    }
}
