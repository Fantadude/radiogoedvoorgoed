package com.example.radiogvg.ui.screens

import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.radiogvg.R
import com.example.radiogvg.data.CentovaNowPlaying
import com.example.radiogvg.network.RadioApiClient
import com.example.radiogvg.ui.theme.LightBlueLight
import com.example.radiogvg.ui.theme.LightBluePrimary
import com.example.radiogvg.ui.theme.LightBlueSecondary
import com.example.radiogvg.ui.theme.OffWhite
import com.example.radiogvg.ui.theme.TextDark
import com.example.radiogvg.ui.theme.TextMedium
import com.example.radiogvg.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val apiClient = remember { RadioApiClient() }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var nowPlaying by remember { mutableStateOf<CentovaNowPlaying?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Update now playing info periodically
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                val result = apiClient.getNowPlaying()
                result.onSuccess { source ->
                    nowPlaying = source
                    errorMessage = null
                }.onFailure {
                    // Silently fail - keep showing last info
                }
            } catch (e: Exception) {
                // Ignore errors in polling
            }
            delay(15000) // Update every 15 seconds
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Don't stop playback - let it continue in background
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Refresh now playing when coming back
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mediaPlayer?.release()
                    mediaPlayer = null
                    isPlaying = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaPlayer?.release()
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        } else {
            if (mediaPlayer == null) {
                isLoading = true
                errorMessage = null

                try {
                    val newPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(RadioApiClient.STREAM_URL)
                        setOnPreparedListener { mp ->
                            mp.start()
                            mp.setVolume(volume, volume)
                            isPlaying = true
                            isLoading = false
                        }
                        setOnErrorListener { _, what, extra ->
                            errorMessage = "Playback error: $what"
                            isLoading = false
                            isPlaying = false
                            true
                        }
                        prepareAsync()
                    }
                    mediaPlayer = newPlayer
                } catch (e: Exception) {
                    errorMessage = "Failed to load stream"
                    isLoading = false
                }
            } else {
                mediaPlayer?.start()
                isPlaying = true
            }
        }
    }

    fun updateVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(volume, volume)
    }

    // Extract artist and title from Centova response
    val title = nowPlaying?.title ?: "radiogoedvoorgoed"
    val artist = nowPlaying?.artist ?: ""
    val albumArtUrl = nowPlaying?.art

    if (isLandscape) {
        // Landscape layout - compact, horizontal
        LandscapePlayerLayout(
            title = title,
            artist = artist,
            albumArtUrl = albumArtUrl,
            isPlaying = isPlaying,
            isLoading = isLoading,
            volume = volume,
            nowPlaying = nowPlaying,
            errorMessage = errorMessage,
            onTogglePlayback = { togglePlayback() },
            onVolumeChange = { updateVolume(it) }
        )
    } else {
        // Portrait layout - original vertical layout
        PortraitPlayerLayout(
            title = title,
            artist = artist,
            albumArtUrl = albumArtUrl,
            isPlaying = isPlaying,
            isLoading = isLoading,
            volume = volume,
            nowPlaying = nowPlaying,
            errorMessage = errorMessage,
            onTogglePlayback = { togglePlayback() },
            onVolumeChange = { updateVolume(it) }
        )
    }
}

