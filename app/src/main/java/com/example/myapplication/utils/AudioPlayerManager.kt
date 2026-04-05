package com.example.myapplication

import android.media.MediaPlayer
import kotlinx.coroutines.*
import java.io.File

object AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    var currentPlayingId: Int = -1
        private set

    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Коллбеки для обновления UI адаптера
    var onProgressUpdate: ((messageId: Int, progressMs: Int) -> Unit)? = null
    var onStateChanged: ((messageId: Int, isPlaying: Boolean) -> Unit)? = null

    fun play(messageId: Int, audioPath: String) {
        // Если нажали на тот же трек, что сейчас играет - ставим на паузу
        if (currentPlayingId == messageId && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }

        // Останавливаем предыдущий трек
        stop()

        val file = File(audioPath)
        if (!file.exists() && !audioPath.startsWith("http")) return

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                start()
                setOnCompletionListener {
                    this@AudioPlayerManager.stop()
                }
            }
            currentPlayingId = messageId
            onStateChanged?.invoke(messageId, true)
            startProgressUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        updateJob?.cancel()
        onStateChanged?.invoke(currentPlayingId, false)
    }

    fun stop() {
        val lastId = currentPlayingId
        updateJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        currentPlayingId = -1
        if (lastId != -1) {
            onStateChanged?.invoke(lastId, false)
            onProgressUpdate?.invoke(lastId, 0) // Сбрасываем ползунок
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    private fun startProgressUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                onProgressUpdate?.invoke(currentPlayingId, mediaPlayer!!.currentPosition)
                delay(100) // Обновляем каждые 100мс
            }
        }
    }
}