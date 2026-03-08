package com.example.radiogvg.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
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
import androidx.compose.material.icons.filled.Pause
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.radiogvg.R
import com.example.radiogvg.data.CentovaNowPlaying
import com.example.radiogvg.network.RadioApiClient
import com.example.radiogvg.service.MediaPlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// SharedPreferences constants
private const val PREFS_NAME = "RadioGvG_Prefs"
private const val KEY_BATTERY_OPT_DIALOG_SHOWN = "battery_opt_dialog_shown"
private const val KEY_BATTERY_OPT_ENABLED = "battery_opt_enabled"

// Parse history string from API (format: "Artist - Title" or just "Title")
// Also handles numbered format like "1.) Artist - Title"
private fun parseHistoryItem(historyItem: String): Pair<String, String> {
    // Remove leading numbering like "1.) "
    val cleanedItem = historyItem.replace(Regex("^\\d+\\.\\)\\s*"), "")
    val parts = cleanedItem.split(" - ", limit = 2)
    return if (parts.size == 2) {
        parts[1].trim() to parts[0].trim() // title to artist
    } else {
        cleanedItem.trim() to ""
    }
}

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
    var isServicePlayingRadio by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var nowPlaying by remember { mutableStateOf<CentovaNowPlaying?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Battery optimization dialog state
    var showBatteryOptDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

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
                        isServicePlayingRadio = mode == MediaPlaybackService.PlaybackMode.RADIO
                    }

                    override fun onProgressUpdate(position: Int, duration: Int) {
                        // Radio doesn't need progress updates
                    }

                    override fun onMetadataUpdate(title: String, artist: String, coverUrl: String?) {
                        // Radio updates handled separately via API
                    }
                })

                // Sync initial state
                isPlaying = mediaService?.isPlaying() == true
                isServicePlayingRadio = mediaService?.getPlaybackMode() == MediaPlaybackService.PlaybackMode.RADIO
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

    // Update now playing info periodically
    LaunchedEffect(Unit) {
        // Immediate first fetch
        try {
            val result = apiClient.getNowPlaying()
            result.onSuccess { source ->
                nowPlaying = source
                if (isServiceBound) {
                    mediaService?.updateRadioMetadata(
                        title = source?.title ?: "",
                        artist = source?.artist ?: "",
                        artUrl = source?.art
                    )
                }
            }
        } catch (_: Exception) {
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
                        mediaService?.updateRadioMetadata(
                            title = source?.title ?: "",
                            artist = source?.artist ?: "",
                            artUrl = source?.art
                        )
                    }
                    errorMessage = null
                }.onFailure {
                    // Silently fail - keep showing last info
                }
            } catch (_: Exception) {
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

    // Function to check if battery optimization is enabled
    fun isBatteryOptimizationEnabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    // Function to open battery optimization settings
    // Note: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is acceptable for radio streaming apps
    // that require uninterrupted background playback. This is a legitimate use case per
    // Android policy for media playback apps.
    fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to app settings if the specific intent fails
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun togglePlayback() {
        if (isServiceBound) {
            // Check if we should show battery optimization dialog
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val dialogShown = prefs.getBoolean(KEY_BATTERY_OPT_DIALOG_SHOWN, false)
            val userEnabledOpt = prefs.getBoolean(KEY_BATTERY_OPT_ENABLED, true)

            if (!isPlaying && !dialogShown && isBatteryOptimizationEnabled() && userEnabledOpt) {
                // Show the dialog before starting playback
                showBatteryOptDialog = true
                return
            }

            isLoading = true
            if (isServicePlayingRadio && isPlaying) {
                // Pause radio
                mediaService?.pause()
                isPlaying = false
            } else if (isServicePlayingRadio && !isPlaying) {
                // Resume existing radio playback
                mediaService?.resume()
                isPlaying = true
            } else {
                // Start radio - this will stop any podcast via the service
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    action = MediaPlaybackService.ACTION_PLAY_RADIO
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isPlaying = true
                isServicePlayingRadio = true
            }
            isLoading = false
        }
    }

    fun updateVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        mediaService?.setVolume(volume)
    }

    // Extract artist and title from Centova response - use empty defaults
    val title = nowPlaying?.title ?: ""
    val artist = nowPlaying?.artist ?: ""
    val albumArtUrl = nowPlaying?.art

    // Battery Optimization Dialog
    if (showBatteryOptDialog) {
        BatteryOptimizationDialog(
            onDismiss = { userChoice ->
                showBatteryOptDialog = false
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean(KEY_BATTERY_OPT_DIALOG_SHOWN, true)
                    putBoolean(KEY_BATTERY_OPT_ENABLED, userChoice)
                    apply()
                }
                if (userChoice) {
                    openBatteryOptimizationSettings()
                }
                // Continue with playback
                togglePlayback()
            }
        )
    }

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
            onVolumeChange = { updateVolume(it) },
            isServicePlayingRadio = isServicePlayingRadio
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
            onVolumeChange = { updateVolume(it) },
            isServicePlayingRadio = isServicePlayingRadio
        )
    }
}

