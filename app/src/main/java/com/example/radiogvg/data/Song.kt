package com.example.radiogvg.data

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val ID: Int,
    val artist: String,
    val title: String,
    val album: String? = null,
    val duration: String
)

@Serializable
data class SongResponse(
    val songs: List<Song>
)

@Serializable
data class SongRequest(
    val songID: Int,
    val username: String = "Anonymous",
    val message: String = ""
)

@Serializable
data class RequestResponse(
    val success: Boolean,
    val requestId: Int? = null,
    val message: String
)