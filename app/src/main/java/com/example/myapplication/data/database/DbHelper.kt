package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(
    context: Context,
    factory: SQLiteDatabase.CursorFactory?
) : SQLiteOpenHelper(context, "messenger_app", factory, 8) {

    override fun onCreate(db: SQLiteDatabase?) {
        db!!.execSQL(
            """CREATE TABLE users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                login TEXT UNIQUE,
                mail TEXT,
                password TEXT,
                is_online INTEGER DEFAULT 0,
                last_seen TEXT DEFAULT '',
                avatar_path TEXT DEFAULT NULL
            )"""
        )

        db.execSQL(
            """CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT,
                receiver TEXT,
                text TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                is_read INTEGER DEFAULT 0,
                type TEXT DEFAULT 'text',
                media_url TEXT DEFAULT NULL,
                duration INTEGER DEFAULT 0,
                reply_to_id INTEGER DEFAULT 0,
                reply_to_text TEXT DEFAULT NULL,
                is_favorite INTEGER DEFAULT 0
            )"""
        )

        db.execSQL(
            """CREATE TABLE pinned_chats(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_login TEXT,
                chat_with TEXT,
                UNIQUE(user_login, chat_with)
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db!!.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS pinned_chats")
        onCreate(db)
    }

    // ==================== ПОЛЬЗОВАТЕЛИ ====================

    fun addUser(user: User) {
        val values = ContentValues()
        values.put("login", user.login)
        values.put("mail", user.mail)
        // Сохраняем ХЕШ, а не чистый пароль
        values.put("password", HashUtils.sha256(user.password))
        val db = this.writableDatabase
        db.insertOrThrow("users", null, values)
        db.close()
    }

    fun getUser(login: String, password: String): Boolean {
        val db = this.readableDatabase
        // Проверяем по ХЕШУ
        val hashedPassword = HashUtils.sha256(password)
        val result = db.rawQuery(
            "SELECT * FROM users WHERE login = ? AND password = ?",
            arrayOf(login, hashedPassword)
        )
        val exists = result.moveToFirst()
        result.close()
        return exists
    }

    fun getAllUsersExcept(currentLogin: String): List<User> {
        val users = mutableListOf<User>()
        val db = this.readableDatabase
        val result = db.rawQuery("SELECT * FROM users WHERE login != ?", arrayOf(currentLogin))
        while (result.moveToNext()) { users.add(cursorToUser(result)) }
        result.close()
        return users
    }

    fun searchUsers(query: String, exceptLogin: String): List<User> {
        val users = mutableListOf<User>()
        val db = this.readableDatabase
        val result = db.rawQuery(
            "SELECT * FROM users WHERE login LIKE ? AND login != ?",
            arrayOf("%$query%", exceptLogin)
        )
        while (result.moveToNext()) { users.add(cursorToUser(result)) }
        result.close()
        return users
    }

    fun getUsersWithMessages(currentUser: String): List<User> {
        val users = mutableListOf<User>()
        val db = this.readableDatabase
        val result = db.rawQuery(
            """SELECT DISTINCT u.* FROM users u
               INNER JOIN messages m 
               ON (m.sender = u.login AND m.receiver = ?)
               OR (m.receiver = u.login AND m.sender = ?)
               WHERE u.login != ?""",
            arrayOf(currentUser, currentUser, currentUser)
        )
        while (result.moveToNext()) { users.add(cursorToUser(result)) }
        result.close()
        return users
    }

    fun getUserMail(login: String): String {
        val db = this.readableDatabase
        val result = db.rawQuery("SELECT mail FROM users WHERE login = ?", arrayOf(login))
        val mail = if (result.moveToFirst()) result.getString(0) else ""
        result.close()
        return mail
    }

    fun changeLogin(oldLogin: String, newLogin: String): Boolean {
        return try {
            val db = this.writableDatabase
            val v1 = ContentValues(); v1.put("login", newLogin)
            db.update("users", v1, "login = ?", arrayOf(oldLogin))
            val v2 = ContentValues(); v2.put("sender", newLogin)
            db.update("messages", v2, "sender = ?", arrayOf(oldLogin))
            val v3 = ContentValues(); v3.put("receiver", newLogin)
            db.update("messages", v3, "receiver = ?", arrayOf(oldLogin))
            db.close()
            true
        } catch (e: Exception) { false }
    }

    fun changePassword(login: String, newPassword: String) {
        val db = this.writableDatabase
        val values = ContentValues(); values.put("password", newPassword)
        db.update("users", values, "login = ?", arrayOf(login))
        db.close()
    }

    // ==================== АВАТАРКИ ====================

    fun setAvatar(login: String, path: String) {
        val db = this.writableDatabase
        val values = ContentValues(); values.put("avatar_path", path)
        db.update("users", values, "login = ?", arrayOf(login))
        db.close()
    }

    fun getAvatar(login: String): String? {
        val db = this.readableDatabase
        val result = db.rawQuery("SELECT avatar_path FROM users WHERE login = ?", arrayOf(login))
        val path = if (result.moveToFirst()) result.getString(0) else null
        result.close()
        return path
    }
    fun addUserIfNotExists(login: String) {
        val db = this.writableDatabase
        val cursor = db.rawQuery("SELECT id FROM users WHERE login = ?", arrayOf(login))
        val exists = cursor.moveToFirst()
        cursor.close()

        if (!exists) {
            val values = ContentValues()
            values.put("login", login)
            values.put("mail", "")
            values.put("password", "")
            try {
                db.insert("users", null, values)
            } catch (e: Exception) {
                // Уже существует
            }
        }
    }

    private fun cursorToUser(cursor: Cursor): User {
        return User(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            login = cursor.getString(cursor.getColumnIndexOrThrow("login")),
            mail = cursor.getString(cursor.getColumnIndexOrThrow("mail")),
            password = "",
            avatarPath = cursor.getString(cursor.getColumnIndexOrThrow("avatar_path"))
        )
    }

    // ==================== СООБЩЕНИЯ ====================

    fun addMessage(sender: String, receiver: String, text: String) {
        val values = ContentValues()
        values.put("sender", sender); values.put("receiver", receiver)
        values.put("text", text); values.put("type", "text")
        val db = this.writableDatabase; db.insert("messages", null, values); db.close()
    }

    fun addImageMessage(sender: String, receiver: String, imagePath: String) {
        val values = ContentValues()
        values.put("sender", sender); values.put("receiver", receiver)
        values.put("text", "📷 Фото"); values.put("type", "image")
        values.put("media_url", imagePath)
        val db = this.writableDatabase; db.insert("messages", null, values); db.close()
    }

    fun addAudioMessage(sender: String, receiver: String, audioPath: String, duration: Int) {
        val values = ContentValues()
        values.put("sender", sender); values.put("receiver", receiver)
        values.put("text", "🎤 Голосовое"); values.put("type", "audio")
        values.put("media_url", audioPath); values.put("duration", duration)
        val db = this.writableDatabase; db.insert("messages", null, values); db.close()
    }

    fun addReplyMessage(sender: String, receiver: String, text: String, replyToId: Int, replyToText: String) {
        val values = ContentValues()
        values.put("sender", sender); values.put("receiver", receiver)
        values.put("text", text); values.put("type", "text")
        values.put("reply_to_id", replyToId); values.put("reply_to_text", replyToText)
        val db = this.writableDatabase; db.insert("messages", null, values); db.close()
    }

    fun getMessages(user1: String, user2: String): List<Message> {
        val messages = mutableListOf<Message>()
        val db = this.readableDatabase
        val result = db.rawQuery(
            """SELECT * FROM messages 
               WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
               ORDER BY id ASC""",
            arrayOf(user1, user2, user2, user1)
        )
        while (result.moveToNext()) { messages.add(cursorToMessage(result)) }
        result.close()
        return messages
    }

    fun getLastMessage(user1: String, user2: String): String {
        val db = this.readableDatabase
        val result = db.rawQuery(
            """SELECT text FROM messages 
               WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
               ORDER BY id DESC LIMIT 1""",
            arrayOf(user1, user2, user2, user1)
        )
        val msg = if (result.moveToFirst()) result.getString(0) else ""
        result.close(); return msg
    }

    fun getLastMessageTime(user1: String, user2: String): String {
        val db = this.readableDatabase
        val result = db.rawQuery(
            """SELECT timestamp FROM messages 
               WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
               ORDER BY id DESC LIMIT 1""",
            arrayOf(user1, user2, user2, user1)
        )
        val time = if (result.moveToFirst()) result.getString(0) else ""
        result.close(); return time
    }

    fun getUnreadCount(currentUser: String, sender: String): Int {
        val db = this.readableDatabase
        val result = db.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE sender = ? AND receiver = ? AND is_read = 0",
            arrayOf(sender, currentUser)
        )
        val count = if (result.moveToFirst()) result.getInt(0) else 0
        result.close(); return count
    }

    fun markMessagesAsRead(currentUser: String, sender: String) {
        val db = this.writableDatabase
        val values = ContentValues(); values.put("is_read", 1)
        db.update("messages", values, "sender = ? AND receiver = ? AND is_read = 0",
            arrayOf(sender, currentUser))
        db.close()
    }

    fun deleteMessage(messageId: Int) {
        val db = this.writableDatabase
        db.delete("messages", "id = ?", arrayOf(messageId.toString()))
        db.close()
    }

    fun editMessage(messageId: Int, newText: String) {
        val db = this.writableDatabase
        val values = ContentValues(); values.put("text", newText)
        db.update("messages", values, "id = ?", arrayOf(messageId.toString()))
        db.close()
    }

    // ==================== ПОИСК ====================

    fun searchMessages(currentUser: String, chatWith: String, query: String): List<Message> {
        val messages = mutableListOf<Message>()
        val db = this.readableDatabase
        val result = db.rawQuery(
            """SELECT * FROM messages 
               WHERE ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?))
               AND text LIKE ? ORDER BY id ASC""",
            arrayOf(currentUser, chatWith, chatWith, currentUser, "%$query%")
        )
        while (result.moveToNext()) { messages.add(cursorToMessage(result)) }
        result.close(); return messages
    }

    fun searchGlobalMessages(currentUser: String, query: String): List<Message> {
        val messages = mutableListOf<Message>()
        val db = this.readableDatabase
        val result = db.rawQuery(
            """SELECT * FROM messages 
               WHERE (sender = ? OR receiver = ?) AND text LIKE ?
               ORDER BY id DESC LIMIT 100""",
            arrayOf(currentUser, currentUser, "%$query%")
        )
        while (result.moveToNext()) { messages.add(cursorToMessage(result)) }
        result.close(); return messages
    }

    // ==================== ИЗБРАННОЕ ====================

    fun toggleFavorite(messageId: Int): Boolean {
        val db = this.writableDatabase
        val result = db.rawQuery("SELECT is_favorite FROM messages WHERE id = ?",
            arrayOf(messageId.toString()))
        val current = if (result.moveToFirst()) result.getInt(0) else 0
        result.close()
        val newState = if (current == 1) 0 else 1
        val values = ContentValues(); values.put("is_favorite", newState)
        db.update("messages", values, "id = ?", arrayOf(messageId.toString()))
        db.close()
        return newState == 1
    }

    fun getFavoriteMessages(login: String): List<Message> {
        val messages = mutableListOf<Message>()
        val db = this.readableDatabase
        val result = db.rawQuery(
            """SELECT * FROM messages 
               WHERE (sender = ? OR receiver = ?) AND is_favorite = 1
               ORDER BY id DESC""",
            arrayOf(login, login)
        )
        while (result.moveToNext()) { messages.add(cursorToMessage(result)) }
        result.close(); return messages
    }

    // ==================== ЗАКРЕПЛЁННЫЕ ЧАТЫ ====================

    fun isPinned(userLogin: String, chatWith: String): Boolean {
        val db = this.readableDatabase
        val result = db.rawQuery(
            "SELECT * FROM pinned_chats WHERE user_login = ? AND chat_with = ?",
            arrayOf(userLogin, chatWith)
        )
        val pinned = result.moveToFirst(); result.close(); return pinned
    }

    fun togglePin(userLogin: String, chatWith: String) {
        val db = this.writableDatabase
        if (isPinned(userLogin, chatWith)) {
            db.delete("pinned_chats", "user_login = ? AND chat_with = ?",
                arrayOf(userLogin, chatWith))
        } else {
            val values = ContentValues()
            values.put("user_login", userLogin); values.put("chat_with", chatWith)
            db.insertWithOnConflict("pinned_chats", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
        db.close()
    }

    fun deleteChat(currentUser: String, chatWith: String) {
        val db = this.writableDatabase
        db.delete("messages", "(sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)",
            arrayOf(currentUser, chatWith, chatWith, currentUser))
        db.delete("pinned_chats", "user_login = ? AND chat_with = ?",
            arrayOf(currentUser, chatWith))
        db.close()
    }

    // ==================== CURSOR → MESSAGE ====================

    private fun cursorToMessage(cursor: Cursor): Message {
        return Message(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            sender = cursor.getString(cursor.getColumnIndexOrThrow("sender")),
            receiver = cursor.getString(cursor.getColumnIndexOrThrow("receiver")),
            text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
            timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp")),
            isRead = cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 1,
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            mediaUrl = cursor.getString(cursor.getColumnIndexOrThrow("media_url")),
            duration = cursor.getInt(cursor.getColumnIndexOrThrow("duration")),
            replyToId = cursor.getInt(cursor.getColumnIndexOrThrow("reply_to_id")),
            replyToText = cursor.getString(cursor.getColumnIndexOrThrow("reply_to_text")),
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1
        )
    }
}