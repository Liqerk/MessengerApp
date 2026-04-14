package com.example.myapplication

import com.example.myapplication.transport.MessageTransport
import com.example.myapplication.transport.TransportResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerTest {

    @Test
    fun `test search users via transport`() = runBlocking {
        // Создаем "подделку" транспорта
        val mockTransport = mockk<MessageTransport>()

        // Описываем поведение: при поиске возвращаем список из 1 юзера
        coEvery { mockTransport.searchUsers("qwe", any()) } returns
                TransportResult.Success(listOf(User(login = "qwe", mail = "", password = "")))

        // Проверяем результат
        val result = mockTransport.searchUsers("qwe", "me")

        assertTrue(result is TransportResult.Success)
        assertEquals("qwe", (result as TransportResult.Success).data[0].login)
    }
}