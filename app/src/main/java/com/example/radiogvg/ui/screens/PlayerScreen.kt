package com.example.radiogvg.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
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
import com.example.radiogvg.service.MediaPlaybackService
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

    // Service binding
    var mediaService by remember { mutableStateOf<MediaPlaybackService?>(null) }
    var isServiceBound by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var nowPlaying by remember { mutableStateOf<CentovaNowPlaying?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as MediaPlaybackService.LocalBinder
                mediaService = binder.getService()
                isServiceBound = true
                isPlaying = mediaService?.isPlaying() ?: false
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
            }
        }
    }

    // Update now playing info periodically
    LaunchedEffect(Unit) {
        // Immediate first fetch
        try {
            val result = apiClient.getNowPlaying()
            result.onSuccess { source ->
                nowPlaying = source
                if (isServiceBound) {
                    mediaService?.updateMetadata(
                        title = source?.title ?: "radiogoedvoorgoed",
                        artist = source?.artist ?: "",
                        artUrl = source?.art
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore initial fetch errors
        }
        
        // Then poll every 10 seconds (faster updates)
        while (isActive) {
            delay(10000)
            try {
                val result = apiClient.getNowPlaying()
                result.onSuccess { source ->
                    nowPlaying = source
                    if (isServiceBound) {
                        mediaService?.updateMetadata(
                            title = source?.title ?: "radiogoedvoorgoed",
                            artist = source?.artist ?: "",
                            artUrl = source?.art
                        )
                    }
                    errorMessage = null
                }.onFailure {
                    // Silently fail - keep showing last info
                }
            } catch (e: Exception) {
                // Ignore errors in polling
            }
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Don't stop playback - service handles background
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Refresh playing state when coming back
                    isPlaying = mediaService?.isPlaying() ?: false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun togglePlayback() {
        if (isServiceBound) {
            isLoading = true
            if (mediaService?.isPlaying() == true) {
                mediaService?.pause()
                isPlaying = false
            } else {
                // Start the service first
                val intent = Intent(context, MediaPlaybackService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                mediaService?.play()
                isPlaying = true
            }
            isLoading = false
        }
    }

    fun updateVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        mediaService?.setVolume(volume)
    }

    // Extract artist and title from Centova response
    val title = nowPlaying?.title ?: "radiogoedvoorgoed"
    val artist = nowPlaying?.artist ?: ""
    val albumArtUrl = nowPlaying?.art

    if (isLandscape) {
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App name header
        Text(
            text = "radiogoedvoorgoed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = LightBluePrimary
        )

        // Album Art with Logo overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtWithLogo(
                isPlaying = isPlaying,
                artist = artist,
                title = title,
                artworkUrl = albumArtUrl,
                fallbackLogo = painterResource(id = R.drawable.radio_logo),
                maxHeightFraction = 0.7f
            )
        }

        // Track Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMedium,
                        textAlign = TextAlign.Center
                    )
                }
                nowPlaying?.let { np ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when {
                            isLoading -> {
                                // Show spinning loading indicator
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = LightBluePrimary,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Connecting...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMedium
                                )
                            }
                            isPlaying -> {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Green, CircleShape)
                                )
                                Text(
                                    text = "Live • ${np.listeners} listeners",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMedium
                                )
                            }
                            else -> {
                                Text(
                                    text = "● Paused",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error Message
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5))
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD32F2F),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Play/Pause Button
        PlayPauseButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            onTogglePlayback = onTogglePlayback
        )

        // Volume Control
        VolumeControl(
            volume = volume,
            onVolumeChange = onVolumeChange
        )
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
        // Left side: Album Art
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
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = LightBluePrimary,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Connecting...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMedium
                                )
                            }
                            isPlaying -> {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.Green, CircleShape)
                                )
                                Text(
                                    text = "Live • ${nowPlaying?.listeners ?: 0} listeners",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMedium
                                )
                            }
                            else -> {
                                Text(
                                    text = "● Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }

            // Error Message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5))
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD32F2F),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // Play/Pause Button (compact)
            PlayPauseButton(
                isPlaying = isPlaying,
                isLoading = isLoading,
                onTogglePlayback = onTogglePlayback,
                modifier = Modifier.size(56.dp)
            )

            // Volume Control (compact)
            VolumeControl(
                volume = volume,
                onVolumeChange = onVolumeChange,
                compact = true
            )
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.95f,
        animationSpec = tween(200),
        label = "button_scale"
    )

    Button(
        onClick = onTogglePlayback,
        enabled = !isLoading,
        modifier = modifier
            .size(80.dp)
            .scale(scale),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = LightBluePrimary,
            disabledContainerColor = LightBlueLight
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = White,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = if (isPlaying) {
                    Icons.Filled.PlayArrow
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
                tint = White
            )
        }
    }
}

@Composable
private fun VolumeControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (volume > 0) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
            contentDescription = "Volume",
            tint = TextMedium,
            modifier = Modifier.size(if (compact) 20.dp else 24.dp)
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
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextDark,
            modifier = Modifier.width(if (compact) 32.dp else 36.dp)
        )
    }
}

@Composable
private fun AlbumArtWithLogo(
    isPlaying: Boolean,
    artist: String,
    title: String,
    artworkUrl: String?,
    fallbackLogo: androidx.compose.ui.graphics.painter.Painter,
    maxHeightFraction: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Album art or visualizer
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = "Album Art",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            // Animated placeholder when playing, static when paused
            Box(
                modifier = Modifier
                    .fillMaxHeight(maxHeightFraction)
                    .aspectRatio(1f)
                    .scale(if (isPlaying) scale else 1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LightBlueLight),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    // Visualizer bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            val barScale by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        500 + index * 100,
                                        easing = FastOutSlowInEasing
                                    ),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bar_$index"
                            )
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .heightIn(max = 60.dp)
                                    .fillMaxHeight(barScale)
                                    .background(White, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                } else {
                    // Music note icon
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "radiogoedvoorgoed",
                        modifier = Modifier.size(64.dp),
                        tint = LightBluePrimary
                    )
                }
            }
        }

        // Logo overlay - only show when no album art
        if (artworkUrl == null) {
            Image(
                painter = fallbackLogo,
                contentDescription = "radiogoedvoorgoed",
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .aspectRatio(1f)
                    .align(Alignment.Center),
                alpha = 0.7f
            )
        }
    }
}
