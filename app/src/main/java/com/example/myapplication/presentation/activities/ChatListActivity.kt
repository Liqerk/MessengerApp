package com.example.myapplication

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myapplication.transport.ServerTransport
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class ChatListActivity : AppCompatActivity() {

    private lateinit var currentUser: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: ChatRecyclerAdapter
    private lateinit var searchInput: EditText
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var connectionStatus: TextView

    private var isInSearchMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        Repository.init(this)
        val session = SessionManager(this)
        currentUser = session.getLogin() ?: ""
        val token = session.getToken() ?: ""

        Log.e("ChatListActivity", "🚨🚨🚨 ChatListActivity START")
        Log.e("ChatListActivity", "🚨 login: $currentUser, token: ${token.take(20)}...")

        // ✅ ВАЖНО: Подключаем WebSocket при старте!
        if (token.isNotEmpty() && TransportManager.getMode() == TransportMode.SERVER) {
            val transport = TransportManager.get()
            if (transport is ServerTransport) {
                transport.setToken(token, currentUser)

                // ✅ ПРИНУДИТЕЛЬНОЕ ПОДКЛЮЧЕНИЕ
                Log.e("ChatListActivity", "🚨 Force connecting WebSocket...")
                transport.connect()
            }
        }
        if (currentUser.isEmpty()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        recyclerView = findViewById(R.id.chat_recycler_view)
        emptyText = findViewById(R.id.empty_text)
        searchInput = findViewById(R.id.search_input)
        appBarLayout = findViewById(R.id.app_bar)
        connectionStatus = findViewById(R.id.connection_status)

        updateConnectionStatus()
        setupMyAvatar()
        setupRecyclerView()
        setupAppBar()
        setupSearch()
        setupThemeButton()
        setupLogoutButton()
    }

    override fun onResume() {
        super.onResume()
        loadChats()
        updateConnectionStatus()
        setupMyAvatar()
        appBarLayout.post { appBarLayout.setExpanded(false, false) }

        // --- НОВОЕ: Обработка события подключения и отправки ---
        TransportManager.onNetworkEvent = { event ->
            runOnUiThread {
                when (event) {
                    is TransportManager.NetworkEvent.PendingMessagesAvailable -> {
                        // Показываем сообщение внизу экрана (Snackbar)
                        Snackbar.make(recyclerView, "Найдено ${event.count} неотправленных сообщений. Отправить?", Snackbar.LENGTH_INDEFINITE)
                            .setAction("ОТПРАВИТЬ") {
                                MessageQueue.processPending()
                            }
                            .show()
                    }
                    else -> { }
                }
            }
        }
    }

    // ==================== UI INIT ====================

    private fun setupMyAvatar() {
        val myAvatarText: TextView = findViewById(R.id.my_avatar)
        val myAvatarImage: ImageView = findViewById(R.id.my_avatar_image)

        val avatarPath = Repository.getAvatar(currentUser)

        if (!avatarPath.isNullOrEmpty()) {
            val isHttp = avatarPath.startsWith("http://") || avatarPath.startsWith("https://")

            if (isHttp) {
                myAvatarImage.visibility = View.VISIBLE
                myAvatarText.visibility = View.GONE

                Glide.with(this)
                    .load(avatarPath)
                    .circleCrop()
                    .into(myAvatarImage)
            } else {
                val localFile = File(avatarPath)
                if (localFile.exists()) {
                    myAvatarImage.visibility = View.VISIBLE
                    myAvatarText.visibility = View.GONE

                    Glide.with(this)
                        .load(localFile)
                        .circleCrop()
                        .into(myAvatarImage)
                } else {
                    showMyAvatarLetter(myAvatarText, myAvatarImage)
                }
            }
        } else {
            showMyAvatarLetter(myAvatarText, myAvatarImage)
        }

        val avatarContainer = findViewById<FrameLayout>(R.id.my_avatar_container)
        avatarContainer.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }



    private fun showMyAvatarLetter(text: TextView, image: ImageView) {
        image.visibility = View.GONE
        text.visibility = View.VISIBLE
        text.text = currentUser.firstOrNull()?.uppercase() ?: "?"
        val colors = intArrayOf(
            0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt()
        )
        text.background.setTint(colors[abs(currentUser.hashCode()) % colors.size])
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatRecyclerAdapter(
            emptyList(),
            onClick = { chatItem -> openChat(chatItem.name) },
            onLongClick = { chatItem -> showChatOptions(chatItem) }
        )
        recyclerView.adapter = adapter
    }

    private fun setupAppBar() {
        appBarLayout.post {
            appBarLayout.setExpanded(false, false)
        }

        appBarLayout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                val totalRange = appBarLayout.totalScrollRange
                if (totalRange > 0 && abs(verticalOffset) == totalRange) {
                    if (searchInput.text.toString().isNotEmpty()) {
                        searchInput.text.clear()
                        isInSearchMode = false
                        loadChats()
                    }
                }
            }
        )
    }

    private fun setupSearch() {
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

                isInSearchMode = true

                if (query.startsWith("@")) {
                    val userQuery = query.removePrefix("@").trim()
                    if (userQuery.isNotEmpty()) {
                        searchUsers(userQuery)
                    } else {
                        loadChats()
                    }
                } else {
                    searchChatsAndMessages(query)
                }
            }
        })
    }

    private fun setupThemeButton() {
        val themeButton: ImageButton = findViewById(R.id.theme_button)
        themeButton.setOnClickListener {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    private fun setupLogoutButton() {
        val session = SessionManager(this)
        val logoutButton: ImageButton = findViewById(R.id.logout_button)

        logoutButton.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Выйти?")
                .setMessage("Вы уверены что хотите выйти из аккаунта?")
                .setPositiveButton("Выйти") { _, _ ->
                    TransportManager.get().disconnect()
                    SessionManager(this).logout()
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

    // ==================== НАВИГАЦИЯ ====================

    private fun openChat(chatWith: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatWith", chatWith)
        startActivity(intent)
    }

    // ==================== ПОИСК ПОЛЬЗОВАТЕЛЕЙ (@) ====================

    private fun searchUsers(query: String) {
        lifecycleScope.launch {
            val localUsers = Repository.searchUsers(query, currentUser)

            if (TransportManager.getMode() == TransportMode.SERVER) {
                val result = TransportManager.get().searchUsers(query, currentUser)
                when (result) {
                    is TransportResult.Success -> {
                        val merged = (localUsers + result.data)
                            .filter { it.login != currentUser }
                            .distinctBy { it.login }

                        val items = merged.map { user ->
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

                    is TransportResult.Error -> {
                        android.util.Log.e("SEARCH_USERS", "server error = ${result.message}")
                    }
                }
            }

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

    // ==================== ПОИСК ЧАТОВ И СООБЩЕНИЙ ====================

    private fun searchChatsAndMessages(query: String) {
        lifecycleScope.launch {
            val allChats = Repository.getChatList(currentUser)

            // 1. Поиск по имени чата / собеседника
            val chatsByName = allChats.filter {
                it.name.contains(query, ignoreCase = true)
            }.sortedWith(
                compareByDescending<ChatItem> { it.isPinned }
                    .thenByDescending { it.lastMessageTime }
            )

            // 2. Поиск по тексту последнего сообщения в чате
            val chatsByLastMessage = allChats.filter {
                it.lastMessage.contains(query, ignoreCase = true)
            }.sortedWith(
                compareByDescending<ChatItem> { it.isPinned }
                    .thenByDescending { it.lastMessageTime }
            )

            val mergedByChats = (chatsByName + chatsByLastMessage).distinctBy { it.name }

            if (mergedByChats.isNotEmpty()) {
                adapter.updateData(mergedByChats)
                updateEmptyState(false, "")
                return@launch
            }

            // 3. Если по чатам не нашли — ищем по всем сообщениям
            val results = Repository.searchGlobalMessages(currentUser, query)

            if (results.isEmpty()) {
                adapter.updateData(emptyList())
                updateEmptyState(true, "Ничего не найдено")
                return@launch
            }

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
            }.sortedByDescending { it.lastMessageTime }

            adapter.updateData(items)
            updateEmptyState(items.isEmpty(), "Ничего не найдено")
        }
    }

    // Оставляю старый метод, чтобы ничего не ломать, но теперь он просто вызывает улучшенный поиск
    private fun searchMessages(query: String) {
        searchChatsAndMessages(query)
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
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

}