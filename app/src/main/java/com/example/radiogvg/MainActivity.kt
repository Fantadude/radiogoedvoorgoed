package com.example.radiogvg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.radiogvg.ui.screens.PlayerScreen
import com.example.radiogvg.ui.screens.PodcastsScreen
import com.example.radiogvg.ui.screens.RequestsScreen
import com.example.radiogvg.ui.theme.RadiogvgTheme
import com.example.radiogvg.data.PodcastEpisode
import com.example.radiogvg.network.RadioApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RadiogvgTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RadioApp()
                }
            }
        }
    }
}

data class NavigationItem(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit
)

@Composable
fun RadioApp() {
    // Hoisted podcast state - survives tab switches
    var podcastEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var isPodcastLoading by remember { mutableStateOf(false) }
    var podcastError by remember { mutableStateOf<String?>(null) }
    val apiClient = remember { RadioApiClient() }

    // Function to load/refetch podcasts
    val loadPodcasts: () -> Unit = {
        isPodcastLoading = true
        podcastError = null
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = apiClient.getPodcasts()
                result.onSuccess { episodeList ->
                    podcastEpisodes = episodeList
                }.onFailure { error ->
                    podcastError = error.message ?: "Failed to load podcasts"
                }
            } catch (e: Exception) {
                podcastError = "Error: ${e.message}"
            }
            isPodcastLoading = false
        }
    }

    // Initial load only if empty
    LaunchedEffect(Unit) {
        if (podcastEpisodes.isEmpty()) {
            loadPodcasts()
        }
    }

    val navigationItems = listOf(
        NavigationItem(
            title = "Radio",
            icon = Icons.Default.PlayArrow,
            screen = { PlayerScreen() }
        ),
        NavigationItem(
            title = "Requests",
            icon = Icons.Default.Menu,
            screen = { RequestsScreen() }
        ),
        NavigationItem(
            title = "Podcasts",
            icon = Icons.Default.Home,
            screen = { PodcastsScreen(
                episodes = podcastEpisodes,
                isLoading = isPodcastLoading,
                errorMessage = podcastError,
                onRefresh = loadPodcasts
            ) }
        )
    )

    var selectedItemIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Navigation Bar - iOS style
        TopNavigationBar(
            items = navigationItems,
            selectedIndex = selectedItemIndex,
            onItemSelected = { selectedItemIndex = it }
        )

        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            navigationItems[selectedItemIndex].screen()
        }
    }
}

@Composable
private fun TopNavigationBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    NavigationButton(
                        title = item.title,
                        icon = item.icon,
                        isSelected = isSelected,
                        onClick = { onItemSelected(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = if (isSelected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) MaterialTheme.typography.labelLarge.fontWeight else androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }
}
