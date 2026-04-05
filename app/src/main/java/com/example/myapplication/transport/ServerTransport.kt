package com.example.myapplication.transport

import android.util.Log
import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.Repository
import com.example.myapplication.User
import com.example.myapplication.models.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ServerTransport(
    private var baseUrl: String = "http://192.168.0.35:8080"
) : MessageTransport {

    private val TAG = "ServerTransport"

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
    private var messageCallback: ((Message) -> Unit)? = null
    private var _isConnected = false

    fun updateUrl(url: String) {
        baseUrl = url
        Log.d(TAG, "📍 Server URL updated to: $url")
    }

    // ========== АВТОРИЗАЦИЯ ==========

    override suspend fun register(login: String, mail: String, password: String): TransportResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = """{"login":"$login","mail":"$mail","password":"$password"}"""
                val response = postRaw("/auth/register", body)

                if (response != null) {
                    val auth = json.decodeFromString<AuthResponse>(response)
                    token = auth.token
                    TransportResult.Success(token)
                } else {
                    TransportResult.Error("Registration failed")
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
                val body = """{"login":"$login","password":"$password"}"""
                val response = postRaw("/auth/login", body)

                Log.d(TAG, "Login response: $response")

                if (response != null) {
                    val auth = json.decodeFromString<AuthResponse>(response)
                    token = auth.token
                    Log.d(TAG, "✅ Login successful, token: ${token.take(20)}...")
                    TransportResult.Success(token)
                } else {
                    TransportResult.Error("Invalid credentials")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                TransportResult.Error("Network error: ${e.message}", e)
            }
        }
    }

    // ========== СООБЩЕНИЯ ==========

    override suspend fun sendMessage(sender: String, receiver: String, text: String): TransportResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val body = """{"sender":"$sender","receiver":"$receiver","text":"$text"}"""
                if (_isConnected && webSocket != null) {
                    webSocket?.send(body)
                    TransportResult.Success(true)
                } else {
                    val response = postRaw("/messages", body)
                    TransportResult.Success(response != null)
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
                val response = get("/messages/read?sender=$sender")
                TransportResult.Success(response != null)
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
                val response = get("/users/search?q=$query&except=$currentUser")
                if (response != null) {
                    val dtos = json.decodeFromString<List<UserDto>>(response)
                    TransportResult.Success(dtos.map { it.toDomain() })
                } else {
                    TransportResult.Error("Search failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                TransportResult.Error("Network error", e)
            }
        }
    }

    // ========== WEBSOCKET ==========

    override fun onMessageReceived(callback: (Message) -> Unit) {
        messageCallback = callback
    }

    override fun connect() {
        if (token.isEmpty()) {
            Log.w(TAG, "Cannot connect: no token")
            return
        }

        val wsUrl = baseUrl.replace("http", "ws") + "/ws?token=$token"
        Log.d(TAG, "🔌 Connecting to WebSocket: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected = true
                Log.d(TAG, "✅ WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = json.decodeFromString<Message>(text)

                    // 1. Сохраняем в локальную SQLite
                    Repository.saveIncomingMessage(msg)

                    // 2. Отдаем в UI (чтобы обновить RecyclerView)
                    // Важно делать это в Main потоке, так как OkHttp вызывает onMessage в фоновом
                    CoroutineScope(Dispatchers.Main).launch {
                        messageCallback?.invoke(msg)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse WS message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected = false
                Log.e(TAG, "❌ WebSocket error: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected = false
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected = false
            }
        })
    }

    override fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected = false
    }

    override fun isConnected() = _isConnected

    // ========== HTTP HELPERS (с сырым JSON) ==========

    private fun postRaw(path: String, jsonBody: String): String? {
        return try {
            Log.d(TAG, "POST $path: $jsonBody")

            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()
            Log.d(TAG, "POST $path response: ${bodyString?.take(200)} (code: ${response.code})")

            if (response.isSuccessful && bodyString != null) {
                bodyString
            } else {
                Log.e(TAG, "HTTP ${response.code}: ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed: ${e.message}")
            null
        }
    }

    private fun get(path: String): String? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .get()
                .build()

            Log.d(TAG, "GET $path")

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()
            Log.d(TAG, "GET $path response: ${bodyString?.take(200)} (code: ${response.code})")

            if (response.isSuccessful && bodyString != null) {
                bodyString
            } else {
                Log.e(TAG, "HTTP ${response.code}: ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed: ${e.message}")
            null
        }
    }
}