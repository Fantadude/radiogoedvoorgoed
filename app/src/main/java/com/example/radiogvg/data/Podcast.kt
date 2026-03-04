package com.example.radiogvg.data

import kotlinx.serialization.Serializable

@Serializable
data class PodcastEpisode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: String,
    val publishDate: String,
    val coverImage: String? = null
)

@Serializable
data class NowPlayingInfo(
    val icestats: IceStats? = null
)

@Serializable
data class IceStats(
    val source: IceSource? = null
)

@Serializable
data class IceSource(
    val title: String? = null,
    val listeners: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    val song: String? = null,
    val artworkUrl: String? = null,
    val coverUrl: String? = null
)