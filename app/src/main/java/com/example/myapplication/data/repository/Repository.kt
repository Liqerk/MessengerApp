package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.database.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object Repository {

    private lateinit var db: DbHelper

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (!::db.isInitialized) {
            db = DbHelper(appContext, null)
        }
    }

    // ==================== АВТОРИЗАЦИЯ ====================

    fun register(login: String, mail: String, password: String): Boolean =
        try { db.addUser(User(login = login, mail = mail, password = password)); true }
        catch (e: Exception) { e.printStackTrace(); false }

    fun login(login: String, password: String): Boolean = db.getUser(login, password)

    // ==================== ПОЛЬЗОВАТЕЛИ ====================

    fun searchUsers(query: String, currentUser: String): List<User> = db.searchUsers(query, currentUser)
    fun getAllUsers(currentUser: String): List<User> = db.getAllUsersExcept(currentUser)
    fun getUserMail(login: String): String = db.getUserMail(login)
    fun changeLogin(oldLogin: String, newLogin: String): Boolean = db.changeLogin(oldLogin, newLogin)
    fun changePassword(login: String, newPassword: String) = db.changePassword(login, newPassword)

    fun setAvatar(login: String, path: String) {
        db.addUserIfNotExists(login)
        db.setAvatar(login, path)
    }

    fun getAvatar(login: String): String? = db.getAvatar(login)

    // ==================== СООБЩЕНИЯ ====================

    fun sendMessage(sender: String, receiver: String, text: String, isSent: Boolean = true, clientMessageId: String = ""): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addMessage(sender, receiver, text, isSent, clientMessageId)
        } catch (e: Exception) { e.printStackTrace(); -1 }

    fun sendImageMessage(sender: String, receiver: String, imagePath: String, isSent: Boolean = true, clientMessageId: String = ""): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addImageMessage(sender, receiver, imagePath, isSent, clientMessageId)
        } catch (e: Exception) { e.printStackTrace(); -1 }

    fun sendAudioMessage(sender: String, receiver: String, audioPath: String, duration: Int, isSent: Boolean = true, clientMessageId: String = ""): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addAudioMessage(sender, receiver, audioPath, duration, isSent, clientMessageId)
        } catch (e: Exception) { e.printStackTrace(); -1 }

    fun ensureUserExists(login: String) { db.addUserIfNotExists(login) }

    fun sendReplyMessage(sender: String, receiver: String, text: String, replyTo: Message, isSent: Boolean = true, clientMessageId: String = ""): Int =
        try {
            db.addUserIfNotExists(receiver)
            db.addReplyMessage(sender, receiver, text, replyTo.id, replyTo.text ?: "", isSent, clientMessageId)
        } catch (e: Exception) { e.printStackTrace(); -1 }

    // ==================== ЧТЕНИЕ ====================

    fun getMessages(user1: String, user2: String): List<Message> = db.getMessages(user1, user2)

    fun syncMessage(sender: String, receiver: String, text: String, timestamp: String, clientMessageId: String = ""): Int =
        try {
            db.addUserIfNotExists(sender)
            db.addUserIfNotExists(receiver)
            db.addMessageWithTimestamp(sender, receiver, text, timestamp, true, clientMessageId)
        } catch (e: Exception) { e.printStackTrace(); -1 }

    fun messageExists(sender: String, receiver: String, text: String, timestamp: String): Boolean {
        val normalized = try {
            timestamp.replace("T", " ").replace(Regex("\\..*Z?$"), "").trim()
        } catch (e: Exception) { timestamp }
        return db.messageExists(sender, receiver, text, normalized)
    }

    // ✅ НОВЫЙ: проверка по clientMessageId
    fun messageExistsByClientId(clientMessageId: String): Boolean =
        db.messageExistsByClientId(clientMessageId)

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

    // ==================== УПРАВЛЕНИЕ ====================
    fun deleteMessageByClientId(clientMessageId: String): Boolean =
        db.deleteMessageByClientMessageId(clientMessageId) > 0
    fun markMessageAsSent(messageId: Int) = db.markMessageAsSent(messageId)
    fun getUnsentMessages(currentUser: String): List<Message> = db.getUnsentMessages(currentUser)

    // ✅ ИСПРАВЛЕННЫЙ: входящие сообщения
    fun saveIncomingMessage(msg: Message) {
        try {
            android.util.Log.d("Repository", "💾 Incoming: ${msg.sender}→${msg.receiver}: ${msg.text.take(20)} | ID=${msg.id} | CID=${msg.clientMessageId}")

            // ✅ ГЛАВНАЯ ПРОВЕРКА: по clientMessageId (если есть)
            if (msg.clientMessageId.isNotBlank()) {
                if (db.messageExistsByClientId(msg.clientMessageId)) {
                    android.util.Log.d("Repository", "⏭️ Skip: clientMessageId ${msg.clientMessageId} exists")
                    return
                }
            }

            // ✅ Проверка по timestamp (для старых сообщений без clientMessageId)
            if (msg.timestamp.isNotBlank()) {
                val exists = messageExists(msg.sender, msg.receiver, msg.text, msg.timestamp)
                if (exists) {
                    android.util.Log.d("Repository", "⏭️ Skip: text+ts match")
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
                isSent = true,
                clientMessageId = msg.clientMessageId
            )

            if (msgId > 0) {
                android.util.Log.d("Repository", "✅ Saved with local ID=$msgId | CID=${msg.clientMessageId}")
            } else {
                android.util.Log.d("Repository", "⏭️ UNIQUE constraint blocked")
            }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "❌ Failed to save", e)
        }
    }

    // ✅ НОВЫЙ: подтверждение своего сообщения сервером
    fun confirmOwnMessage(clientMessageId: String, serverTimestamp: String, serverId: Int): Boolean {
        // ✅ Вариант 1: по clientMessageId
        if (clientMessageId.isNotBlank()) {
            val localMsg = db.getMessageByClientMessageId(clientMessageId)
            if (localMsg != null) {
                val updated = db.confirmOwnMessage(localMsg.id, serverTimestamp, serverId)
                android.util.Log.d("Repository", "✅ Confirmed by clientId: CID=$clientMessageId → localId=${localMsg.id}")
                return updated
            }
        }

        // ✅ Вариант 2 (fallback): по sender+receiver+text (последнее неподтверждённое)
        // Это сработает даже если сервер не вернул clientMessageId
        return false // пока оставляем false, обработаем в ServerTransport
    }
    // ==================== ОСТАЛЬНОЕ ====================

    fun searchGlobalMessages(currentUser: String, query: String): List<Message> = db.searchGlobalMessages(currentUser, query)
    fun markAsRead(currentUser: String, sender: String) = db.markMessagesAsRead(currentUser, sender)
    fun editMessage(messageId: Int, newText: String): Boolean = try { db.editMessage(messageId, newText); true } catch (e: Exception) { false }
    fun deleteMessage(messageId: Int): Boolean = try { db.deleteMessage(messageId); true } catch (e: Exception) { false }
    fun toggleFavorite(messageId: Int): Boolean = db.toggleFavorite(messageId)
    fun getFavoriteMessages(login: String): List<Message> = db.getFavoriteMessages(login)
    fun searchMessages(currentUser: String, chatWith: String, query: String): List<Message> = db.searchMessages(currentUser, chatWith, query)
    fun isChatPinned(userLogin: String, chatWith: String): Boolean = db.isPinned(userLogin, chatWith)
    fun togglePinChat(userLogin: String, chatWith: String) = db.togglePin(userLogin, chatWith)
    fun deleteChat(currentUser: String, chatWith: String) = db.deleteChat(currentUser, chatWith)
    fun getMessagesFlow(u1: String, u2: String): Flow<List<MessageEntity>> = emptyFlow()
}