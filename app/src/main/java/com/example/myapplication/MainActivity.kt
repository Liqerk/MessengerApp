package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Repository.init(this)
        val session = SessionManager(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Если уже залогинен — сразу к чатам
        if (session.isLoggedIn()) {
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
                        Toast.makeText(this@MainActivity, "Аккаунт создан!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                        finish()
                    }
                    is TransportResult.Error -> {
                        Toast.makeText(this@MainActivity, "Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}