package com.example.radiogvg.data

import kotlinx.serialization.Serializable

@Serializable
data class CentovaNowPlaying(
    val title: String? = null,
    val art: String? = null,
    val artist: String? = null,
    val ulistener: Int = 0,
    val listeners: Int = 0,
    val bitrate: String? = null,
    val djusername: String? = null,
    val djprofile: String? = null,
    val history: List<String>? = null,
    val comingsoon: List<String>? = null
)