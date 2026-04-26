package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(
    context: Context,
    factory: SQLiteDatabase.CursorFactory?
) : SQLiteOpenHelper(context, "messenger_app", factory, 11) { // ✅ Версия 11

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
            client_message_id TEXT,
            sender TEXT,
            receiver TEXT,
            text TEXT,
            timestamp DATETIME DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')),
            is_read INTEGER DEFAULT 0,
            type TEXT DEFAULT 'text',
            media_url TEXT DEFAULT NULL,
            duration INTEGER DEFAULT 0,
            reply_to_id INTEGER DEFAULT 0,
            reply_to_text TEXT DEFAULT NULL,
            is_favorite INTEGER DEFAULT 0,
            is_sent INTEGER DEFAULT 1,
            
            UNIQUE(sender, receiver, text, timestamp) ON CONFLICT IGNORE
        )"""
        )

        // ✅ Индекс для быстрого поиска по client_message_id
        db.execSQL(
            "CREATE INDEX idx_client_message_id ON messages(client_message_id)"
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
        if (oldVersion < 11) {
            db!!.execSQL("ALTER TABLE messages ADD COLUMN client_message_id TEXT DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_client_message_id ON messages(client_message_id)")
        }
    }

    // ==================== НОРМАЛИЗАЦИЯ ====================

    private fun normalizeTimestamp(ts: String): String {
        return try {
            ts.replace("T", " ")
                .replace(Regex("\\..*Z?$"), "")
                .trim()
        } catch (e: Exception) {
            ts
        }
    }

    // ==================== СООБЩЕНИЯ ====================

    // ✅ ОСНОВНОЙ МЕТОД: добавление сообщения с clientMessageId
    fun addMessage(
        sender: String,
        receiver: String,
        text: String,
        isSent: Boolean = true,
        clientMessageId: String = ""
    ): Int {
        return writableDatabase.insert("messages", null, ContentValues().apply {
            put("sender", sender)
            put("receiver", receiver)
            put("text", text)
            put("type", "text")
            put("is_sent", if (isSent) 1 else 0)
            put("client_message_id", clientMessageId)
        }).toInt()
    }

    // ✅ Сохранение с серверным timestamp + clientMessageId
    fun addMessageWithTimestamp(
        sender: String,
        receiver: String,
        text: String,
        timestamp: String,
        isSent: Boolean = true,
        clientMessageId: String = ""
    ): Int {
        return writableDatabase.insert("messages", null, ContentValues().apply {
            put("sender", sender)
            put("receiver", receiver)
            put("text", text)
            put("timestamp", normalizeTimestamp(timestamp))
            put("type", "text")
            put("is_sent", if (isSent) 1 else 0)
            put("client_message_id", clientMessageId)
        }).toInt()
    }

    // ✅ НОВЫЙ МЕТОД: найти сообщение по clientMessageId
    fun getMessageByClientMessageId(clientMessageId: String): Message? {
        if (clientMessageId.isBlank()) return null
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE client_message_id = ?",
            arrayOf(clientMessageId)
        )
        val msg = if (cursor.moveToFirst()) cursorToMessage(cursor) else null
        cursor.close()
        return msg
    }
    fun deleteMessageByClientMessageId(clientMessageId: String): Int {
        return writableDatabase.delete(
            "messages",
            "client_message_id = ? AND client_message_id <> ''",
            arrayOf(clientMessageId)
        )
    }

    // ✅ НОВЫЙ МЕТОД: подтвердить своё сообщение серверным timestamp и ID
    fun confirmOwnMessage(
        localId: Int,
        serverTimestamp: String,
        serverId: Int
    ): Boolean {
        val updated = writableDatabase.update(
            "messages",
            ContentValues().apply {
                put("timestamp", normalizeTimestamp(serverTimestamp))
                put("is_sent", 1)
            },
            "id = ?",
            arrayOf(localId.toString())
        )
        return updated > 0
    }

    // ✅ НОВЫЙ МЕТОД: найти последнее неотправленное сообщение по тексту
    fun findPendingMessage(sender: String, receiver: String, text: String): Message? {
        val cursor = readableDatabase.rawQuery(
            """SELECT * FROM messages 
               WHERE sender = ? AND receiver = ? AND text = ? AND is_sent = 0
               ORDER BY id DESC LIMIT 1""",
            arrayOf(sender, receiver, text)
        )
        val msg = if (cursor.moveToFirst()) cursorToMessage(cursor) else null
        cursor.close()
        return msg
    }

    // ==================== Существующие методы ====================

    fun addImageMessage(sender: String, receiver: String, imagePath: String, isSent: Boolean = true, clientMessageId: String = ""): Int {
        return writableDatabase.insert("messages", null, ContentValues().apply {
            put("sender", sender)
            put("receiver", receiver)
            put("text", "📷 Фото")
            put("type", "image")
            put("media_url", imagePath)
            put("is_sent", if (isSent) 1 else 0)
            put("client_message_id", clientMessageId)
        }).toInt()
    }

    fun addAudioMessage(sender: String, receiver: String, audioPath: String, duration: Int, isSent: Boolean = true, clientMessageId: String = ""): Int {
        return writableDatabase.insert("messages", null, ContentValues().apply {
            put("sender", sender)
            put("receiver", receiver)
            put("text", "🎤 Голосовое")
            put("type", "audio")
            put("media_url", audioPath)
            put("duration", duration)
            put("is_sent", if (isSent) 1 else 0)
            put("client_message_id", clientMessageId)
        }).toInt()
    }

    fun addReplyMessage(sender: String, receiver: String, text: String, replyToId: Int, replyToText: String, isSent: Boolean = true, clientMessageId: String = ""): Int {
        return writableDatabase.insert("messages", null, ContentValues().apply {
            put("sender", sender)
            put("receiver", receiver)
            put("text", text)
            put("type", "text")
            put("reply_to_id", replyToId)
            put("reply_to_text", replyToText)
            put("is_sent", if (isSent) 1 else 0)
            put("client_message_id", clientMessageId)
        }).toInt()
    }

    fun getMessages(user1: String, user2: String): List<Message> {
        val messages = mutableListOf<Message>()
        val cursor = readableDatabase.rawQuery(
            """SELECT * FROM messages 
               WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
               ORDER BY id ASC""",
            arrayOf(user1, user2, user2, user1)
        )
        while (cursor.moveToNext()) {
            messages.add(cursorToMessage(cursor))
        }
        cursor.close()
        return messages
    }

    fun messageExists(sender: String, receiver: String, text: String, timestamp: String): Boolean {
        val normalized = normalizeTimestamp(timestamp)
        val cursor = readableDatabase.rawQuery(
            """SELECT id FROM messages 
               WHERE sender = ? AND receiver = ? AND text = ? AND timestamp = ?""",
            arrayOf(sender, receiver, text, normalized)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    // ✅ НОВЫЙ: проверка по clientMessageId
    fun messageExistsByClientId(clientMessageId: String): Boolean {
        if (clientMessageId.isBlank()) return false
        val cursor = readableDatabase.rawQuery(
            "SELECT id FROM messages WHERE client_message_id = ?",
            arrayOf(clientMessageId)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getLastMessage(user1: String, user2: String): String {
        val cursor = readableDatabase.rawQuery(
            """SELECT text FROM messages 
               WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
               ORDER BY id DESC LIMIT 1""",
            arrayOf(user1, user2, user2, user1)
        )
        val msg = if (cursor.moveToFirst()) cursor.getString(0) else ""
        cursor.close()
        return msg
    }

    fun getLastMessageTime(user1: String, user2: String): String {
        val cursor = readableDatabase.rawQuery(
            """SELECT timestamp FROM messages 
               WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)
               ORDER BY id DESC LIMIT 1""",
            arrayOf(user1, user2, user2, user1)
        )
        val time = if (cursor.moveToFirst()) cursor.getString(0) else ""
        cursor.close()
        return time
    }

    fun getUnreadCount(currentUser: String, sender: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE sender = ? AND receiver = ? AND is_read = 0",
            arrayOf(sender, currentUser)
        )
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    fun markMessageAsSent(messageId: Int) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("is_sent", 1) },
            "id = ?",
            arrayOf(messageId.toString())
        )
    }

    fun getUnsentMessages(currentUser: String): List<Message> {
        val list = mutableListOf<Message>()
        val cursor = readableDatabase.rawQuery(
            """SELECT * FROM messages 
               WHERE sender = ? AND is_sent = 0 
               ORDER BY timestamp ASC""",
            arrayOf(currentUser)
        )
        while (cursor.moveToNext()) {
            list.add(cursorToMessage(cursor))
        }
        cursor.close()
        return list
    }

    fun markMessagesAsRead(currentUser: String, sender: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("is_read", 1) },
            "sender = ? AND receiver = ? AND is_read = 0",
            arrayOf(sender, currentUser)
        )
    }

    fun deleteMessage(messageId: Int) {
        writableDatabase.delete("messages", "id = ?", arrayOf(messageId.toString()))
    }

    fun editMessage(messageId: Int, newText: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("text", newText) },
            "id = ?",
            arrayOf(messageId.toString())
        )
    }

    fun searchMessages(currentUser: String, chatWith: String, query: String): List<Message> {
        val messages = mutableListOf<Message>()
        val cursor = readableDatabase.rawQuery(
            """SELECT * FROM messages 
               WHERE ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?))
               AND text LIKE ? ORDER BY id ASC""",
            arrayOf(currentUser, chatWith, chatWith, currentUser, "%$query%")
        )
        while (cursor.moveToNext()) { messages.add(cursorToMessage(cursor)) }
        cursor.close()
        return messages
    }

    fun searchGlobalMessages(currentUser: String, query: String): List<Message> {
        val messages = mutableListOf<Message>()
        val cursor = readableDatabase.rawQuery(
            """SELECT * FROM messages 
               WHERE (sender = ? OR receiver = ?) AND text LIKE ?
               ORDER BY id DESC LIMIT 100""",
            arrayOf(currentUser, currentUser, "%$query%")
        )
        while (cursor.moveToNext()) { messages.add(cursorToMessage(cursor)) }
        cursor.close()
        return messages
    }

    fun toggleFavorite(messageId: Int): Boolean {
        val cursor = readableDatabase.rawQuery("SELECT is_favorite FROM messages WHERE id = ?", arrayOf(messageId.toString()))
        val current = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        val newState = if (current == 1) 0 else 1
        writableDatabase.update("messages", ContentValues().apply { put("is_favorite", newState) }, "id = ?", arrayOf(messageId.toString()))
        return newState == 1
    }

    fun getFavoriteMessages(login: String): List<Message> {
        val messages = mutableListOf<Message>()
        val cursor = readableDatabase.rawQuery(
            """SELECT * FROM messages 
               WHERE (sender = ? OR receiver = ?) AND is_favorite = 1
               ORDER BY id DESC""",
            arrayOf(login, login)
        )
        while (cursor.moveToNext()) { messages.add(cursorToMessage(cursor)) }
        cursor.close()
        return messages
    }

    // ==================== ПОЛЬЗОВАТЕЛИ ====================

    fun addUser(user: User) {
        writableDatabase.insertOrThrow("users", null, ContentValues().apply {
            put("login", user.login)
            put("mail", user.mail)
            put("password", HashUtils.sha256(user.password))
        })
    }

    fun getUser(login: String, password: String): Boolean {
        val hashedPassword = HashUtils.sha256(password)
        val cursor = readableDatabase.rawQuery("SELECT * FROM users WHERE login = ? AND password = ?", arrayOf(login, hashedPassword))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getAllUsersExcept(currentLogin: String): List<User> {
        val users = mutableListOf<User>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM users WHERE login != ?", arrayOf(currentLogin))
        while (cursor.moveToNext()) { users.add(cursorToUser(cursor)) }
        cursor.close()
        return users
    }

    fun searchUsers(query: String, exceptLogin: String): List<User> {
        val users = mutableListOf<User>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM users WHERE login LIKE ? AND login != ?", arrayOf("%$query%", exceptLogin))
        while (cursor.moveToNext()) { users.add(cursorToUser(cursor)) }
        cursor.close()
        return users
    }

    fun getUsersWithMessages(currentUser: String): List<User> {
        val users = mutableListOf<User>()
        val cursor = readableDatabase.rawQuery(
            """SELECT DISTINCT u.* FROM users u
               INNER JOIN messages m 
               ON (m.sender = u.login AND m.receiver = ?) OR (m.receiver = u.login AND m.sender = ?)
               WHERE u.login != ?""",
            arrayOf(currentUser, currentUser, currentUser)
        )
        while (cursor.moveToNext()) { users.add(cursorToUser(cursor)) }
        cursor.close()
        return users
    }

    fun getUserMail(login: String): String {
        val cursor = readableDatabase.rawQuery("SELECT mail FROM users WHERE login = ?", arrayOf(login))
        val mail = if (cursor.moveToFirst()) cursor.getString(0) else ""
        cursor.close()
        return mail
    }

    fun changeLogin(oldLogin: String, newLogin: String): Boolean {
        return try {
            writableDatabase.apply {
                update("users", ContentValues().apply { put("login", newLogin) }, "login = ?", arrayOf(oldLogin))
                update("messages", ContentValues().apply { put("sender", newLogin) }, "sender = ?", arrayOf(oldLogin))
                update("messages", ContentValues().apply { put("receiver", newLogin) }, "receiver = ?", arrayOf(oldLogin))
            }
            true
        } catch (e: Exception) { false }
    }

    fun changePassword(login: String, newPassword: String) {
        writableDatabase.update("users", ContentValues().apply { put("password", HashUtils.sha256(newPassword)) }, "login = ?", arrayOf(login))
    }

    fun setAvatar(login: String, path: String) {
        writableDatabase.update("users", ContentValues().apply { put("avatar_path", path) }, "login = ?", arrayOf(login))
    }

    fun getAvatar(login: String): String? {
        val cursor = readableDatabase.rawQuery("SELECT avatar_path FROM users WHERE login = ?", arrayOf(login))
        val path = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        return path
    }

    fun addUserIfNotExists(login: String) {
        val cursor = readableDatabase.rawQuery("SELECT id FROM users WHERE login = ?", arrayOf(login))
        val exists = cursor.moveToFirst()
        cursor.close()
        if (!exists) {
            try { writableDatabase.insert("users", null, ContentValues().apply { put("login", login); put("mail", ""); put("password", "") }) }
            catch (_: Exception) {}
        }
    }

    fun isPinned(userLogin: String, chatWith: String): Boolean {
        val cursor = readableDatabase.rawQuery("SELECT * FROM pinned_chats WHERE user_login = ? AND chat_with = ?", arrayOf(userLogin, chatWith))
        val pinned = cursor.moveToFirst()
        cursor.close()
        return pinned
    }

    fun togglePin(userLogin: String, chatWith: String) {
        if (isPinned(userLogin, chatWith)) {
            writableDatabase.delete("pinned_chats", "user_login = ? AND chat_with = ?", arrayOf(userLogin, chatWith))
        } else {
            writableDatabase.insertWithOnConflict("pinned_chats", null, ContentValues().apply {
                put("user_login", userLogin); put("chat_with", chatWith)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun deleteChat(currentUser: String, chatWith: String) {
        writableDatabase.delete("messages", "(sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)", arrayOf(currentUser, chatWith, chatWith, currentUser))
        writableDatabase.delete("pinned_chats", "user_login = ? AND chat_with = ?", arrayOf(currentUser, chatWith))
    }

    fun getMessageById(id: Int): Message? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM messages WHERE id = ?", arrayOf(id.toString()))
        val msg = if (cursor.moveToFirst()) cursorToMessage(cursor) else null
        cursor.close()
        return msg
    }

    // ==================== МАППЕРЫ ====================

    private fun cursorToUser(cursor: Cursor): User {
        return User(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            login = cursor.getString(cursor.getColumnIndexOrThrow("login")),
            mail = cursor.getString(cursor.getColumnIndexOrThrow("mail")),
            password = "",
            avatarPath = cursor.getString(cursor.getColumnIndexOrThrow("avatar_path"))
        )
    }

    private fun cursorToMessage(cursor: Cursor): Message {
        return Message(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            clientMessageId = cursor.getString(cursor.getColumnIndexOrThrow("client_message_id")) ?: "",
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
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1,
            isSent = cursor.getInt(cursor.getColumnIndexOrThrow("is_sent")) == 1
        )
    }
}