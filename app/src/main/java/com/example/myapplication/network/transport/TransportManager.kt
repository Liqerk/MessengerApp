package com.example.myapplication.transport

import android.content.Context
import android.util.Log
import com.example.myapplication.MessageQueue
import com.example.myapplication.Repository
import com.example.myapplication.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

enum class TransportMode {
    LOCAL,
    SERVER,
    BRIAR
}

object TransportManager {

    private const val TAG = "TransportManager"
    private const val PREF_NAME = "transport_settings"
    private const val KEY_MODE = "transport_mode"
    private const val KEY_SERVER_URL = "server_url"

    private var current: MessageTransport? = null
    private var mode: TransportMode = TransportMode.LOCAL
    private var appContext: Context? = null

    // 🔴 ДОБАВЛЕНО: Свойство для коллбека состояния подключения
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    // Коллбек для уведомлений о событиях сети
    var onNetworkEvent: ((NetworkEvent) -> Unit)? = null

    fun init(context: Context, mode: TransportMode? = null) {
        this.appContext = context.applicationContext
        val actualMode = mode ?: getSavedMode(context)
        this.mode = actualMode

        current = when (actualMode) {
            TransportMode.LOCAL -> {
                Log.d(TAG, "📱 Using LOCAL transport")
                LocalTransport()
            }

            TransportMode.SERVER -> {
                val url = getSavedServerUrl(context)
                Log.d(TAG, "🌐 Using SERVER transport: $url")

                val transport = ServerTransport(url)

                // Загрузка сохраненного токена при старте приложения
                val session = SessionManager(context)
                val login = session.getLogin()
                val token = session.getToken()
                if (!login.isNullOrEmpty() && !token.isNullOrEmpty()) {
                    transport.setToken(token, login)
                    Log.d(TAG, "✅ Loaded saved token for user: $login")

                    // ✅ ВАЖНО: ПОДКЛЮЧАЕМ WEBSOCKET!
                    transport.connect()
                    Log.d(TAG, "🚨 WebSocket connect() called from TransportManager.init()")
                }

                transport.apply {
                    onConnectionStateChange = { connected ->
                        TransportManager.onConnectionStateChanged?.invoke(connected)

                        if (connected) {
                            val pendingCount: Int = MessageQueue.getPendingCount()
                            if (pendingCount > 0) {
                                onNetworkEvent?.invoke(NetworkEvent.PendingMessagesAvailable(pendingCount))
                            }

                            val user = SessionManager(context).getLogin()
                            if (!user.isNullOrEmpty()) {
                                MessageQueue.loadPendingFromDb(user)
                                MessageQueue.processPending()
                            }
                        }
                    }
                }

                transport  // ← возвращаем transport
            }

            TransportMode.BRIAR -> {
                Log.d(TAG, "🔒 BRIAR not implemented, using LOCAL")
                LocalTransport()
            }
        }

        saveMode(context, actualMode)
    }

    fun get(): MessageTransport {
        if (current == null) {
            Log.w(TAG, "⚠️ TransportManager not initialized, using LOCAL")
            current = LocalTransport()
        }
        return current!!
    }

    fun getMode(): TransportMode = mode

    fun switchMode(newMode: TransportMode, serverUrl: String = "http://192.168.0.35:8080") {
        val ctx = appContext ?: return
        if (mode == newMode) return

        current?.disconnect()
        mode = newMode

        current = when (newMode) {
            TransportMode.LOCAL -> {
                LocalTransport()  // ← ДОБАВИТЬ ЭТУ ВЕТКУ!
            }

            TransportMode.SERVER -> {
                saveServerUrl(ctx, serverUrl)
                val transport = ServerTransport(serverUrl)

                val session = SessionManager(ctx)
                val login = session.getLogin()
                val token = session.getToken()
                if (!login.isNullOrEmpty() && !token.isNullOrEmpty()) {
                    transport.setToken(token, login)
                    transport.connect()
                }

                transport.apply {
                    onConnectionStateChange = { connected ->
                        TransportManager.onConnectionStateChanged?.invoke(connected)

                        if (connected) {
                            val pendingCount: Int = MessageQueue.getPendingCount()
                            if (pendingCount > 0) {
                                onNetworkEvent?.invoke(
                                    NetworkEvent.PendingMessagesAvailable(pendingCount)
                                )
                            }

                            val user = SessionManager(ctx).getLogin()
                            if (!user.isNullOrEmpty()) {
                                MessageQueue.loadPendingFromDb(user)
                                MessageQueue.processPending()
                            }
                        }
                    }
                }
                transport
            }

            TransportMode.BRIAR -> {
                LocalTransport()  // ← ДОБАВИТЬ ЭТУ ВЕТКУ!
            }
        }

        saveMode(ctx, newMode)
    }

    private fun saveMode(context: Context, mode: TransportMode) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    private fun getSavedMode(context: Context): TransportMode {
        val name = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, TransportMode.LOCAL.name)
        return try {
            TransportMode.valueOf(name ?: TransportMode.LOCAL.name)
        } catch (e: Exception) {
            TransportMode.LOCAL
        }
    }

    private fun saveServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, url)
            .apply()
    }

    private fun getSavedServerUrl(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_URL, "http://192.168.0.35:8080")
            ?: "http://192.168.0.35:8080"
    }

    // Класс для событий сети
    sealed class NetworkEvent {
        data class PendingMessagesAvailable(val count: Int) : NetworkEvent()
    }

}