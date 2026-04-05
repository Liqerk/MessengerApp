package com.example.myapplication

import android.view.View
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.launch
import java.io.File

class ChatListActivity : AppCompatActivity() {

    private lateinit var currentUser: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: ChatRecyclerAdapter
    private lateinit var searchInput: EditText
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var connectionStatus: TextView

    // Состояние поиска
    private var isInSearchMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        Repository.init(this)
        val session = SessionManager(this)
        currentUser = session.getLogin() ?: ""

        if (currentUser.isEmpty()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // Инициализация UI
        recyclerView = findViewById(R.id.chat_recycler_view)
        emptyText = findViewById(R.id.empty_text)
        searchInput = findViewById(R.id.search_input)
        appBarLayout = findViewById(R.id.app_bar)
        connectionStatus = findViewById(R.id.connection_status)

        // Обновляем статус подключения
        updateConnectionStatus()

        // Аватар пользователя
        val myAvatarText: TextView = findViewById(R.id.my_avatar)
        val myAvatarImage: ImageView = findViewById(R.id.my_avatar_image)

        val avatarPath = Repository.getAvatar(currentUser)
        if (avatarPath != null && File(avatarPath).exists()) {
            myAvatarImage.visibility = View.VISIBLE
            myAvatarText.visibility = View.GONE
            Glide.with(this).load(File(avatarPath)).circleCrop().into(myAvatarImage)
        } else {
            myAvatarImage.visibility = View.GONE
            myAvatarText.visibility = View.VISIBLE
            myAvatarText.text = currentUser.first().uppercase()
            val colors = intArrayOf(
                0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
                0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt()
            )
            myAvatarText.background.setTint(colors[Math.abs(currentUser.hashCode()) % colors.size])
        }

        // Открытие профиля
        val avatarContainer = findViewById<FrameLayout>(R.id.my_avatar_container)
        avatarContainer.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Настройка RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatRecyclerAdapter(
            emptyList(),
            onClick = { chatItem ->
                openChat(chatItem.name)
            },
            onLongClick = { chatItem ->
                showChatOptions(chatItem)
            }
        )
        recyclerView.adapter = adapter

        // Сворачивание AppBar при старте
        appBarLayout.post {
            appBarLayout.setExpanded(false, false)
        }

        // Очистка поиска при сворачивании AppBar
        appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                val totalRange = appBarLayout.totalScrollRange
                if (totalRange > 0 && Math.abs(verticalOffset) == totalRange) {
                    if (searchInput.text.toString().isNotEmpty()) {
                        searchInput.text.clear()
                        isInSearchMode = false
                        loadChats()
                    }
                }
            }
        )

        // Живой поиск
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                if (query.isEmpty()) {
                    isInSearchMode = false
                    loadChats()
                    return
                }

                // Поиск пользователей через @
                if (query.startsWith("@")) {
                    val userQuery = query.removePrefix("@").trim()
                    if (userQuery.isNotEmpty()) {
                        searchUsers(userQuery)
                    } else {
                        loadChats()
                    }
                }
                // Поиск сообщений
                else if (query.length >= 2) {
                    searchMessages(query)
                }
            }
        })

        // Тема
        val themeButton: ImageButton = findViewById(R.id.theme_button)
        themeButton.setOnClickListener {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        // Выход
        val logoutButton: ImageButton = findViewById(R.id.logout_button)
        logoutButton.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Выйти?")
                .setMessage("Вы уверены что хотите выйти из аккаунта?")
                .setPositiveButton("Выйти") { _, _ ->
                    session.logout()
                    Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadChats()
        updateConnectionStatus()
        appBarLayout.post {
            appBarLayout.setExpanded(false, false)
        }
    }

    // ==================== НАВИГАЦИЯ ====================

    private fun openChat(chatWith: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatWith", chatWith)
        startActivity(intent)
    }

    // ==================== ПОИСК ПОЛЬЗОВАТЕЛЕЙ (@) ====================

    private fun searchUsers(query: String) {
        lifecycleScope.launch {
            // Сначала локальный поиск
            val localUsers = Repository.searchUsers(query, currentUser)

            // Потом серверный если подключен
            if (TransportManager.getMode() == TransportMode.SERVER) {
                val result = TransportManager.get().searchUsers(query, currentUser)
                when (result) {
                    is TransportResult.Success -> {
                        val items = result.data
                            .filter { it.login != currentUser }
                            .map { user ->
                                ChatItem(
                                    id = user.id,
                                    name = user.login,
                                    image = "",
                                    lastMessage = if (user.isOnline) "🟢 В сети" else "⬜ Не в сети",
                                    lastMessageTime = "",
                                    unreadCount = 0,
                                    isOnline = user.isOnline
                                )
                            }
                        adapter.updateData(items)
                        updateEmptyState(items.isEmpty(), "Пользователи не найдены")
                        return@launch
                    }
                    else -> { /* continue with local */ }
                }
            }

            // Показываем локальных
            val items = localUsers.map { user ->
                ChatItem(
                    id = user.id,
                    name = user.login,
                    image = "",
                    lastMessage = "⬜ Локальный пользователь",
                    lastMessageTime = "",
                    unreadCount = 0,
                    isOnline = false
                )
            }
            adapter.updateData(items)
            updateEmptyState(items.isEmpty(), "Пользователи не найдены")
        }
    }

    // ==================== ПОИСК СООБЩЕНИЙ ====================

    private fun searchMessages(query: String) {
        lifecycleScope.launch {
            val results = Repository.searchGlobalMessages(currentUser, query)

            if (results.isEmpty()) {
                adapter.updateData(emptyList())
                updateEmptyState(true, "Ничего не найдено")
                return@launch
            }

            // Группируем по собеседнику
            val grouped = results.groupBy { msg ->
                if (msg.sender == currentUser) msg.receiver else msg.sender
            }

            val items = grouped.map { (chatWith, messages) ->
                val lastMsg = messages.first()
                ChatItem(
                    id = 0,
                    name = chatWith,
                    image = "",
                    lastMessage = "💬 ${messages.size} совпадений",
                    lastMessageTime = lastMsg.timestamp,
                    unreadCount = messages.size,
                    isOnline = false
                )
            }

            adapter.updateData(items)
            updateEmptyState(items.isEmpty(), "Ничего не найдено")
        }
    }

    // ==================== ЗАГРУЗКА ЧАТОВ ====================

    private fun loadChats() {
        lifecycleScope.launch {
            val chatItems = Repository.getChatList(currentUser)

            val sortedChats = chatItems.sortedWith(
                compareByDescending<ChatItem> { it.isPinned }
                    .thenByDescending { it.lastMessageTime }
            )

            adapter.updateData(sortedChats)
            updateEmptyState(
                sortedChats.isEmpty(),
                "Нет чатов\nВведите @ и имя пользователя для поиска"
            )

            if (TransportManager.getMode() == TransportMode.SERVER) {
                SyncManager.syncChats(currentUser)
            }
        }
    }

    // ==================== СТАТУС ПОДКЛЮЧЕНИЯ ====================

    private fun updateConnectionStatus() {
        if (::connectionStatus.isInitialized) {
            val mode = TransportManager.getMode()
            connectionStatus.text = when (mode) {
                TransportMode.LOCAL -> "📱 Локально"
                TransportMode.SERVER -> "🌐 Онлайн"
                TransportMode.BRIAR -> "🔒 Briar"
            }
        }
    }

    // ==================== ОПЦИИ ЧАТА ====================

    private fun showChatOptions(chatItem: ChatItem) {
        val isPinned = Repository.isChatPinned(currentUser, chatItem.name)
        val pinText = if (isPinned) "📌 Открепить" else "📌 Закрепить"

        android.app.AlertDialog.Builder(this)
            .setTitle(chatItem.name)
            .setItems(arrayOf(pinText, "🗑️ Удалить чат")) { _, which ->
                when (which) {
                    0 -> {
                        Repository.togglePinChat(currentUser, chatItem.name)
                        loadChats()
                        Toast.makeText(
                            this,
                            if (!isPinned) "📌 Закреплено" else "Откреплено",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Удалить чат?")
                            .setMessage("Все сообщения с ${chatItem.name} будут удалены")
                            .setPositiveButton("Удалить") { _, _ ->
                                Repository.deleteChat(currentUser, chatItem.name)
                                loadChats()
                                Toast.makeText(this, "Чат удалён", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Отмена", null)
                            .show()
                    }
                }
            }
            .show()
    }

    // ==================== UI HELPERS ====================

    private fun updateEmptyState(isEmpty: Boolean, text: String) {
        if (isEmpty) {
            emptyText.text = text
            emptyText.visibility = TextView.VISIBLE
            recyclerView.visibility = RecyclerView.GONE
        } else {
            emptyText.visibility = TextView.GONE
            recyclerView.visibility = RecyclerView.VISIBLE
        }
    }
}