package com.example.myapplication.transport

import android.util.Log
import com.example.myapplication.Message
import com.example.myapplication.Repository
import com.example.myapplication.SessionManager
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
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var token: String = ""
    private var currentUserLogin: String = ""
    private var _isConnected = false

    var onConnectionStateChange: ((Boolean) -> Unit)? = null

    private var messageCallback: ((Message) -> Unit)? = null
    private var typingCallback: ((String, Boolean) -> Unit)? = null
    private var readCallback: ((String) -> Unit)? = null
    private var onlineCallback: ((String, Boolean) -> Unit)? = null

    fun setToken(savedToken: String, login: String) {
        this.token = savedToken
        this.currentUserLogin = login
    }

    override fun onMessageReceived(callback: (Message) -> Unit) {
        messageCallback = callback
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

    override suspend fun register(login: String, mail: String, password: String): TransportResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(RegisterRequest(login, mail, password))

                Log.e(TAG, "🚨 Register request: $body")

                val response = postRaw("/auth/register", body)

                if (response != null) {
                    Log.e(TAG, "🚨 Register response: ${response.take(100)}")

                    try {
                        val authResponse = json.decodeFromString<LegacyAuthResponse>(response)
                        token = authResponse.token
                        currentUserLogin = login
                        Log.e(TAG, "🚨 Register successful, token: ${token.take(20)}...")
                        TransportResult.Success(token)
                    } catch (e: Exception) {
                        val apiRes = json.decodeFromString<ApiResponse<AuthResponse>>(response)
                        if (apiRes.success && apiRes.data != null) {
                            token = apiRes.data.accessToken
                            currentUserLogin = login
                            Log.e(TAG, "🚨 Register successful (new API), token: ${token.take(20)}...")
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
                        Log.d(TAG, "✅ Login successful")
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
        text: String
    ): TransportResult<Int> {
        return withContext(Dispatchers.IO) {
            try {
                if (_isConnected && webSocket != null) {
                    val legacyMessage = mapOf(
                        "sender" to sender,
                        "receiver" to receiver,
                        "text" to text,
                        "type" to "text"
                    )

                    val jsonToSend = json.encodeToString(legacyMessage)
                    Log.d(TAG, "📤 Sending via WS: $jsonToSend")

                    val sent = webSocket?.send(jsonToSend) == true

                    if (sent) {
                        Log.d(TAG, "✅ Message sent via WS")
                        TransportResult.Success(1)
                    } else {
                        TransportResult.Error("WebSocket send failed")
                    }
                } else {
                    Log.w(TAG, "⚠️ WebSocket not connected, using HTTP fallback")
                    val body = json.encodeToString(
                        mapOf(
                            "sender" to sender,
                            "receiver" to receiver,
                            "text" to text
                        )
                    )
                    val response = postRaw("/messages", body)
                    if (response != null) {
                        TransportResult.Success(1)
                    } else {
                        TransportResult.Error("HTTP send failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send message error", e)
                TransportResult.Error("Failed to send", e)
            }
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
                        val wrapped = json.decodeFromString<com.example.myapplication.models.ApiResponse<List<UserDto>>>(apiResponse)
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

    override fun connect() {
        if (token.isEmpty()) {
            Log.e(TAG, "🚨❌ Cannot connect: token is EMPTY!")
            return
        }

        Log.e(TAG, "🚨 baseUrl: $baseUrl")

        val wsUrl = baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws?token=$token"

        Log.e(TAG, "🚨 Connecting to: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("User-Agent", "Android Messenger")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected = true
                onConnectionStateChange?.invoke(true)
                Log.e(TAG, "🚨✅✅✅ WEBSOCKET CONNECTED! ✅✅✅")
                Log.e(TAG, "🚨 Response: ${response.code} ${response.message}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected = false
                onConnectionStateChange?.invoke(false)

                Log.e(TAG, "🚨❌❌❌ WEBSOCKET FAILED: ${t.javaClass.simpleName}")
                Log.e(TAG, "🚨 Error message: ${t.message}")
                Log.e(TAG, "🚨 URL attempted: $wsUrl")

                if (response != null) {
                    Log.e(TAG, "🚨 Response code: ${response.code}")
                    Log.e(TAG, "🚨 Response message: ${response.message}")
                    response.body?.string()?.let {
                        Log.e(TAG, "🚨 Response body: $it")
                    }
                }

                t.printStackTrace()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.e(TAG, "🚨📥📥📥 WEBSOCKET MESSAGE: ${text.take(100)}")
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
            }
        })
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            // ✅ ПОПЫТКА 1: Envelope формат
            try {
                val envelope = json.decodeFromString<WsEnvelope>(text)

                when (envelope.type) {
                    "message" -> {
                        var msgDto = json.decodeFromString<MessageDto>(envelope.payload)

                        // ✅ ИГНОРИРУЕМ свои собственные сообщения (сервер шлёт подтверждение)
                        if (msgDto.sender == currentUserLogin) {
                            Log.d(TAG, "⏭️ Ignoring own message confirmation: ${msgDto.text.take(20)}")
                            return
                        }

                        if (msgDto.timestamp.isBlank()) {
                            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date())
                            msgDto = msgDto.copy(timestamp = now)
                        }

                        val msg = msgDto.toDomain()

                        // ✅ Дедупликация
                        val msgKey = "${msg.sender}:${msg.receiver}:${msg.text}:${msg.timestamp}"
                        if (!processedMessageIds.add(msgKey)) {
                            Log.d(TAG, "⏭️ Duplicate WS message skipped")
                            return
                        }
                        if (processedMessageIds.size > 100) {
                            val iterator = processedMessageIds.iterator()
                            repeat(50) { if (iterator.hasNext()) { iterator.next(); iterator.remove() } }
                        }

                        Repository.saveIncomingMessage(msg)

                        CoroutineScope(Dispatchers.Main).launch {
                            messageCallback?.invoke(msg)
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
                }

                return

            } catch (e: Exception) {
                Log.d(TAG, "Not envelope format, trying legacy: ${e.message}")
            }

            // ✅ ПОПЫТКА 2: Legacy формат
            try {
                var msgDto = json.decodeFromString<MessageDto>(text)

                // ✅ ИГНОРИРУЕМ свои собственные сообщения
                if (msgDto.sender == currentUserLogin) {
                    Log.d(TAG, "⏭️ Ignoring own legacy message confirmation: ${msgDto.text.take(20)}")
                    return
                }

                if (msgDto.timestamp.isBlank()) {
                    val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                    msgDto = msgDto.copy(timestamp = now)
                    Log.d(TAG, "⚠️ Legacy no timestamp, generated: $now")
                }

                val msg = msgDto.toDomain()

                // ✅ Дедупликация
                val msgKey = "${msg.sender}:${msg.receiver}:${msg.text}:${msg.timestamp}"
                if (!processedMessageIds.add(msgKey)) {
                    Log.d(TAG, "⏭️ Duplicate WS legacy message skipped")
                    return
                }
                if (processedMessageIds.size > 100) {
                    val iterator = processedMessageIds.iterator()
                    repeat(50) { if (iterator.hasNext()) { iterator.next(); iterator.remove() } }
                }

                Log.d(TAG, "✅ Legacy: ${msg.sender}→${msg.receiver}: ${msg.text.take(20)} | TS=${msg.timestamp}")

                Repository.saveIncomingMessage(msg)

                CoroutineScope(Dispatchers.Main).launch {
                    messageCallback?.invoke(msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse: $text", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ handleWebSocketMessage error", e)
        }
    }

    override fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected = false
        onConnectionStateChange?.invoke(false)
    }

    override fun isConnected() = _isConnected

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
                    val url = org.json.JSONObject(body).getString("url")
                    TransportResult.Success(url)
                } else {
                    TransportResult.Error("Upload failed: ${response.code}")
                }
            } catch (e: Exception) {
                TransportResult.Error("Network error", e)
            }
        }
    }

    override suspend fun sendMediaMessage(
        sender: String,
        receiver: String,
        text: String,
        type: String,
        mediaUrl: String,
        duration: Int
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
                        duration = duration
                    )

                    val envelope = WsEnvelope(
                        type = "message",
                        payload = json.encodeToString(messageData)
                    )

                    val sent = webSocket?.send(json.encodeToString(envelope)) == true
                    if (sent) {
                        TransportResult.Success(1)
                    } else {
                        TransportResult.Error("WS send failed")
                    }
                } else {
                    val body = json.encodeToString(
                        MessageDto(
                            sender = sender,
                            receiver = receiver,
                            text = text,
                            type = type,
                            mediaUrl = mediaUrl,
                            duration = duration
                        )
                    )
                    val response = postRaw("/messages", body)
                    if (response != null) {
                        TransportResult.Success(1)
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