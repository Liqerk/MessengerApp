package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("messenger_session", Context.MODE_PRIVATE)

    fun saveLogin(login: String) {
        prefs.edit().putString("current_user", login).apply()
    }

    fun getLogin(): String? {
        return prefs.getString("current_user", null)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return getLogin() != null
    }
}