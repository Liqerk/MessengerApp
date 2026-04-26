package com.example.myapplication.transport

import android.util.Log
import com.example.myapplication.AppLifecycleTracker
import com.example.myapplication.Message
import com.example.myapplication.Repository
import com.example.myapplication.models.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.example.myapplication.User
import com.example.myapplication.ChatItem
import com.example.myapplication.NotificationHelper
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.util.Collections

class ServerTransport(
    private var baseUrl: String = "http://192.168.0.35:8080"
) : MessageTransport {

    private val TAG = "ServerTransport"
    private val processedMessageIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        var appContext: android.content.Context? = null
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)  // ✅ ПИНГ КАЖДЫЕ 20 СЕК
        .build()

    private var webSocket: WebSocket? = null
    private var token: String = ""
    private var currentUserLogin: String = ""
    private var _isConnected = false

    // ✅ АВТОПЕРЕПОДКЛЮЧЕНИЕ
    private var shouldBeConnected = false
    private var reconnectJob: Job? = null
    private var healthCheckJob: Job? = null
    private var deleteCallback: ((clientMessageId: String) -> Unit)? = null
    private var deleteChatCallback: ((String) -> Unit)? = null

    fun onDeleteMessageReceived(callback: (clientMessageId: String) -> Unit) {
        deleteCallback = callback
    }

    fun onDeleteChatReceived(callback: (String) -> Unit) {
        deleteChatCallback = callback
    }
    var onConnectionStateChange: ((Boolean) -> Unit)? = null

    private var messageCallback: ((Message) -> Unit)? = null
    private var typingCallback: ((String, Boolean) -> Unit)? = null
    private var readCallback: ((String) -> Unit)? = null
    private var onlineCallback: ((String, Boolean) -> Unit)? = null
    private val messageCallbacks = mutableListOf<(Message) -> Unit>()

    override fun onMessageReceived(callback: (Message) -> Unit) {
        messageCallbacks.add(callback)
    }

    override fun removeMessageCallback(callback: (Message) -> Unit) {
        messageCallbacks.remove(callback)
    }

    fun clearMessageCallbacks() {
        messageCallbacks.clear()
    }

    fun setToken(savedToken: String, login: String) {
        this.token = savedToken
        this.currentUserLogin = login
    }

    fun onTypingReceived(callback: (String, Boolean) -> Unit) {
        typingCallback = callback
    }

    fun onReadReceived(callback: (String) -> Unit) {
        readCallback = callback
    }

    fun onOnlineStatusReceived(callback: (String, Boolean) -> Unit) {
        onlineCallback = callback
    }

    fun sendTyping(receiver: String, isTyping: Boolean) {
        if (!_isConnected || webSocket == null) return

        val payload = json.encodeToString(
            WsTypingEvent(
                sender = currentUserLogin,
                receiver = receiver,
                isTyping = isTyping
            )
        )

        val envelope = json.encodeToString(
            WsEnvelope(
                type = "typing",
                payload = payload
            )
        )

        webSocket?.send(envelope)
    }

    fun sendRead(sender: String) {
        if (!_isConnected || webSocket == null) return

        val payload = json.encodeToString(
            WsReadEvent(
                sender = sender,
                reader = currentUserLogin
            )
        )

        val envelope = json.encodeToString(
            WsEnvelope(
                type = "read",
                payload = payload
            )
        )

        webSocket?.send(envelope)
    }

    // ==========================================
    // ПОДКЛЮЧЕНИЕ С АВТОПЕРЕПОДКЛЮЧЕНИЕМ
    // ==========================================

    override fun connect() {
        if (token.isEmpty()) {
            Log.e(TAG, "Cannot connect: token is EMPTY!")
            return
        }

        shouldBeConnected = true
        startHealthCheck()  // ✅ ЗАПУСКАЕМ МОНИТОРИНГ
        attemptConnect()
    }

    private fun attemptConnect() {
        if (!shouldBeConnected) return

        val wsUrl = baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws?token=$token"

        Log.d(TAG, "🔌 Connecting to: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("User-Agent", "Android Messenger")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected = true
                reconnectJob?.cancel()
                onConnectionStateChange?.invoke(true)
                Log.d(TAG, "✅ WebSocket CONNECTED")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected = false
                onConnectionStateChange?.invoke(false)
                Log.e(TAG, "❌ WebSocket FAILED: ${t.message}")

                scheduleReconnect(3000)  // ✅ ЧЕРЕЗ 3 СЕК
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected = false
                onConnectionStateChange?.invoke(false)
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected = false
                onConnectionStateChange?.invoke(false)
                Log.w(TAG, "🔌 WebSocket CLOSED: $code $reason")

                if (shouldBeConnected) {
                    scheduleReconnect(3000)  // ✅ ЧЕРЕЗ 3 СЕК
                }
            }
        })
    }

    // ✅ ПЕРЕПОДКЛЮЧЕНИЕ
    private fun scheduleReconnect(delayMs: Long) {
        if (!shouldBeConnected) return

        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(delayMs)
            if (shouldBeConnected && !_isConnected) {
                Log.d(TAG, "🔄 Reconnecting...")
                attemptConnect()
            }
        }
    }

    // ✅ ПЕРИОДИЧЕСКАЯ ПРОВЕРКА КАЖДЫЕ 10 СЕКУНД
    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (shouldBeConnected) {
                delay(10_000)  // Проверяем каждые 10 секунд

                if (!_isConnected && shouldBeConnected) {
                    Log.d(TAG, "🏥 Health check: not connected, reconnecting...")
                    attemptConnect()
                }
            }
        }
    }

    override fun disconnect() {
        shouldBeConnected = false
        reconnectJob?.cancel()
        healthCheckJob?.cancel()  // ✅ ОСТАНАВЛИВАЕМ МОНИТОРИНГ
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected = false
        onConnectionStateChange?.invoke(false)
        Log.d(TAG, "🔌 Disconnected")
    }

    override fun isConnected() = _isConnected

    // ==========================================
    // ОСТАЛЬНЫЕ МЕТОДЫ БЕЗ ИЗМЕНЕНИЙ
    // ==========================================

    override suspend fun register(login: String, mail: String, password: String): TransportResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(RegisterRequest(login, mail, password))
                val response = postRaw("/auth/register", body)

                if (response != null) {
                    try {
                        val authResponse = json.decodeFromString<LegacyAuthResponse>(response)
                        token = authResponse.token
                        currentUserLogin = login
                        TransportResult.Success(token)
                    } catch (e: Exception) {
                        val apiRes = json.decodeFromString<ApiResponse<AuthResponse>>(response)
                        if (apiRes.success && apiRes.data != null) {
                            token = apiRes.data.accessToken
                            currentUserLogin = login
                            TransportResult.Success(token)
                        } else {
                            TransportResult.Error(apiRes.error ?: "Registration failed")
                        }
                    }
                } else {
                    TransportResult.Error("Network error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Register error", e)
                TransportResult.Error("Network error: ${e.message}", e)
            }
        }
    }

    override suspend fun login(login: String, password: String): TransportResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(LoginRequest(login, password))
                val response = postRaw("/api/v1/auth/login", body)

                if (response != null) {
                    val apiRes = json.decodeFromString<ApiResponse<AuthResponse>>(response)
                    if (apiRes.success && apiRes.data != null) {
                        token = apiRes.data.accessToken
                        currentUserLogin = login
                        TransportResult.Success(token)
                    } else {
                        TransportResult.Error(apiRes.error ?: "Invalid credentials")
                    }
                } else {
                    TransportResult.Error("Invalid credentials or server down")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                TransportResult.Error("Network error: ${e.message}", e)
            }
        }
    }

    override suspend fun sendMessage(
        sender: String,
        receiver: String,
        text: String,
        clientMessageId: String
    ): TransportResult<Int> {
        return withContext(Dispatchers.IO) {
            try {
                if (_isConnected && webSocket != null) {
                    val msg = mapOf(
                        "sender" to sender,
                        "receiver" to receiver,
                        "text" to text,
                        "type" to "text",
                        "clientMessageId" to clientMessageId
                    )
                    val sent = webSocket?.send(json.encodeToString(msg)) == true
                    if (sent) TransportResult.Success(1) else TransportResult.Error("WS failed")
                } else {
                    val body = json.encodeToString(
                        mapOf(
                            "sender" to sender,
                            "receiver" to receiver,
                            "text" to text,
                            "clientMessageId" to clientMessageId
                        )
                    )
                    val response = postRaw("/messages", body)

                    if (response != null) {
                        try {
                            val msgDto = json.decodeFromString<MessageDto>(response)

                            if (msgDto.clientMessageId.isNotBlank()) {
                                Repository.confirmOwnMessage(
                                    clientMessageId = msgDto.clientMessageId,
                                    serverTimestamp = msgDto.timestamp,
                                    serverId = msgDto.id
                                )
                                Log.d(TAG, "✅ HTTP msg confirmed: CID=${msgDto.clientMessageId}")
                            }

                            TransportResult.Success(1)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse HTTP response", e)
                            TransportResult.Success(1)
                        }
                    } else {
                        TransportResult.Error("HTTP send failed")
                    }
                }
            } catch (e: Exception) {
                TransportResult.Error("Failed to send", e)
            }
        }
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            val envelope = json.decodeFromString<WsEnvelope>(text)

            when (envelope.type) {
                "message" -> {
                    val msgDto = json.decodeFromString<MessageDto>(envelope.payload)

                    // ✅ СОБСТВЕННЫЕ СООБЩЕНИЯ - ИГНОРИРУЕМ (сервер больше не шлёт)
                    if (msgDto.sender == currentUserLogin) {
                        Log.d(TAG, "⏭️ Skipping own message echo")
                        return
                    }

                    // ✅ ВХОДЯЩИЕ СООБЩЕНИЯ
                    val msg = msgDto.toDomain()
                    val msgKey = msgDto.clientMessageId.ifBlank {
                        "${msg.sender}:${msg.receiver}:${msg.text}:${msg.timestamp}"
                    }

                    if (!processedMessageIds.add(msgKey)) {
                        Log.d(TAG, "⏭️ Duplicate ignored: $msgKey")
                        return
                    }

                    if (processedMessageIds.size > 100) {
                        val iterator = processedMessageIds.iterator()
                        repeat(50) {
                            if (iterator.hasNext()) {
                                iterator.next()
                                iterator.remove()
                            }
                        }
                    }

                    Repository.saveIncomingMessage(msg)

                    if (!AppLifecycleTracker.isAppInForeground) {
                        appContext?.let { ctx ->
                            NotificationHelper.showMessageNotification(ctx, msg.sender, msg.text)
                        }
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        messageCallbacks.forEach { it.invoke(msg) }
                    }
                }

                "typing" -> {
                    val event = json.decodeFromString<WsTypingEvent>(envelope.payload)
                    CoroutineScope(Dispatchers.Main).launch {
                        typingCallback?.invoke(event.sender, event.isTyping)
                    }
                }

                "read" -> {
                    val event = json.decodeFromString<WsReadEvent>(envelope.payload)
                    CoroutineScope(Dispatchers.Main).launch {
                        readCallback?.invoke(event.reader)
                    }
                }

                "online" -> {
                    val event = json.decodeFromString<WsOnlineEvent>(envelope.payload)
                    CoroutineScope(Dispatchers.Main).launch {
                        onlineCallback?.invoke(event.login, event.isOnline)
                    }
                }

                "delete" -> {
                    val event = json.decodeFromString<WsDeleteEvent>(envelope.payload)
                    Log.d(TAG, "🗑️ Delete event: CID=${event.clientMessageId}")

                    CoroutineScope(Dispatchers.Main).launch {
                        val deleted = if (event.clientMessageId.isNotBlank()) {
                            Repository.deleteMessageByClientId(event.clientMessageId)
                        } else {
                            false
                        }

                        if (deleted) {
                            deleteCallback?.invoke(event.clientMessageId)
                        }
                    }
                }

                "delete_chat" -> {
                    val event = json.decodeFromString<WsDeleteChatEvent>(envelope.payload)
                    Log.d(TAG, "🗑️ Delete chat event: ${event.chatWith}")

                    CoroutineScope(Dispatchers.Main).launch {
                        deleteChatCallback?.invoke(event.chatWith)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ handleWebSocketMessage error", e)
        }
    }

    override suspend fun getMessages(user1: String, user2: String): TransportResult<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get("/messages?user1=$user1&user2=$user2")
                if (response != null) {
                    val dtos = json.decodeFromString<List<MessageDto>>(response)
                    TransportResult.Success(dtos.map { it.toDomain() })
                } else {
                    TransportResult.Error("Failed to load messages")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Get messages error", e)
                TransportResult.Error("Network error", e)
            }
        }
    }

    override suspend fun markAsRead(currentUser: String, sender: String): TransportResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                sendRead(sender)
                TransportResult.Success(true)
            } catch (e: Exception) {
                TransportResult.Error("Failed to mark as read", e)
            }
        }
    }

    override suspend fun getChatList(currentUser: String): TransportResult<List<ChatItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = get("/chats?user=$currentUser")
                if (response != null) {
                    val dtos = json.decodeFromString<List<ChatItemDto>>(response)
                    TransportResult.Success(dtos.map { it.toDomain() })
                } else {
                    TransportResult.Error("Failed to load chats")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Get chats error", e)
                TransportResult.Error("Network error", e)
            }
        }
    }

    override suspend fun searchUsers(query: String, currentUser: String): TransportResult<List<User>> {
        return withContext(Dispatchers.IO) {
            try {
                val legacyResponse = get("/users/search?q=$query&except=$currentUser")
                if (legacyResponse != null) {
                    try {
                        val dtos = json.decodeFromString<List<UserDto>>(legacyResponse)
                        return@withContext TransportResult.Success(dtos.map { it.toDomain() })
                    } catch (e: Exception) {
                        Log.e(TAG, "Legacy users parse failed: ${e.message}")
                    }
                }

                val apiResponse = get("/api/v1/users/search?q=$query")
                if (apiResponse != null) {
                    try {
                        val wrapped = json.decodeFromString<ApiResponse<List<UserDto>>>(apiResponse)
                        val users = wrapped.data?.map { it.toDomain() } ?: emptyList()
                        return@withContext TransportResult.Success(users)
                    } catch (e: Exception) {
                        Log.e(TAG, "API users parse failed: ${e.message}")
                    }
                }

                TransportResult.Error("Search failed")
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                TransportResult.Error("Network error", e)
            }
        }
    }

    private fun postRaw(path: String, jsonBody: String): String? {
        return try {
            val builder = Request.Builder()
                .url("$baseUrl$path")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))

            if (token.isNotEmpty()) {
                builder.addHeader("Authorization", "Bearer $token")
            }

            val request = builder.build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && bodyString != null) {
                bodyString
            } else {
                Log.e(TAG, "POST $path failed: HTTP ${response.code} ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed: ${e.message}", e)
            null
        }
    }

    private fun get(path: String): String? {
        return try {
            val builder = Request.Builder()
                .url("$baseUrl$path")
                .get()

            if (token.isNotEmpty()) {
                builder.addHeader("Authorization", "Bearer $token")
            }

            val request = builder.build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && bodyString != null) {
                bodyString
            } else {
                Log.e(TAG, "GET $path failed: HTTP ${response.code} ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed: ${e.message}", e)
            null
        }
    }

    override suspend fun uploadFile(file: File): TransportResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/upload")
                    .post(requestBody)
                    .apply { if (token.isNotEmpty()) addHeader("Authorization", "Bearer $token") }
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    var url = org.json.JSONObject(body).getString("url")

                    if (url.startsWith("/")) {
                        url = "$baseUrl$url"
                    }

                    Log.d(TAG, "✅ Upload success: $url")
                    TransportResult.Success(url)
                } else {
                    TransportResult.Error("Upload failed: ${response.code}")
                }
            } catch (e: Exception) {
                TransportResult.Error("Network error", e)
            }
        }
    }
    override suspend fun deleteMessage(messageId: Int, clientMessageId: String): TransportResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (_isConnected && webSocket != null) {
                    // ✅ ОТПРАВЛЯЕМ ТОЛЬКО clientMessageId
                    val envelope = WsEnvelope(
                        type = "delete",
                        payload = json.encodeToString(
                            mapOf("clientMessageId" to clientMessageId)
                        )
                    )
                    val sent = webSocket?.send(json.encodeToString(envelope)) == true

                    if (sent) {
                        TransportResult.Success(true)
                    } else {
                        TransportResult.Error("WS failed")
                    }
                } else {
                    // Оффлайн - локальное удаление
                    TransportResult.Success(true)
                }
            } catch (e: Exception) {
                TransportResult.Error("Delete failed: ${e.message}", e)
            }
        }
    }

    // ✅ НОВЫЙ: Удаление чата через WS
    override suspend fun deleteChat(user1: String, user2: String): TransportResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (_isConnected && webSocket != null) {
                    // ✅ ТОЛЬКО chatWith (БЕЗ deletedBy)
                    val envelope = WsEnvelope(
                        type = "delete_chat",
                        payload = json.encodeToString(
                            mapOf("chatWith" to user2)
                        )
                    )
                    val sent = webSocket?.send(json.encodeToString(envelope)) == true

                    if (sent) {
                        TransportResult.Success(true)
                    } else {
                        TransportResult.Error("WS failed")
                    }
                } else {
                    // Оффлайн - локальное удаление
                    Repository.deleteChat(user1, user2)
                    TransportResult.Success(true)
                }
            } catch (e: Exception) {
                TransportResult.Error("Delete chat failed: ${e.message}", e)
            }
        }
    }
    override suspend fun sendMediaMessage(
        sender: String,
        receiver: String,
        text: String,
        type: String,
        mediaUrl: String,
        duration: Int,
        clientMessageId: String
    ): TransportResult<Int> {
        return withContext(Dispatchers.IO) {
            try {
                if (_isConnected && webSocket != null) {
                    val messageData = MessageDto(
                        sender = sender,
                        receiver = receiver,
                        text = text,
                        type = type,
                        mediaUrl = mediaUrl,
                        duration = duration,
                        clientMessageId = clientMessageId
                    )
                    val envelope = WsEnvelope(
                        type = "message",
                        payload = json.encodeToString(messageData)
                    )
                    val sent = webSocket?.send(json.encodeToString(envelope)) == true
                    if (sent) TransportResult.Success(1) else TransportResult.Error("WS failed")
                } else {
                    val body = json.encodeToString(
                        MessageDto(
                            sender = sender,
                            receiver = receiver,
                            text = text,
                            type = type,
                            mediaUrl = mediaUrl,
                            duration = duration,
                            clientMessageId = clientMessageId
                        )
                    )
                    val response = postRaw("/messages", body)

                    if (response != null) {
                        try {
                            val msgDto = json.decodeFromString<MessageDto>(response)

                            if (msgDto.clientMessageId.isNotBlank()) {
                                Repository.confirmOwnMessage(
                                    clientMessageId = msgDto.clientMessageId,
                                    serverTimestamp = msgDto.timestamp,
                                    serverId = msgDto.id
                                )
                            }

                            TransportResult.Success(1)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse media response", e)
                            TransportResult.Success(1)
                        }
                    } else {
                        TransportResult.Error("HTTP send failed")
                    }
                }
            } catch (e: Exception) {
                TransportResult.Error("Error: ${e.message}", e)
            }
        }
    }
}