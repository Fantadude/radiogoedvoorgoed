package com.example.radiogvg.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.radiogvg.data.PodcastEpisode
import com.example.radiogvg.service.MediaPlaybackService
import com.example.radiogvg.ui.theme.LightBlueLight
import com.example.radiogvg.ui.theme.LightBluePrimary
import com.example.radiogvg.ui.theme.LightBlueSecondary
import com.example.radiogvg.ui.theme.OffWhite
import com.example.radiogvg.ui.theme.TextDark
import com.example.radiogvg.ui.theme.TextLight
import com.example.radiogvg.ui.theme.TextMedium
import com.example.radiogvg.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PodcastsScreen(
    episodes: List<PodcastEpisode>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current

    // Service state
    var mediaService by remember { mutableStateOf<MediaPlaybackService?>(null) }
    var isServiceBound by remember { mutableStateOf(false) }

    // Playback state from service
    var isPlaying by remember { mutableStateOf(false) }
    var isServicePlayingPodcast by remember { mutableStateOf(false) }
    var currentEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }

    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as MediaPlaybackService.LocalBinder
                mediaService = binder.getService()
                isServiceBound = true

                // Set callback for updates
                mediaService?.setPlaybackCallback(object : MediaPlaybackService.PlaybackCallback {
                    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
                    override fun onPlaybackStateChanged(playing: Boolean, mode: MediaPlaybackService.PlaybackMode) {
                        isPlaying = playing
                        isServicePlayingPodcast = mode == MediaPlaybackService.PlaybackMode.PODCAST
                        if (!playing || mode != MediaPlaybackService.PlaybackMode.PODCAST) {
                            // Reset progress if stopped or switched to radio
                            if (mode == MediaPlaybackService.PlaybackMode.RADIO) {
                                currentProgress = 0f
                                currentPosition = 0
                            }
                        }
                    }

                    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
                    override fun onProgressUpdate(position: Int, dur: Int) {
                        if (isServicePlayingPodcast) {
                            currentPosition = position
                            duration = dur
                            currentProgress = if (dur > 0) position.toFloat() / dur.toFloat() else 0f
                        }
                    }

                    override fun onMetadataUpdate(title: String, artist: String, coverUrl: String?) {
                        // Update current episode info if needed
                    }
                })

                // Sync initial state
                isPlaying = mediaService?.isPlaying() == true
                isServicePlayingPodcast = mediaService?.getPlaybackMode() == MediaPlaybackService.PlaybackMode.PODCAST

                // Sync current episode from service if playing podcast
                if (isServicePlayingPodcast && mediaService != null) {
                    val serviceUrl = mediaService!!.getPodcastUrl()
                    val serviceTitle = mediaService!!.getPodcastTitle()
                    val serviceCover = mediaService!!.getPodcastCoverUrl()

                    if (serviceUrl.isNotEmpty()) {
                        // Find matching episode in list or create from service data
                        currentEpisode = episodes.find { it.audioUrl == serviceUrl }
                            ?: PodcastEpisode(
                                id = serviceUrl.hashCode().toString(),
                                title = serviceTitle,
                                description = "",
                                audioUrl = serviceUrl,
                                coverImage = serviceCover,
                                publishDate = "",
                                duration = ""
                            )

                        currentPosition = mediaService!!.getCurrentPosition()
                        duration = mediaService!!.getDuration()
                        currentProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mediaService = null
                isServiceBound = false
            }
        }
    }

    // Bind to service on startup
    DisposableEffect(context) {
        val intent = Intent(context, MediaPlaybackService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            if (isServiceBound) {
                context.unbindService(serviceConnection)
                mediaService?.setPlaybackCallback(null)
            }
        }
    }

    // Progress tracking when UI is visible
    LaunchedEffect(isServiceBound, isServicePlayingPodcast) {
        while (isActive && isServiceBound && isServicePlayingPodcast) {
            mediaService?.let { service ->
                if (service.getPlaybackMode() == MediaPlaybackService.PlaybackMode.PODCAST) {
                    currentPosition = service.getCurrentPosition()
                    duration = service.getDuration()
                    currentProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                }
            }
            delay(1000)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Handle lifecycle events - sync state when returning to screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Refresh playing state when coming back to this screen
                    isPlaying = mediaService?.isPlaying() ?: false
                    isServicePlayingPodcast = mediaService?.getPlaybackMode() == MediaPlaybackService.PlaybackMode.PODCAST
                    // Also sync current episode if playing
                    if (isServicePlayingPodcast && mediaService != null) {
                        currentPosition = mediaService!!.getCurrentPosition()
                        duration = mediaService!!.getDuration()
                        currentProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

                        // Sync current episode from service metadata
                        val serviceUrl = mediaService!!.getPodcastUrl()
                        val serviceTitle = mediaService!!.getPodcastTitle()
                        val serviceCover = mediaService!!.getPodcastCoverUrl()

                        // Find matching episode in list or create from service data
                        currentEpisode = episodes.find { it.audioUrl == serviceUrl }
                            ?: PodcastEpisode(
                                id = serviceUrl.hashCode().toString(),
                                title = serviceTitle,
                                description = "",
                                audioUrl = serviceUrl,
                                coverImage = serviceCover,
                                publishDate = "",
                                duration = ""
                            )
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun playEpisode(episode: PodcastEpisode) {
        // Check if this episode is already playing
        val isSameEpisode = currentEpisode?.id == episode.id

        if (isSameEpisode && isPlaying) {
            // Toggle pause for same episode
            mediaService?.pause()
        } else if (isSameEpisode && !isPlaying) {
            // Resume same episode
            mediaService?.resume()
        } else {
            // Start new episode - this will stop any radio playback via the service
            currentEpisode = episode
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_PLAY_PODCAST
                putExtra(MediaPlaybackService.EXTRA_PODCAST_URL, episode.audioUrl)
                putExtra(MediaPlaybackService.EXTRA_PODCAST_TITLE, episode.title)
                putExtra(MediaPlaybackService.EXTRA_PODCAST_ARTIST, "Kringloop Verhalen")
                putExtra(MediaPlaybackService.EXTRA_PODCAST_COVER, episode.coverImage)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun seekTo(progress: Float) {
        mediaService?.let { service ->
            if (service.getPlaybackMode() == MediaPlaybackService.PlaybackMode.PODCAST) {
                val newPosition = (progress * service.getDuration()).toInt()
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    action = MediaPlaybackService.ACTION_SEEK_PODCAST
                    putExtra(MediaPlaybackService.EXTRA_SEEK_POSITION, newPosition)
                }
                context.startService(intent)
                currentPosition = newPosition
                currentProgress = progress
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LightBluePrimary,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Podcasts",
                        style = MaterialTheme.typography.headlineMedium,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kringloop Verhalen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.8f)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = White
                        )
                    }
                }
            }
        }

        // Player Bar (only show if service is playing a podcast or we have a selected episode)
        if (isServicePlayingPodcast || (currentEpisode != null && !isPlaying)) {
            currentEpisode?.let { episode ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LightBlueSecondary.copy(alpha = 0.2f),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Episode info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Cover image or placeholder
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LightBluePrimary)
                            ) {
                                episode.coverImage?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = TextDark
                                )
                                Text(
                                    text = formatTime(currentPosition) + " / " + formatTime(duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMedium
                                )
                            }

                            // Play/Pause button
                            IconButton(
                                onClick = { playEpisode(episode) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying && isServicePlayingPodcast) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying && isServicePlayingPodcast) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp),
                                    tint = LightBluePrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress bar
                        Slider(
                            value = currentProgress,
                            onValueChange = { seekTo(it) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = LightBluePrimary,
                                activeTrackColor = LightBluePrimary,
                                inactiveTrackColor = LightBlueLight
                            )
                        )
                    }
                }
            }
        }

        // Error Message
        errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFEBEE)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFFE57373)
                )
            }
        }

        // Episodes List
        if (isLoading && episodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LightBluePrimary)
            }
        } else if (episodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No episodes found",
                    color = TextLight
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(episodes) { episode ->
                    val isThisEpisodePlaying = currentEpisode?.id == episode.id &&
                            isPlaying &&
                            isServicePlayingPodcast

                    EpisodeCard(
                        episode = episode,
                        isPlaying = isThisEpisodePlaying,
                        onPlayClick = { playEpisode(episode) }
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Image (smaller, compact)
        Box(
            modifier = Modifier
                .size(80.dp, 60.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            episode.coverImage?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightBluePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = White
                )
            }

            // Play overlay (only on hover/tap)
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LightBluePrimary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Playing",
                        modifier = Modifier.size(24.dp),
                        tint = White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Episode Info (compact, single line text)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = episode.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(episode.publishDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLight
                )
                if (episode.duration.isNotBlank()) {
                    Text(
                        text = episode.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}
