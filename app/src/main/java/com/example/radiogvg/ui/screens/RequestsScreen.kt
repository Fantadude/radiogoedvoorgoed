package com.example.radiogvg.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.radiogvg.data.Song
import com.example.radiogvg.data.SongRequest
import com.example.radiogvg.network.RadioApiClient
import kotlinx.coroutines.launch

private val ALPHABET = listOf("#") + ('A'..'Z').map { it.toString() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen() {
    val apiClient = remember { RadioApiClient() }
    val scope = rememberCoroutineScope()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var selectedLetter by remember { mutableStateOf("A") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }

    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var username by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Load songs when letter changes
    LaunchedEffect(selectedLetter) {
        if (!isSearchMode) {
            isLoading = true
            errorMessage = null
            try {
                val result = apiClient.getSongsByLetter(selectedLetter)
                result.onSuccess { songList ->
                    songs = songList
                }.onFailure { error ->
                    errorMessage = "Failed to load songs"
                }
            } catch (e: Exception) {
                errorMessage = "Network error"
            }
            isLoading = false
        }
    }

    // Search function
    fun performSearch(query: String) {
        if (query.length >= 2) {
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    val result = apiClient.searchSongs(query)
                    result.onSuccess { songList ->
                        songs = songList
                    }.onFailure { error ->
                        errorMessage = "Search failed"
                    }
                } catch (e: Exception) {
                    errorMessage = "Network error"
                }
                isLoading = false
            }
        }
    }

    // Submit request
    fun submitRequest() {
        selectedSong?.let { song ->
            scope.launch {
                isSubmitting = true
                errorMessage = null
                successMessage = null
                try {
                    val request = SongRequest(
                        songID = song.ID,
                        username = username.takeIf { it.isNotBlank() } ?: "Anonymous",
                        message = message
                    )
                    val result = apiClient.submitRequest(request)
                    result.onSuccess { response ->
                        successMessage = response.message
                        selectedSong = null
                        username = ""
                        message = ""
                    }.onFailure { error ->
                        errorMessage = "Failed to submit"
                    }
                } catch (e: Exception) {
                    errorMessage = "Network error"
                }
                isSubmitting = false
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Compact Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Song Requests",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Browse or search songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
        }

        // Search Bar - Compact
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        isSearchMode = it.isNotEmpty()
                        if (it.length >= 3) performSearch(it)
                        else if (it.isEmpty()) isSearchMode = false
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Compact Alphabet Bar
        if (!isSearchMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ALPHABET.forEach { letter ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedLetter == letter) colorScheme.primary
                                    else colorScheme.primaryContainer
                                )
                                .clickable { selectedLetter = letter },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                fontSize = 12.sp,
                                color = if (selectedLetter == letter) colorScheme.onPrimary else colorScheme.onPrimaryContainer,
                                fontWeight = if (selectedLetter == letter) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Messages
        if (errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.errorContainer
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(12.dp),
                    color = colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (successMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = successMessage!!,
                        color = colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Content Area
        if (selectedSong == null) {
            // Song List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary, modifier = Modifier.size(36.dp))
                }
            } else if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSearchMode)
                            "No songs found"
                        else
                            "No songs for $selectedLetter",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(songs) { song ->
                        CompactSongCard(
                            song = song,
                            onClick = { selectedSong = song }
                        )
                    }
                }
            }
        } else {
            // Request Form
            CompactRequestForm(
                song = selectedSong!!,
                username = username,
                onUsernameChange = { username = it },
                message = message,
                onMessageChange = { message = it },
                onBack = { selectedSong = null },
                onSubmit = { submitRequest() },
                isSubmitting = isSubmitting
            )
        }
    }
}

@Composable
fun CompactSongCard(song: Song, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactRequestForm(
    song: Song,
    username: String,
    onUsernameChange: (String) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    isSubmitting: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // Selected Song Card - Compact
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Change", color = colorScheme.primary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Request Form - Compact
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Request Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Your Name (optional)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = { Text("Message for DJ (optional)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit Request")
                    }
                }
            }
        }
    }
}

fun formatDuration(duration: String): String {
    return try {
        val seconds = duration.toInt()
        val mins = seconds / 60
        val secs = seconds % 60
        "${mins}:${secs.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        duration
    }
}