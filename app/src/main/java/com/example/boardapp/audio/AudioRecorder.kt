package com.example.boardapp.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    fun startRecording(): Boolean {
        return try {
            val audioDir = File(context.filesDir, "audios")
            if (!audioDir.exists()) audioDir.mkdirs()
            currentOutputFile = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentOutputFile!!.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun stopRecording(): String? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            currentOutputFile?.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
