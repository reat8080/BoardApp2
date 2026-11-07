package com.example.boardapp.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun playAudio(uriString: String) {
        if (uriString.isBlank()) return
        player?.release()
        try {
            player = MediaPlayer().apply {
                setDataSource(context, Uri.parse(uriString))
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
