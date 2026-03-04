package com.example.radiogvg.network

import android.util.Log
import com.example.radiogvg.data.CentovaNowPlaying
import com.example.radiogvg.data.PodcastEpisode
import com.example.radiogvg.data.RequestResponse
import com.example.radiogvg.data.Song
import com.example.radiogvg.data.SongRequest
import com.example.radiogvg.data.SongResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

class RadioApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.BODY
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000 // 30 seconds
            connectTimeoutMillis = 15000 // 15 seconds
            socketTimeoutMillis = 30000 // 30 seconds
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
        expectSuccess = false
    }

    companion object {
        const val BASE_URL = "http://86.84.18.58:3000"
        const val STREAM_URL = "https://ex52.voordeligstreamen.nl/8154/stream"
        const val NOW_PLAYING_URL = "https://ex52.voordeligstreamen.nl/cp/get_info.php?p=8154"
        const val PODCAST_FEED_URL = "https://radiogoedvoorgoed.nl/feed/podcast/kringloop-verhalen/"
    }

    // Radio Stream Functions
    fun getStreamUrl(): String = STREAM_URL

    suspend fun getNowPlaying(): Result<CentovaNowPlaying?> {
        return try {
            val response: CentovaNowPlaying = client.get(NOW_PLAYING_URL).body()
            Result.success(response)
        } catch (e: Exception) {
            Log.e("RadioApi", "Error fetching now playing from Centova", e)
            Result.failure(e)
        }
    }

    // Song Requests Functions
    suspend fun getSongsByLetter(letter: String): Result<List<Song>> {
        return try {
            val response: SongResponse = client.get("$BASE_URL/api/songs/letter/$letter").body()
            Result.success(response.songs)
        } catch (e: Exception) {
            Log.e("RadioApi", "Error fetching songs", e)
            Result.failure(e)
        }
    }

    suspend fun searchSongs(query: String): Result<List<Song>> {
        return try {
            val response: SongResponse = client.get("$BASE_URL/api/songs/search") {
                parameter("q", query)
            }.body()
            Result.success(response.songs)
        } catch (e: Exception) {
            Log.e("RadioApi", "Error searching songs", e)
            Result.failure(e)
        }
    }

    suspend fun submitRequest(request: SongRequest): Result<RequestResponse> {
        return try {
            val response: RequestResponse = client.post("$BASE_URL/api/requests") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Log.e("RadioApi", "Error submitting request", e)
            Result.failure(e)
        }
    }

    // Podcast Functions - Using IO dispatcher for network calls
    suspend fun getPodcasts(): Result<List<PodcastEpisode>> {
        var retries = 0
        val maxRetries = 3

        while (retries < maxRetries) {
            try {
                Log.d("RadioApi", "Fetching podcast feed, attempt ${retries + 1}")

                // Run network operation on IO dispatcher
                val xmlString = withContext(Dispatchers.IO) {
                    val url = URL(PODCAST_FEED_URL)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = "GET"
                        connectTimeout = 20000 // 20 seconds
                        readTimeout = 30000 // 30 seconds
                        setRequestProperty("User-Agent", "RadioGVG-Android-App/1.0")
                        setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
                        useCaches = false
                    }

                    val responseCode = connection.responseCode
                    Log.d("RadioApi", "Podcast feed response code: $responseCode")

                    if (responseCode == 200) {
                        val content = connection.inputStream.bufferedReader().use { it.readText() }
                        connection.disconnect()
                        content
                    } else {
                        connection.disconnect()
                        throw Exception("HTTP $responseCode")
                    }
                }

                // Parse on IO dispatcher as well
                val episodes = withContext(Dispatchers.IO) {
                    parsePodcastFeed(xmlString)
                }

                Log.d("RadioApi", "Parsed ${episodes.size} episodes")
                return Result.success(episodes)

            } catch (e: Exception) {
                retries++
                val errorMsg = e.message ?: e.javaClass.simpleName
                Log.e("RadioApi", "Attempt $retries failed: $errorMsg", e)
                if (retries >= maxRetries) {
                    return Result.failure(Exception("Network error: $errorMsg"))
                }
                // Wait before retry
                delay(1000L * retries)
            }
        }

        return Result.failure(Exception("Failed after $maxRetries attempts"))
    }

    private fun parsePodcastFeed(xmlString: String): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentEpisode: PodcastEpisodeBuilder? = null
            var channelImage: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "item" -> currentEpisode = PodcastEpisodeBuilder()
                            "title" -> {
                                if (currentEpisode != null) {
                                    currentEpisode.title = parser.nextText()
                                }
                            }
                            "description" -> {
                                if (currentEpisode != null) {
                                    currentEpisode.description = parser.nextText()
                                }
                            }
                            "enclosure" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (currentEpisode != null && url != null) {
                                    currentEpisode.audioUrl = url
                                }
                            }
                            "pubDate" -> {
                                if (currentEpisode != null) {
                                    currentEpisode.publishDate = parser.nextText()
                                }
                            }
                            "duration", "itunes:duration" -> {
                                if (currentEpisode != null) {
                                    currentEpisode.duration = parser.nextText()
                                }
                            }
                            "itunes:image" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null) {
                                    if (currentEpisode != null) {
                                        currentEpisode.coverImage = href
                                    } else {
                                        channelImage = href
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item" && currentEpisode != null) {
                            // Use channel image as fallback
                            if (currentEpisode.coverImage == null) {
                                currentEpisode.coverImage = channelImage
                            }
                            episodes.add(currentEpisode.build("${episodes.size}"))
                            currentEpisode = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("RadioApi", "Error parsing RSS", e)
        }

        return episodes
    }

    private class PodcastEpisodeBuilder {
        var title: String = ""
        var description: String = ""
        var audioUrl: String = ""
        var duration: String = ""
        var publishDate: String = ""
        var coverImage: String? = null

        fun build(id: String) = PodcastEpisode(
            id = id,
            title = title,
            description = description,
            audioUrl = audioUrl,
            duration = formatDuration(duration),
            publishDate = publishDate,
            coverImage = coverImage
        )

        private fun formatDuration(duration: String): String {
            return try {
                val seconds = duration.toInt()
                val mins = seconds / 60
                val secs = seconds % 60
                "${mins}:${secs.toString().padStart(2, '0')}"
            } catch (e: Exception) {
                duration
            }
        }
    }
}
