package com.example.myapplication

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioPlayerManagerTest {

    @Before
    fun setup() {
        AudioPlayerManager.stop() // Очистка после предыдущих тестов
    }

    @Test
    fun `test play and stop`() {
        assertFalse(AudioPlayerManager.isPlaying) // Если добавишь такой геттер

        AudioPlayerManager.play(1, "fake_path.mp3")
        // Тут сложно проверить, что реально играет, так как это MediaPlayer внутри.
        // Но мы можем проверить состояние ID
        assertEquals(1, AudioPlayerManager.currentPlayingId)

        AudioPlayerManager.stop()
        assertEquals(-1, AudioPlayerManager.currentPlayingId)
    }
}