package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
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
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myapplication.dpi.DpiManager
import java.io.File
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ProfileActivity : AppCompatActivity() {

    companion object {
        // ✅ ДОБАВЛЕНО ЭТОТ КОНСТАНТЫ
        const val VPN_REQUEST = 100
        const val PICK_AVATAR = 200
    }

    private lateinit var avatarImage: ImageView
    private lateinit var avatarLetter: TextView
    private lateinit var currentLogin: String
    private lateinit var dpiManager: DpiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        Repository.init(this)
        val session = SessionManager(this)
        currentLogin = session.getLogin() ?: ""

        // Инициализация элементов
        avatarImage = findViewById(R.id.profile_avatar_image)
        avatarLetter = findViewById(R.id.profile_avatar)

        val avatarContainer: FrameLayout = findViewById(R.id.avatar_container)
        val loginText: TextView = findViewById(R.id.profile_login)
        val mailText: TextView = findViewById(R.id.profile_mail)
        val backButton: ImageButton = findViewById(R.id.back_button)
        val changeNameBtn: LinearLayout = findViewById(R.id.change_name_btn)
        val changePasswordBtn: LinearLayout = findViewById(R.id.change_password_btn)
        val favoritesBtn: LinearLayout = findViewById(R.id.favorites_btn)

        // DPI Switch Logic
        dpiManager = DpiManager(this)
        val dpiSwitch = findViewById<SwitchCompat>(R.id.dpi_switch)
        dpiSwitch.isChecked = dpiManager.wasEnabled()

        dpiSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val vpnIntent = dpiManager.prepareVpn()
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, VPN_REQUEST) // Теперь работает
                } else {
                    val prefs = getSharedPreferences("byedpi", MODE_PRIVATE).edit()
                    prefs.putString("byedpi_cmd_args","--disorder 1 --split-pos 5 --split-at-host --drop-sack --no-domain --tcp-fast-open --host-mixed-case --remove-spaces --split-tls-record --split-tls-record-pos 5 --split-at-sni --udp-fake-count 3")
                    prefs.putString("byedpi_mode", "cmd")
                    prefs.apply()
                    dpiManager.start()
                    Toast.makeText(this, "ByeDPI включен", Toast.LENGTH_SHORT).show()
                }
            } else {
                dpiManager.stop()
                Toast.makeText(this, "ByeDPI выключен", Toast.LENGTH_SHORT).show()
            }
        }

        // Режим сервера
        val modeSwitch = findViewById<SwitchCompat>(R.id.mode_switch)
        val serverUrlInput = findViewById<EditText>(R.id.server_url_input)

        modeSwitch.isChecked = TransportManager.getMode() == TransportMode.SERVER

        modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) TransportMode.SERVER else TransportMode.LOCAL
            val url = serverUrlInput.text.toString().ifEmpty { "http://192.168.0.35:8080" }
            TransportManager.switchMode(mode, url)
            Toast.makeText(this, if (isChecked) "🌐 Серверный режим" else "📱 Локальный режим", Toast.LENGTH_SHORT).show()
        }

        // Загрузка данных
        loginText.text = currentLogin
        mailText.text = Repository.getUserMail(currentLogin)
        loadAvatar()

        // События
        backButton.setOnClickListener { finish() }
        avatarContainer.setOnClickListener { pickImageFromGallery() }
        favoritesBtn.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }

        // Смена логина
        changeNameBtn.setOnClickListener {
            val editText = EditText(this)
            editText.setText(currentLogin)
            editText.setSelection(currentLogin.length)
            editText.setPadding((16 * resources.displayMetrics.density).toInt(), 0, 0, 0)

            AlertDialog.Builder(this)
                .setTitle("Изменить логин")
                .setView(editText)
                .setPositiveButton("Сохранить") { _, _ ->
                    val newLogin = editText.text.toString().trim()
                    if (newLogin.isNotEmpty() && newLogin != currentLogin) {
                        if (Repository.changeLogin(currentLogin, newLogin)) {
                            session.saveLogin(newLogin)
                            currentLogin = newLogin
                            loginText.text = newLogin
                            avatarLetter.text = newLogin.first().uppercase()
                            loadAvatar()
                            Toast.makeText(this, "Логин изменён!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Логин уже занят!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Отмена", null).show()
        }

        // Смена пароля
        changePasswordBtn.setOnClickListener {
            val editText = EditText(this)
            editText.hint = "Новый пароль"
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            editText.setPadding((16 * resources.displayMetrics.density).toInt(), 0, 0, 0)

            AlertDialog.Builder(this)
                .setTitle("Изменить пароль")
                .setView(editText)
                .setPositiveButton("Сохранить") { _, _ ->
                    val newPassword = editText.text.toString().trim()
                    if (newPassword.length >= 4) {
                        Repository.changePassword(currentLogin, newPassword)
                        Toast.makeText(this, "Пароль изменён!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Минимум 4 символа!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Отмена", null).show()
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_AVATAR)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Выбор аватара
        if (requestCode == PICK_AVATAR && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val path = saveAvatarFile(uri)
                if (path != null) {
                    Toast.makeText(this, "Загрузка на сервер...", Toast.LENGTH_SHORT).show()

                    lifecycleScope.launch(Dispatchers.IO) {
                        val file = File(path)
                        // Отправляем файл на сервер (или локально, смотря какой режим)
                        val result = TransportManager.get().uploadFile(file)

                        withContext(Dispatchers.Main) {
                            when (result) {
                                is TransportResult.Success -> {
                                    val finalUrl = result.data // Это либо http://... либо локальный путь

                                    // 1. Принудительно создаем юзера в локальной БД
                                    Repository.ensureUserExists(currentLogin)
                                    // 2. Сохраняем URL
                                    Repository.setAvatar(currentLogin, finalUrl)

                                    loadAvatar()
                                    Toast.makeText(this@ProfileActivity, "Аватарка обновлена!", Toast.LENGTH_SHORT).show()
                                }
                                is TransportResult.Error -> {
                                    Toast.makeText(this@ProfileActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Разрешение на VPN
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                dpiManager.start()
            } else {
                // Если пользователь отклонил доступ к VPN — выключаем галочку
                runOnUiThread {
                    val switchView = findViewById<SwitchCompat>(R.id.dpi_switch)
                    if (switchView.isChecked) switchView.isChecked = false
                }
            }
        }
    }

    // Исправленная функция сохранения файла с уникальным именем
    private fun saveAvatarFile(uri: Uri): String? {
        return try {
            val dir = File(filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()

            // Генерируем уникальное имя на основе времени, чтобы избежать конфликтов
            val fileName = "avatar_${currentLogin}_${System.currentTimeMillis()}.jpg"
            val file = File(dir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadAvatar() {
        val path = Repository.getAvatar(currentLogin)

        if (!path.isNullOrEmpty()) {
            val isHttp = path.startsWith("http://") || path.startsWith("https://")

            // ✅ Если HTTP — грузим сразу, если локальный — проверяем существование файла
            if (isHttp) {
                // HTTP-аватарка
                avatarImage.visibility = View.VISIBLE
                avatarLetter.visibility = View.GONE

                Glide.with(this)
                    .load(path) // Загружаем напрямую URL
                    .centerCrop()
                    .circleCrop()
                    .into(avatarImage)
            } else {
                // Локальный файл
                val localFile = File(path)
                if (localFile.exists()) {
                    avatarImage.visibility = View.VISIBLE
                    avatarLetter.visibility = View.GONE

                    Glide.with(this)
                        .load(localFile)
                        .centerCrop()
                        .circleCrop()
                        .into(avatarImage)
                } else {
                    showAvatarLetter()
                }
            }
        } else {
            showAvatarLetter()
        }
    }


    private fun showAvatarLetter() {
        avatarImage.visibility = View.GONE
        avatarLetter.visibility = View.VISIBLE
        avatarLetter.text = currentLogin.firstOrNull()?.uppercase() ?: "?"
        val colors = intArrayOf(
            0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt()
        )
        avatarLetter.background.setTint(colors[abs(currentLogin.hashCode()) % colors.size])
    }
}