@Composable
private fun PortraitPlayerLayout(
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    nowPlaying: CentovaNowPlaying?,
    errorMessage: String?,
    onTogglePlayback: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Radio Logo (small, at top)
        Image(
            painter = painterResource(id = R.drawable.radio_logo),
            contentDescription = "radiogoedvoorgoed",
            modifier = Modifier
                .size(48.dp)
                .padding(top = 4.dp),
            contentScale = ContentScale.Fit
        )

        // App name below logo
        Text(
            text = "radiogoedvoorgoed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LightBluePrimary,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        // Album Art - takes most space (flexible)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtWithLogo(
                isPlaying = isPlaying,
                artist = artist,
                title = title,
                artworkUrl = albumArtUrl,
                fallbackLogo = painterResource(id = R.drawable.radio_logo),
                maxHeightFraction = 0.85f
            )
        }

        // Track Info Card (compact)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Song Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                // Artist
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMedium,
                        maxLines = 1
                    )
                }

                // Live indicator / Paused text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Text(
                            text = "Live • ${nowPlaying?.listeners ?: 0} listeners",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMedium
                        )
                    } else {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMedium
                        )
                    }
                }
            }
        }

        // Recently Played & Coming Soon (horizontal, compact)
        if (!nowPlaying?.history.isNullOrEmpty() || !nowPlaying?.comingsoon.isNullOrEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = LightBlueSecondary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Recently Played Column
                    if (!nowPlaying?.history.isNullOrEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Recently",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = LightBluePrimary
                            )
                            nowPlaying?.history?.take(3)?.forEach { track ->
                                Text(
                                    text = track,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark,
                                    maxLines = 1,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Coming Soon Column
                    if (!nowPlaying?.comingsoon.isNullOrEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Coming Soon",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = LightBluePrimary
                            )
                            nowPlaying?.comingsoon?.take(3)?.forEach { track ->
                                Text(
                                    text = track,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark,
                                    maxLines = 1,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error Message (if any)
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(8.dp),
                    color = Color(0xFFE57373),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Play Button (centered, prominent)
        FilledIconButton(
            onClick = onTogglePlayback,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .padding(vertical = 8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = LightBluePrimary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }

        // Volume Slider (compact)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = LightBluePrimary,
                    modifier = Modifier.size(20.dp)
                )

                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = LightBluePrimary,
                        activeTrackColor = LightBluePrimary,
                        inactiveTrackColor = LightBlueLight
                    )
                )

                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextDark,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

@Composable
private fun LandscapePlayerLayout(
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    nowPlaying: CentovaNowPlaying?,
    errorMessage: String?,
    onTogglePlayback: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Album Art (small, compact - fixed size)
        Box(
            modifier = Modifier
                .sizeIn(maxHeight = 180.dp, maxWidth = 180.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtWithLogo(
                isPlaying = isPlaying,
                artist = artist,
                title = title,
                artworkUrl = albumArtUrl,
                fallbackLogo = painterResource(id = R.drawable.radio_logo),
                maxHeightFraction = 1f
            )
        }

        // Right side: All controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            // App name
            Text(
                text = "radiogoedvoorgoed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LightBluePrimary
            )

            // Track Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMedium,
                            maxLines = 1
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Text(
                                text = "Live • ${nowPlaying?.listeners ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMedium
                            )
                        } else {
                            Text(
                                text = "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMedium
                            )
                        }
                    }
                }
            }

            // Recently Played (horizontal chips)
            if (!nowPlaying?.history.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = LightBlueSecondary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Recent:",
                            style = MaterialTheme.typography.labelSmall,
                            color = LightBluePrimary,
                            fontWeight = FontWeight.Medium
                        )
                        nowPlaying?.history?.take(2)?.forEach { track ->
                            Text(
                                text = "• $track",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDark,
                                maxLines = 1,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Error Message
            errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE57373)
                )
            }

            // Play Button
            FilledIconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = LightBluePrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
            }

            // Volume Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = LightBluePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = LightBluePrimary,
                            activeTrackColor = LightBluePrimary,
                            inactiveTrackColor = LightBlueLight
                        )
                    )
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark,
                        modifier = Modifier.width(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumArtWithLogo(
    isPlaying: Boolean,
    artist: String,
    title: String,
    artworkUrl: String?,
    fallbackLogo: androidx.compose.ui.graphics.painter.Painter,
    maxHeightFraction: Float = 0.85f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // State for album art URL
    var albumArtUrl by remember { mutableStateOf<String?>(artworkUrl) }
    var isLoadingArt by remember { mutableStateOf(false) }

    // Fetch album art when track changes or artworkUrl from stream changes
    LaunchedEffect(artworkUrl, artist, title) {
        if (!artworkUrl.isNullOrBlank()) {
            albumArtUrl = artworkUrl
            return@LaunchedEffect
        }

        if (artist.isNotBlank() && title.isNotBlank() && title != "radiogoedvoorgoed") {
            isLoadingArt = true
            try {
                val deezerSearchTerm = java.net.URLEncoder.encode("$artist $title", "UTF-8")
                val deezerUrl = java.net.URL("https://api.deezer.com/search?q=$deezerSearchTerm&limit=1")
                val deezerConnection = deezerUrl.openConnection()
                deezerConnection.setRequestProperty("User-Agent", "radiogoedvoorgoed-Android-App")
                deezerConnection.connectTimeout = 5000
                deezerConnection.readTimeout = 5000

                val deezerReader = java.io.BufferedReader(java.io.InputStreamReader(deezerConnection.getInputStream()))
                val deezerResponse = deezerReader.readText()
                deezerReader.close()

                val coverRegex = """"cover":"([^"]+)"|"cover_big":"([^"]+)"|"cover_medium":"([^"]+)"|"cover_xl":"([^"]+)"""".toRegex()
                val deezerMatch = coverRegex.find(deezerResponse)

                if (deezerMatch != null) {
                    albumArtUrl = deezerMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                        ?.replace("\\/", "/")
                }

                if (albumArtUrl == null) {
                    val mbSearchTerm = java.net.URLEncoder.encode("$artist $title", "UTF-8")
                    val mbUrl = java.net.URL("https://musicbrainz.org/ws/2/recording/?query=$mbSearchTerm&limit=1&fmt=json")
                    val mbConnection = mbUrl.openConnection()
                    mbConnection.setRequestProperty("User-Agent", "radiogoedvoorgoed-Android-App/1.0")
                    mbConnection.connectTimeout = 5000
                    mbConnection.readTimeout = 5000

                    val mbReader = java.io.BufferedReader(java.io.InputStreamReader(mbConnection.getInputStream()))
                    val mbResponse = mbReader.readText()
                    mbReader.close()

                    val releaseRegex = """"releases":\s*\[.*?"id":\s*"([^"]+)"""".toRegex()
                    val releaseMatch = releaseRegex.find(mbResponse)

                    if (releaseMatch != null) {
                        val releaseId = releaseMatch.groupValues[1]
                        albumArtUrl = "https://coverartarchive.org/release/$releaseId/front"
                    }
                }
            } catch (e: Exception) {
                albumArtUrl = null
            }
            isLoadingArt = false
        } else {
            albumArtUrl = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight(maxHeightFraction)
            .wrapContentWidth()
            .aspectRatio(1f)
            .scale(if (isPlaying) scale else 1f)
            .clip(RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUrl != null) {
            AsyncImage(
                model = albumArtUrl,
                contentDescription = "$artist - $title",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = fallbackLogo,
                error = fallbackLogo
            )
        } else {
            Image(
                painter = fallbackLogo,
                contentDescription = "radiogoedvoorgoed Logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isLoadingArt) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
