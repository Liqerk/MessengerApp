package com.example.myapplication

import android.util.Log
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.*

object SyncManager {

    private const val TAG = "SyncManager"
    private var isSyncing = false

    suspend fun sendMediaMessage(
        sender: String, receiver: String, text: String,
        type: String, mediaUrl: String, duration: Int
    ): Boolean {
        val result = TransportManager.get().sendMediaMessage(sender, receiver, text, type, mediaUrl, duration)
        return result is TransportResult.Success
    }

    suspend fun syncChats(currentUser: String): Boolean = withContext(Dispatchers.IO) {
        if (isSyncing) return@withContext false
        isSyncing = true

        try {
            Log.d(TAG, "🔄 Syncing chats for $currentUser")

            val result = TransportManager.get().getChatList(currentUser)

            when (result) {
                is TransportResult.Success -> {
                    val serverChats = result.data
                    Log.d(TAG, "📥 Got ${serverChats.size} chats from server")

                    for (chat in serverChats) {
                        Repository.ensureUserExists(chat.name)
                        syncMessages(currentUser, chat.name)
                    }

                    Log.d(TAG, "✅ Sync complete: ${serverChats.size} chats")
                    isSyncing = false
                    true
                }

                is TransportResult.Error -> {
                    Log.e(TAG, "❌ Sync failed: ${result.message}")
                    isSyncing = false
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync error", e)
            isSyncing = false
            false
        }
    }

    // ✅ ИСПРАВЛЕНО: проверка дубликатов по серверному ключу
    private suspend fun syncMessages(currentUser: String, otherUser: String) {
        val result = TransportManager.get().getMessages(currentUser, otherUser)
        when (result) {
            is TransportResult.Success -> {
                val serverMessages = result.data
                Log.d(TAG, "📥 Got ${serverMessages.size} messages for chat with $otherUser")

                var savedCount = 0
                var skippedCount = 0

                for (msg in serverMessages) {
                    // ✅ Проверяем ПО СЕРВЕРНОМУ timestamp
                    val exists = Repository.messageExists(
                        msg.sender, msg.receiver, msg.text, msg.timestamp
                    )

                    if (exists) {
                        skippedCount++
                        continue
                    }

                    Repository.ensureUserExists(msg.sender)
                    Repository.ensureUserExists(msg.receiver)

                    // ✅ Сохраняем С СЕРВЕРНЫМ timestamp (а не генерируем локально)
                    val msgId = Repository.syncMessage(
                        sender = msg.sender,
                        receiver = msg.receiver,
                        text = msg.text,
                        timestamp = msg.timestamp
                    )

                    if (msgId > 0) {
                        savedCount++
                    }
                }

                Log.d(TAG, "📝 $otherUser: +$savedCount новых, ⏭️ $skippedCount пропущено")
            }
            is TransportResult.Error -> {
                Log.e(TAG, "❌ Failed to sync messages for $otherUser: ${result.message}")
            }
        }
    }

    suspend fun searchUsers(query: String, currentUser: String): List<User> =
        withContext(Dispatchers.IO) {
            val localResults = Repository.searchUsers(query, currentUser)

            if (TransportManager.getMode() == TransportMode.SERVER) {
                val result = TransportManager.get().searchUsers(query, currentUser)
                when (result) {
                    is TransportResult.Success -> {
                        result.data.forEach { user ->
                            Repository.ensureUserExists(user.login)
                        }
                        return@withContext (localResults + result.data).distinctBy { it.login }
                    }
                    else -> {}
                }
            }

            localResults
        }

    suspend fun sendMessage(sender: String, receiver: String, text: String): Boolean {
        val result = TransportManager.get().sendMessage(sender, receiver, text)
        return result is TransportResult.Success
    }
}