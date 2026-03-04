package com.example.radiogvg.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import com.example.radiogvg.data.PodcastEpisode
import com.example.radiogvg.network.RadioApiClient
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
fun PodcastsScreen() {
    val apiClient = remember { RadioApiClient() }
    val context = LocalContext.current

    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }

    // Load episodes
    fun loadEpisodes() {
        isLoading = true
        errorMessage = null
        // Note: Using coroutine scope would be better here
    }

    // Initial load
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val result = apiClient.getPodcasts()
            result.onSuccess { episodeList ->
                episodes = episodeList
            }.onFailure { error ->
                errorMessage = error.message ?: "Failed to load podcasts"
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        }
        isLoading = false
    }

    // Progress tracking
    LaunchedEffect(currentEpisode) {
        while (isActive && currentEpisode != null) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPosition = player.currentPosition
                    duration = player.duration
                    currentProgress = if (duration > 0) {
                        currentPosition.toFloat() / duration.toFloat()
                    } else 0f
                }
            }
            delay(1000)
        }
    }

    fun playEpisode(episode: PodcastEpisode) {
        // Stop current if different episode
        if (currentEpisode?.id != episode.id) {
            mediaPlayer?.release()
            mediaPlayer = null
            currentPosition = 0
            duration = 0
            currentProgress = 0f

            try {
                val newPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(episode.audioUrl)
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPlaying = true
                        duration = mp.duration
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosition = 0
                        currentProgress = 0f
                    }
                    setOnErrorListener { _, _, _ ->
                        errorMessage = "Failed to play episode"
                        isPlaying = false
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = newPlayer
                currentEpisode = episode
            } catch (e: Exception) {
                errorMessage = "Error loading audio: ${e.message}"
            }
        } else {
            // Toggle play/pause for same episode
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    player.start()
                    isPlaying = true
                }
            }
        }
    }

    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            val newPosition = (progress * player.duration).toInt()
            player.seekTo(newPosition)
            currentPosition = newPosition
            currentProgress = progress
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
                    onClick = {
                        // Reload episodes
                    },
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

        // Player Bar (if episode selected)
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
                                imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(episodes) { episode ->
                    EpisodeCard(
                        episode = episode,
                        isPlaying = currentEpisode?.id == episode.id && isPlaying,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
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
                        .background(LightBluePrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = LightBluePrimary
                    )
                }

                // Play overlay button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(LightBluePrimary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            tint = White
                        )
                    }
                }
            }

            // Episode Info
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = episode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
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