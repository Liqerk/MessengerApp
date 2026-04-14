package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("messenger_session", Context.MODE_PRIVATE)

    // Обновленный метод для сохранения логина И токена вместе
    fun saveSession(login: String, token: String? = null) {
        val editor = prefs.edit()
        editor.putString("current_user", login)
        if (token != null && token.isNotBlank()) {
            editor.putString("auth_token", token)
        } else {
            editor.remove("auth_token")
        }
        editor.apply()
    }

    fun getLogin(): String? {
        return prefs.getString("current_user", null)
    }
    fun isLoggedIn(): Boolean {
        val login = getLogin()
        val token = getToken()
        Log.e("SessionManager", "🚨 isLoggedIn: login=$login, hasToken=${token != null}")
        return login != null && token != null
    }
    // ЭТОТ МЕТОД НУЖЕН ДЛЯ ТЕРМИНАЛА (УБРАТЬ ЕГО - ОШИБКА)
    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    // Старый метод для совместимости (остальное можно удалить или оставить)
    fun saveLogin(login: String) {
        prefs.edit().putString("current_user", login).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }


}