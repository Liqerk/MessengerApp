package com.example.myapplication.transport

import android.content.Context
import android.util.Log

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
                ServerTransport(url)
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
        val ctx = appContext
        if (ctx == null) {
            Log.e(TAG, "❌ Context is null, cannot switch mode")
            return
        }

        if (mode == newMode) return

        Log.d(TAG, "🔄 Switching from $mode to $newMode")

        current?.disconnect()

        mode = newMode
        current = when (newMode) {
            TransportMode.LOCAL -> LocalTransport()
            TransportMode.SERVER -> {
                saveServerUrl(ctx, serverUrl)
                ServerTransport(serverUrl)
            }
            TransportMode.BRIAR -> LocalTransport()
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
            .getString(KEY_SERVER_URL, "http://192.168.0.35:8080") ?: "http://192.168.0.35:8080"
    }
}