package com.umarabbas.musicplayer

data class Song(
    val title: String,
    val trackNumber: Int,
    val year: Int,
    val duration: Int,
    val path: String?,
    val albumName: String,
    val artistId: Int,
    val artistName: String)