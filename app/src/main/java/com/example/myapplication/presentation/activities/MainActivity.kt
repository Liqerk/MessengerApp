package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.transport.ServerTransport
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Repository.init(this)
        val session = SessionManager(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ✅ ИСПРАВЛЕНО: Проверяем и логин и токен
        if (session.getLogin() != null && session.getToken() != null) {
            // ✅ Восстанавливаем токен в Transport
            if (TransportManager.getMode() == TransportMode.SERVER) {
                val transport = TransportManager.get()
                if (transport is ServerTransport) {
                    transport.setToken(session.getToken()!!, session.getLogin()!!)
                    transport.connect()
                    Log.e("MainActivity", "🚨 Restored session and WebSocket")
                }
            }

            startActivity(Intent(this, ChatListActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val userLogin: EditText = findViewById(R.id.user_login)
        val userMail: EditText = findViewById(R.id.editTextTextEmailAddress)
        val userPassword: EditText = findViewById(R.id.editTextTextPassword)
        val button: Button = findViewById(R.id.button_reg)
        val linkToLog: TextView = findViewById(R.id.linkToLog)

        linkToLog.setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        button.setOnClickListener {
            val login = userLogin.text.toString().trim()
            val mail = userMail.text.toString().trim()
            val password = userPassword.text.toString().trim()

            if (login.isEmpty() || password.isEmpty() || mail.isEmpty()) {
                Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 4) {
                Toast.makeText(this, "Пароль минимум 4 символа!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Используем Transport
            lifecycleScope.launch {
                val result = TransportManager.get().register(login, mail, password)

                when (result) {
                    is TransportResult.Success -> {
                        val token = result.data  // ✅ Получаем токен после регистрации

                        Log.e("MainActivity", "🚨 Register success! Token: ${token.take(20)}...")

                        // ✅ СОХРАНЯЕМ СЕССИЮ
                        val sessionManager = SessionManager(this@MainActivity)
                        sessionManager.saveSession(login, token)

                        // ✅ УСТАНАВЛИВАЕМ ТОКЕН В TRANSPORT
                        if (TransportManager.getMode() == TransportMode.SERVER) {
                            val transport = TransportManager.get()
                            if (transport is ServerTransport) {
                                transport.setToken(token, login)
                                transport.connect()
                                Log.e("MainActivity", "🚨 WebSocket connected after registration")
                            }
                        }

                        Toast.makeText(this@MainActivity, "Аккаунт создан!", Toast.LENGTH_SHORT).show()

                        // ✅ СРАЗУ ПЕРЕХОДИМ К ЧАТАМ (минуя AuthActivity)
                        val intent = Intent(this@MainActivity, ChatListActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    is TransportResult.Error -> {
                        Log.e("MainActivity", "❌ Register failed: ${result.message}")
                        Toast.makeText(this@MainActivity, "Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}