@Composable
private fun BatteryOptimizationDialog(
    onDismiss: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        icon = {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Battery Optimization",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "To ensure uninterrupted radio playback, we recommend disabling battery optimization for this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Without this setting, the system may stop the radio after a while to save battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "You can change this anytime in your device settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDismiss(true) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary
                )
            ) {
                Text("Enable Unrestricted")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss(false) }
            ) {
                Text(
                    "Skip",
                    color = colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
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
    onVolumeChange: (Float) -> Unit,
    isServicePlayingRadio: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        // Album Art
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtWithLogo(
                isPlaying = isPlaying,
                artworkUrl = albumArtUrl,
                fallbackLogo = painterResource(id = R.drawable.radio_logo),
                maxHeightFraction = 0.95f
            )
        }

        // Now Playing - Track Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title.ifBlank { "Radio GvG" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                nowPlaying?.let { np ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = colorScheme.primary,
                                    strokeWidth = 1.5.dp
                                )
                                Text(
                                    text = "Connecting...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            isPlaying -> {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(Color.Green, CircleShape)
                                )
                                Text(
                                    text = "Live • ${np.listeners} listeners",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                Text(
                                    text = "● Paused",
                                    style = MaterialTheme.typography.labelSmall,
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
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        // Recently Played Section
        val history = nowPlaying?.history
            ?.asReversed()
            ?.drop(1)
            ?.filter { it.isNotBlank() && !it.contains("RadioGoedvoorGoed") }
            ?.take(3)
            ?: emptyList()
        if (history.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Recently Played",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        history.forEachIndexed { index, historyItem ->
                            val (songTitle, songArtist) = parseHistoryItem(historyItem)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onPrimary
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = songTitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        if (songArtist.isNotBlank()) {
                                            Text(
                                                text = songArtist,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Play/Pause Button
        PlayPauseButton(
            isPlaying = isPlaying,
            isServicePlayingRadio = isServicePlayingRadio,
            isLoading = isLoading,
            onTogglePlayback = onTogglePlayback,
            modifier = Modifier.size(72.dp)
        )

        // Volume Control
        VolumeControl(
            volume = volume,
            onVolumeChange = onVolumeChange,
            compact = true
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
    onVolumeChange: (Float) -> Unit,
    isServicePlayingRadio: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
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
            // Track Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
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
                                    color = colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Connecting...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
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
                                    color = colorScheme.onSurfaceVariant
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
                    colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // Play/Pause Button (compact)
            PlayPauseButton(
                isPlaying = isPlaying,
                isServicePlayingRadio = isServicePlayingRadio,
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
    isServicePlayingRadio: Boolean,
    isLoading: Boolean,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
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
            containerColor = colorScheme.primary,
            disabledContainerColor = colorScheme.primaryContainer
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = colorScheme.onPrimary,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = if (isPlaying && isServicePlayingRadio) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (isPlaying && isServicePlayingRadio) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
                tint = colorScheme.onPrimary
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
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (volume > 0) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
            contentDescription = "Volume",
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (compact) 20.dp else 24.dp)
        )

        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = colorScheme.primaryContainer
            )
        )

        Text(
            text = "${(volume * 100).toInt()}%",
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            modifier = Modifier.width(if (compact) 32.dp else 36.dp)
        )
    }
}

@Composable
private fun AlbumArtWithLogo(
    isPlaying: Boolean,
    artworkUrl: String?,
    fallbackLogo: androidx.compose.ui.graphics.painter.Painter,
    maxHeightFraction: Float
) {
    val colorScheme = MaterialTheme.colorScheme
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
                    .background(colorScheme.primaryContainer),
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
                                    .background(colorScheme.onPrimaryContainer, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                } else {
                    // Music note icon
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "radiogoedvoorgoed",
                        modifier = Modifier.size(64.dp),
                        tint = colorScheme.primary
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
