package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTest {
    @Test
    fun `test message time formatting`() {
        val timestamp = "2023-10-27T10:15:30"
        // Допустим, у тебя есть функция сокращения времени
        val formatted = formatTimeForUI(timestamp)
        assertEquals("10:15", formatted)
    }
}