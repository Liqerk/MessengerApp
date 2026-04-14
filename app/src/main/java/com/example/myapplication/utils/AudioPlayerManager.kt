package com.example.myapplication

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.*
import java.io.File

object AudioPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false

    var currentPlayingId: Int = -1
        private set

    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val progressMap = mutableMapOf<Int, Int>()
    private val durationMap = mutableMapOf<Int, Int>()

    // Только для обновления конкретного активного элемента (без notifyDataSetChanged)
    var onActiveProgressUpdate: ((progressMs: Int) -> Unit)? = null
    var onPlayStateChanged: ((messageId: Int, isPlaying: Boolean) -> Unit)? = null
    var onPlaybackComplete: ((messageId: Int) -> Unit)? = null

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    val currentPosition: Int
        get() = try {
            if (prepared && mediaPlayer != null) mediaPlayer?.currentPosition ?: 0 else 0
        } catch (_: Exception) {
            0
        }

    fun getSavedProgress(messageId: Int): Int = progressMap[messageId] ?: 0

    fun getSavedDuration(messageId: Int): Int = durationMap[messageId] ?: 0

    fun saveProgressForMessage(messageId: Int, progressMs: Int) {
        progressMap[messageId] = progressMs
    }

    fun play(messageId: Int, audioPath: String) {
        if (currentPlayingId == messageId && isPlaying) {
            pause()
            return
        }

        if (currentPlayingId == messageId && mediaPlayer != null && prepared) {
            resume()
            return
        }

        saveCurrentProgress()
        releaseInternal(notifyState = true)

        val file = File(audioPath)
        if (!audioPath.startsWith("http") && !file.exists()) {
            return
        }

        currentPlayingId = messageId
        val targetPosition = getSavedProgress(messageId)

        try {
            val mp = MediaPlayer()
            mediaPlayer = mp

            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            mp.setDataSource(audioPath)

            mp.setOnPreparedListener { player ->
                prepared = true
                durationMap[messageId] = try { player.duration } catch (_: Exception) { 0 }

                if (targetPosition > 0) {
                    try { player.seekTo(targetPosition) } catch (_: Exception) {}
                }

                player.start()
                onPlayStateChanged?.invoke(messageId, true)
                startProgressUpdates()
            }

            mp.setOnCompletionListener {
                val finishedId = currentPlayingId
                val finalDuration = try {
                    mediaPlayer?.duration ?: durationMap[finishedId] ?: 0
                } catch (_: Exception) { 0 }

                if (finishedId != -1) {
                    progressMap[finishedId] = finalDuration
                    onPlayStateChanged?.invoke(finishedId, false)
                    onPlaybackComplete?.invoke(finishedId)
                }

                releaseInternal(notifyState = false)
            }

            mp.setOnErrorListener { _, _, _ ->
                val failedId = currentPlayingId
                if (failedId != -1) {
                    onPlayStateChanged?.invoke(failedId, false)
                }
                releaseInternal(notifyState = false)
                true
            }

            mp.prepareAsync()

        } catch (e: Exception) {
            e.printStackTrace()
            releaseInternal(notifyState = true)
        }
    }

    fun pause() {
        try {
            val mp = mediaPlayer ?: return
            if (prepared && mp.isPlaying) {
                mp.pause()
                saveCurrentProgress()
                updateJob?.cancel()
                onPlayStateChanged?.invoke(currentPlayingId, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resume() {
        try {
            val mp = mediaPlayer ?: return
            if (prepared && !mp.isPlaying) {
                mp.start()
                onPlayStateChanged?.invoke(currentPlayingId, true)
                startProgressUpdates()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        saveCurrentProgress()
        releaseInternal(notifyState = true)
    }

    fun seekTo(positionMs: Int) {
        try {
            progressMap[currentPlayingId] = positionMs
            if (prepared && mediaPlayer != null) {
                mediaPlayer?.seekTo(positionMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isPlayingMessage(messageId: Int): Boolean {
        return currentPlayingId == messageId && isPlaying
    }

    private fun saveCurrentProgress() {
        val id = currentPlayingId
        if (id == -1) return

        val pos = try {
            if (prepared && mediaPlayer != null) {
                mediaPlayer?.currentPosition ?: 0
            } else {
                progressMap[id] ?: 0
            }
        } catch (_: Exception) {
            progressMap[id] ?: 0
        }

        progressMap[id] = pos
    }

    private fun startProgressUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer ?: break
                if (!prepared || !mp.isPlaying || currentPlayingId == -1) break

                val pos = try { mp.currentPosition } catch (_: Exception) { break }

                progressMap[currentPlayingId] = pos
                onActiveProgressUpdate?.invoke(pos)

                delay(200)
            }
        }
    }

    private fun releaseInternal(notifyState: Boolean) {
        val lastId = currentPlayingId

        updateJob?.cancel()
        updateJob = null

        try {
            mediaPlayer?.setOnPreparedListener(null)
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.setOnErrorListener(null)
        } catch (_: Exception) {}

        try { mediaPlayer?.reset() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}

        mediaPlayer = null
        prepared = false
        currentPlayingId = -1

        if (notifyState && lastId != -1) {
            onPlayStateChanged?.invoke(lastId, false)
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}