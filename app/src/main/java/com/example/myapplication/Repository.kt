package com.example.myapplication

import android.content.ContentValues
import android.content.Context

object Repository {

    private lateinit var db: DbHelper

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = DbHelper(context.applicationContext, null)
        }
    }

    // ==================== АВТОРИЗАЦИЯ ====================

    fun register(login: String, mail: String, password: String): Boolean {
        return try {
            db.addUser(User(login = login, mail = mail, password = password))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun login(login: String, password: String): Boolean {
        return db.getUser(login, password)
    }

    // ==================== ПОЛЬЗОВАТЕЛИ ====================

    fun searchUsers(query: String, currentUser: String): List<User> {
        return db.searchUsers(query, currentUser)
    }

    fun getAllUsers(currentUser: String): List<User> {
        return db.getAllUsersExcept(currentUser)
    }

    fun getUserMail(login: String): String {
        return db.getUserMail(login)
    }

    fun changeLogin(oldLogin: String, newLogin: String): Boolean {
        return db.changeLogin(oldLogin, newLogin)
    }

    fun changePassword(login: String, newPassword: String) {
        db.changePassword(login, newPassword)
    }

    // ==================== АВАТАРКИ ====================

    fun setAvatar(login: String, path: String) {
        db.setAvatar(login, path)
    }

    fun getAvatar(login: String): String? {
        return db.getAvatar(login)
    }

    // ==================== ЧАТЫ ====================

    fun getChatList(currentUser: String): List<ChatItem> {
        val users = db.getUsersWithMessages(currentUser)
        val items = users.map { user ->
            val lastMsg = db.getLastMessage(currentUser, user.login)
            val lastTime = db.getLastMessageTime(currentUser, user.login)
            val unread = db.getUnreadCount(currentUser, user.login)
            val pinned = db.isPinned(currentUser, user.login)
            ChatItem(
                id = user.id,
                name = user.login,
                image = "",
                lastMessage = lastMsg,
                lastMessageTime = lastTime,
                unreadCount = unread,
                isOnline = user.isOnline,
                isPinned = pinned
            )
        }
        return items.sortedByDescending { it.isPinned }
    }

    fun isChatPinned(userLogin: String, chatWith: String): Boolean {
        return db.isPinned(userLogin, chatWith)
    }

    fun togglePinChat(userLogin: String, chatWith: String) {
        db.togglePin(userLogin, chatWith)
    }

    fun deleteChat(currentUser: String, chatWith: String) {
        db.deleteChat(currentUser, chatWith)
    }

    // ==================== СООБЩЕНИЯ ====================

    fun sendMessage(sender: String, receiver: String, text: String): Boolean {
        return try {
            // Добавляем получателя в таблицу users если его нет
            db.addUserIfNotExists(receiver)

            db.addMessage(sender, receiver, text)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Новый метод


    fun sendImageMessage(sender: String, receiver: String, imagePath: String): Boolean {
        return try {
            db.addImageMessage(sender, receiver, imagePath)
            true
        } catch (e: Exception) {
            false
        }
    }
    fun saveIncomingMessage(msg: Message) {
        try {
            // Проверяем, нет л�� его уже в БД (чтобы избежать дублей)
            val existing = db.searchGlobalMessages(msg.receiver, msg.text).find { it.timestamp == msg.timestamp }
            if (existing == null) {
                db.addUserIfNotExists(msg.sender) // Убеждаемся, что отправитель есть в контактах
                db.addMessage(msg.sender, msg.receiver, msg.text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun sendAudioMessage(sender: String, receiver: String, audioPath: String, duration: Int): Boolean {
        return try {
            db.addAudioMessage(sender, receiver, audioPath, duration)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendReplyMessage(sender: String, receiver: String, text: String, replyTo: Message): Boolean {
        return try {
            db.addReplyMessage(sender, receiver, text, replyTo.id, replyTo.text)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getMessages(user1: String, user2: String): List<Message> {
        return db.getMessages(user1, user2)
    }

    fun markAsRead(currentUser: String, sender: String) {
        db.markMessagesAsRead(currentUser, sender)
    }

    fun deleteMessage(messageId: Int): Boolean {
        return try {
            db.deleteMessage(messageId)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun editMessage(messageId: Int, newText: String): Boolean {
        return try {
            db.editMessage(messageId, newText)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== ПОИСК ====================

    fun searchMessages(currentUser: String, chatWith: String, query: String): List<Message> {
        return db.searchMessages(currentUser, chatWith, query)
    }

    fun searchGlobalMessages(currentUser: String, query: String): List<Message> {
        return db.searchGlobalMessages(currentUser, query)
    }

    // ==================== ИЗБРАННОЕ ====================

    fun toggleFavorite(messageId: Int): Boolean {
        return db.toggleFavorite(messageId)
    }

    fun getFavoriteMessages(login: String): List<Message> {
        return db.getFavoriteMessages(login)
    }
}