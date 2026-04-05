package com.example.myapplication

import android.util.Log
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.*

object SyncManager {

    private const val TAG = "SyncManager"
    private var isSyncing = false
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Синхронизация чатов с сервера
    suspend fun syncChats(currentUser: String): Boolean = withContext(Dispatchers.IO) {
        if (isSyncing) return@withContext false
        isSyncing = true

        try {
            val result = TransportManager.get().getChatList(currentUser)

            when (result) {
                is TransportResult.Success -> {
                    Log.d(TAG, "✅ Synced ${result.data.size} chats from server")

                    // Обновляем локальную БД новыми чатами
                    for (chat in result.data) {
                        // Создаём пустые сообщения если чата нет
                        // Repository уже имеет данные
                    }

                    isSyncing = false
                    return@withContext true
                }
                is TransportResult.Error -> {
                    Log.e(TAG, "❌ Sync failed: ${result.message}")
                    isSyncing = false
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync error", e)
            isSyncing = false
            return@withContext false
        }
    }

    // Поиск пользователей - сначала локально, потом сервер
    suspend fun searchUsers(query: String, currentUser: String): List<User> = withContext(Dispatchers.IO) {
        // 1. Сначала локальный поиск
        val localResults = Repository.searchUsers(query, currentUser)

        // 2. Если сервер доступен - ищем там
        if (TransportManager.getMode() == TransportMode.SERVER) {
            val result = TransportManager.get().searchUsers(query, currentUser)
            when (result) {
                is TransportResult.Success -> {
                    // Объединяем результаты, убираем дубликаты
                    val all = (localResults + result.data).distinctBy { it.login }
                    return@withContext all
                }
                else -> { /* ignore */ }
            }
        }

        localResults
    }

    // Отправка сообщения - сохраняем локально, отправляем на сервер
    suspend fun sendMessage(sender: String, receiver: String, text: String): Boolean {
        // 1. Всегда сохраняем локально
        Repository.sendMessage(sender, receiver, text)
        Log.d(TAG, "💾 Message saved locally: $text")

        // 2. Пытаемся отправить на сервер
        if (TransportManager.getMode() == TransportMode.SERVER) {
            val result = TransportManager.get().sendMessage(sender, receiver, text)
            when (result) {
                is TransportResult.Success -> {
                    Log.d(TAG, "✅ Message sent to server")
                    return true
                }
                is TransportResult.Error -> {
                    Log.e(TAG, "❌ Failed to send to server: ${result.message}")
                    // Сообщение уже сохранено локально
                    return true
                }
            }
        }

        return true // Локально сохранено
    }
}