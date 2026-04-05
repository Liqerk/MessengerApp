package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        Repository.init(this)

        val userLogin: EditText = findViewById(R.id.login_input)
        val userPassword: EditText = findViewById(R.id.password_input)
        val buttonLogin: Button = findViewById(R.id.button_login)
        val linkToReg: TextView = findViewById(R.id.linkToReg)

        linkToReg.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        buttonLogin.setOnClickListener {
            val login = userLogin.text.toString().trim()
            val password = userPassword.text.toString().trim()

            if (login.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val result = TransportManager.get().login(login, password)

                when (result) {
                    is TransportResult.Success -> {
                        SessionManager(this@AuthActivity).saveLogin(login)
                        Toast.makeText(this@AuthActivity, "Добро пожаловать!", Toast.LENGTH_SHORT).show()

                        // Подключаем WebSocket после логина
                        TransportManager.get().connect()

                        val intent = Intent(this@AuthActivity, ChatListActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    is TransportResult.Error -> {
                        Toast.makeText(this@AuthActivity, "Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}