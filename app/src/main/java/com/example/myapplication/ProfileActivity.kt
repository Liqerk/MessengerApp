package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat   // ← ЭТОТ импорт
import com.bumptech.glide.Glide
import com.example.myapplication.dpi.DpiManager
import java.io.File
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode

class ProfileActivity : AppCompatActivity() {

    companion object {
        const val VPN_REQUEST = 100
        const val PICK_AVATAR = 200
    }

    private lateinit var avatarImage: ImageView
    private lateinit var avatarLetter: TextView
    private lateinit var currentLogin: String
    private lateinit var dpiManager: DpiManager
    // ❌ УБРАЛИ: private lateinit var dpiSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        Repository.init(this)
        val session = SessionManager(this)
        currentLogin = session.getLogin() ?: ""

        avatarImage = findViewById(R.id.profile_avatar_image)
        avatarLetter = findViewById(R.id.profile_avatar)

        val avatarContainer: FrameLayout = findViewById(R.id.avatar_container)
        val loginText: TextView = findViewById(R.id.profile_login)
        val mailText: TextView = findViewById(R.id.profile_mail)
        val backButton: ImageButton = findViewById(R.id.back_button)
        val changeNameBtn: LinearLayout = findViewById(R.id.change_name_btn)
        val changePasswordBtn: LinearLayout = findViewById(R.id.change_password_btn)
        val favoritesBtn: LinearLayout = findViewById(R.id.favorites_btn)

        // === DPI ===
        dpiManager = DpiManager(this)
        val dpiSwitch = findViewById<SwitchCompat>(R.id.dpi_switch)  // ← SwitchCompat!
        dpiSwitch.isChecked = dpiManager.wasEnabled()

        dpiSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val vpnIntent = dpiManager.prepareVpn()
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, VPN_REQUEST)
                } else {
                    // Настройки ByeDPI для обхода YouTube
                    val prefs = getSharedPreferences("byedpi", MODE_PRIVATE).edit()
                    prefs.putString("byedpi_cmd_args", "-9 --split-pos 1 --disorder")
                    prefs.putString("byedpi_mode", "cmd")
                    prefs.apply()
                    dpiManager.start()
                }
            } else {
                dpiManager.stop()
            }
        }
        val modeSwitch = findViewById<SwitchCompat>(R.id.mode_switch)
        val serverUrlInput = findViewById<EditText>(R.id.server_url_input)

// Восстанавливаем режим
        modeSwitch.isChecked = TransportManager.getMode() == TransportMode.SERVER

        modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) TransportMode.SERVER else TransportMode.LOCAL
            val url = serverUrlInput.text.toString().ifEmpty { "http://192.168.0.35:8080" }

            TransportManager.switchMode(mode, url)

            Toast.makeText(
                this,
                if (isChecked) "🌐 Серверный режим" else "📱 Локальный режим",
                Toast.LENGTH_SHORT
            ).show()
        }
        // === UI ===
        loginText.text = currentLogin
        mailText.text = Repository.getUserMail(currentLogin)
        loadAvatar()

        backButton.setOnClickListener { finish() }

        avatarContainer.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_AVATAR)
        }

        favoritesBtn.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        changeNameBtn.setOnClickListener {
            val editText = EditText(this)
            editText.setText(currentLogin)
            editText.setSelection(currentLogin.length)
            val padding = (16 * resources.displayMetrics.density).toInt()
            editText.setPadding(padding, padding, padding, padding)

            AlertDialog.Builder(this)
                .setTitle("Изменить логин")
                .setView(editText)
                .setPositiveButton("Сохранить") { _, _ ->
                    val newLogin = editText.text.toString().trim()
                    if (newLogin.isEmpty() || newLogin == currentLogin) return@setPositiveButton
                    val success = Repository.changeLogin(currentLogin, newLogin)
                    if (success) {
                        session.saveLogin(newLogin)
                        currentLogin = newLogin
                        loginText.text = newLogin
                        avatarLetter.text = newLogin.first().uppercase()
                        Toast.makeText(this, "Логин изменён!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Логин уже занят!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        changePasswordBtn.setOnClickListener {
            val editText = EditText(this)
            editText.hint = "Новый пароль"
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            val padding = (16 * resources.displayMetrics.density).toInt()
            editText.setPadding(padding, padding, padding, padding)

            AlertDialog.Builder(this)
                .setTitle("Изменить пароль")
                .setView(editText)
                .setPositiveButton("Сохранить") { _, _ ->
                    val newPassword = editText.text.toString().trim()
                    if (newPassword.length < 4) {
                        Toast.makeText(this, "Минимум 4 символа!", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    Repository.changePassword(currentLogin, newPassword)
                    Toast.makeText(this, "Пароль изменён!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    // === AVATAR ===

    private fun loadAvatar() {
        val path = Repository.getAvatar(currentLogin)
        if (path != null && File(path).exists()) {
            avatarImage.visibility = View.VISIBLE
            avatarLetter.visibility = View.GONE
            Glide.with(this).load(File(path)).circleCrop().into(avatarImage)
        } else {
            avatarImage.visibility = View.GONE
            avatarLetter.visibility = View.VISIBLE
            avatarLetter.text = currentLogin.first().uppercase()
            val colors = intArrayOf(
                0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
                0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt()
            )
            avatarLetter.background.setTint(
                colors[kotlin.math.abs(currentLogin.hashCode()) % colors.size]
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // VPN разрешение
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                dpiManager.start()
            } else {
                findViewById<SwitchCompat>(R.id.dpi_switch).isChecked = false
            }
        }

        // Аватарка
        if (requestCode == PICK_AVATAR && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val path = saveAvatarFile(uri)
                if (path != null) {
                    Repository.setAvatar(currentLogin, path)
                    loadAvatar()
                    Toast.makeText(this, "Аватарка обновлена!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveAvatarFile(uri: Uri): String? {
        return try {
            val dir = File(filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "avatar_${currentLogin}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}