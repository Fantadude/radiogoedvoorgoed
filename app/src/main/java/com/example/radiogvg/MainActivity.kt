package com.example.radiogvg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.radiogvg.ui.screens.PlayerScreen
import com.example.radiogvg.ui.screens.PodcastsScreen
import com.example.radiogvg.ui.screens.RequestsScreen
import com.example.radiogvg.ui.theme.LightBluePrimary
import com.example.radiogvg.ui.theme.RadiogvgTheme
import com.example.radiogvg.data.PodcastEpisode
import com.example.radiogvg.network.RadioApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disable edge-to-edge for solid system bars
        setContent {
            RadiogvgTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LightBluePrimary
                ) {
                    RadioApp()
                }
            }
        }
    }
}

data class BottomNavigationItem(
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
        BottomNavigationItem(
            title = "Radio",
            icon = Icons.Default.PlayArrow,
            screen = { PlayerScreen() }
        ),
        BottomNavigationItem(
            title = "Requests",
            icon = Icons.Default.Menu,
            screen = { RequestsScreen() }
        ),
        BottomNavigationItem(
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                tonalElevation = 0.dp
            ) {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedItemIndex == index,
                        onClick = { selectedItemIndex = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            navigationItems[selectedItemIndex].screen()
        }
    }
}
