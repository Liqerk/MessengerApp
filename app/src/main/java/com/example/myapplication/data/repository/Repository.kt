package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.database.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import kotlin.math.abs

object Repository {

    private lateinit var db: DbHelper

    // Инициализация DAO для Room (если используется параллельно)
    private lateinit var messageDao: Any

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (!::db.isInitialized) {
            db = DbHelper(appContext, null)
        }
        // Если используете Room, раскомментируйте это:
        /*
        try {
            val roomDb = AppDatabase.getDatabase(appContext)
            messageDao = roomDb.messageDao()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        */
    }

    // ==================== АВТОРИЗАЦИЯ ====================

    fun register(login: String, mail: String, password: String): Boolean =
        try {
            db.addUser(User(login = login, mail = mail, password = password))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    fun login(login: String, password: String): Boolean = db.getUser(login, password)

    // ==================== ПОЛЬЗОВАТЕЛИ ====================

    fun searchUsers(query: String, currentUser: String): List<User> =
        db.searchUsers(query, currentUser)

    fun getAllUsers(currentUser: String): List<User> =
        db.getAllUsersExcept(currentUser)

    fun getUserMail(login: String): String =
        db.getUserMail(login)

    fun changeLogin(oldLogin: String, newLogin: String): Boolean =
        db.changeLogin(oldLogin, newLogin)

    fun changePassword(login: String, newPassword: String) =
        db.changePassword(login, newPassword)

    fun setAvatar(login: String, path: String) {
        // 1. Сначала принудительно создаем пользователя в локальной БД, если его там нет
        db.addUserIfNotExists(login)
        // 2. Теперь сохраняем путь к картинке
        db.setAvatar(login, path)
    }
    fun getAvatar(login: String): String? =
        db.getAvatar(login)

    // ==================== СООБЩЕНИЯ (Отправка) ====================
    // Важно: параметр isSent должен быть последним и иметь имя для корректной передачи

    fun sendMessage(sender: String, receiver: String, text: String, isSent: Boolean = true): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addMessage(sender, receiver, text, isSent)
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }

    fun sendImageMessage(sender: String, receiver: String, imagePath: String, isSent: Boolean = true): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addImageMessage(sender, receiver, imagePath, isSent)
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }

    fun sendAudioMessage(sender: String, receiver: String, audioPath: String, duration: Int, isSent: Boolean = true): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addAudioMessage(sender, receiver, audioPath, duration, isSent)
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    // ✅ ДОБАВЛЕНО: Принудительно создает пользователя локально, если его нет
    fun ensureUserExists(login: String) {
        db.addUserIfNotExists(login)
    }
    fun sendReplyMessage(sender: String, receiver: String, text: String, replyTo: Message, isSent: Boolean = true): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addReplyMessage(
                sender = sender,
                receiver = receiver,
                text = text,
                replyToId = replyTo.id,
                replyToText = replyTo.text ?: "",
                isSent = isSent
            )
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }

    // ==================== ЧАТЫ (Чтение) ====================

    fun getMessages(user1: String, user2: String): List<Message> =
        db.getMessages(user1, user2)
    // ✅ НОВЫЙ МЕТОД: для SyncManager (с серверным timestamp)
    fun syncMessage(sender: String, receiver: String, text: String, timestamp: String): Int =
        try {
            db.addUserIfNotExists(sender)
            db.addUserIfNotExists(receiver)
            db.addMessageWithTimestamp(sender, receiver, text, timestamp, isSent = true)
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }

    // ✅ Метод для проверки существует ли сообщение
    fun messageExists(sender: String, receiver: String, text: String, timestamp: String): Boolean {
        val normalized = try {
            timestamp.replace("T", " ").replace(Regex("\\..*Z?$"), "").trim()
        } catch (e: Exception) {
            timestamp
        }
        return db.messageExists(sender, receiver, text, normalized)
    }
    fun getChatList(currentUser: String): List<ChatItem> {
        val users = db.getUsersWithMessages(currentUser)
        return users.map { user ->
            ChatItem(
                id = user.id,
                name = user.login,
                image = getAvatar(user.login) ?: "",
                lastMessage = db.getLastMessage(currentUser, user.login),
                lastMessageTime = db.getLastMessageTime(currentUser, user.login),
                unreadCount = db.getUnreadCount(currentUser, user.login),
                isPinned = db.isPinned(currentUser, user.login)
            )
        }.sortedByDescending { it.isPinned }
    }

    // ==================== УПРАВЛЕНИЕ СОСТОЯНИЕМ ====================

    // Обновление статуса "отправлено" (успешная отправка на сервер)
    fun markMessageAsSent(messageId: Int) =
        db.markMessageAsSent(messageId)

    // Получение сообщений, которые еще НЕ отправлены (isSent = 0)
    fun getUnsentMessages(currentUser: String): List<Message> =
        db.getUnsentMessages(currentUser)

    // Сохранение входящего сообщения (всегда помечаем как "отправленное")
    fun saveIncomingMessage(msg: Message) {
        try {
            android.util.Log.d("Repository", "💾 Saving: ${msg.sender}→${msg.receiver}: ${msg.text.take(20)} | TS=${msg.timestamp}")

            // ✅ Проверяем ТОЛЬКО по sender+receiver+text+timestamp
            // НЕ проверяем по ID (серверный ID ≠ локальный ID)
            if (msg.timestamp.isNotBlank()) {
                val exists = messageExists(
                    sender = msg.sender,
                    receiver = msg.receiver,
                    text = msg.text,
                    timestamp = msg.timestamp
                )
                if (exists) {
                    android.util.Log.d("Repository", "⏭️ Skip: duplicate (text+ts match)")
                    return
                }
            }

            db.addUserIfNotExists(msg.sender)
            db.addUserIfNotExists(msg.receiver)

            val msgId = db.addMessageWithTimestamp(
                sender = msg.sender,
                receiver = msg.receiver,
                text = msg.text,
                timestamp = msg.timestamp,
                isSent = true
            )

            if (msgId > 0) {
                android.util.Log.d("Repository", "✅ Saved with local ID=$msgId")
            } else {
                android.util.Log.d("Repository", "⏭️ UNIQUE constraint blocked (ON CONFLICT IGNORE)")
            }

        } catch (e: Exception) {
            android.util.Log.e("Repository", "❌ Failed to save", e)
        }
    }

    // Поиск глобальный
    fun searchGlobalMessages(currentUser: String, query: String): List<Message> =
        db.searchGlobalMessages(currentUser, query)

    // Отметка прочтения
    fun markAsRead(currentUser: String, sender: String) =
        db.markMessagesAsRead(currentUser, sender)

    // Редактирование
    fun editMessage(messageId: Int, newText: String): Boolean =
        try {
            db.editMessage(messageId, newText)
            true
        } catch (e: Exception) {
            false
        }

    // Удаление
    fun deleteMessage(messageId: Int): Boolean =
        try {
            db.deleteMessage(messageId)
            true
        } catch (e: Exception) {
            false
        }

    // Избранное
    fun toggleFavorite(messageId: Int): Boolean =
        db.toggleFavorite(messageId)

    fun getFavoriteMessages(login: String): List<Message> =
        db.getFavoriteMessages(login)

    // Поиск по чату
    fun searchMessages(currentUser: String, chatWith: String, query: String): List<Message> =
        db.searchMessages(currentUser, chatWith, query)

    // Управление закреплением
    fun isChatPinned(userLogin: String, chatWith: String): Boolean =
        db.isPinned(userLogin, chatWith)

    fun togglePinChat(userLogin: String, chatWith: String) =
        db.togglePin(userLogin, chatWith)

    fun deleteChat(currentUser: String, chatWith: String) =
        db.deleteChat(currentUser, chatWith)

    // Room Methods (если используются)
    fun getMessagesFlow(u1: String, u2: String): Flow<List<MessageEntity>> = emptyFlow()
}