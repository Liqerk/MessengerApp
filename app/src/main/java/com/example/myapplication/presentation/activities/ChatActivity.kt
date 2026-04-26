package com.example.myapplication


import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.transport.ServerTransport
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class ChatActivity : AppCompatActivity(), MessageRecyclerAdapter.MessageActionListener {
    companion object {
        private const val REQUEST_CODE_CAMERA_PERMISSION = 200
    }
    private var isLoadingMessages = false  // ✅ ДОБАВЛЕНО
    private var lastLoadedMessageId = -1
    private lateinit var currentUser: String
    private lateinit var chatWith: String
    private var searchResults = mutableListOf<Int>()
    private var currentSearchIndex = -1
    private lateinit var searchPanel: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchCount: TextView
    private lateinit var searchUpBtn: ImageButton
    private lateinit var searchDownBtn: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageRecyclerAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceButton: ImageButton
    private lateinit var attachButton: ImageButton

    private lateinit var inputPanel: LinearLayout
    private lateinit var recordingPanel: LinearLayout
    private lateinit var recordingTimer: TextView
    private lateinit var recordingDot: View
    private lateinit var recordingHint: TextView

    private lateinit var statusText: TextView

    // Мультивыбор
    private lateinit var selectionPanel: LinearLayout
    private lateinit var normalToolbar: LinearLayout
    private lateinit var selectionCount: TextView

    // Reply
    private lateinit var replyPanel: LinearLayout
    private lateinit var replySenderName: TextView
    private lateinit var replyPreviewText: TextView
    private var replyToMessage: Message? = null

    // Typing
    private var typingJob: Job? = null
    private var isTypingSent = false

    // Запись голоса
    private var voiceTouchStartY = 0f
    private val SWIPE_UP_THRESHOLD_DP = 80
    private var recordingStartTime: Long = 0
    private var recordingDurationSeconds: Int = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsedMillis = System.currentTimeMillis() - recordingStartTime
            val seconds = (elapsedMillis / 1000).toInt()
            recordingTimer.text = String.format("%d:%02d", seconds / 60, seconds % 60)
            timerHandler.postDelayed(this, 500)
        }
    }

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var recordingCancelled = false
    private var dotAnimation: Animation? = null

    private var currentPhotoPath: String? = null
    private val REQUEST_CODE_PICK_IMAGE = 100
    private val REQUEST_CODE_CAMERA = 101

    // ==========================================
    // MessageActionListener
    // ==========================================

    override fun onDeleteMessages(messages: List<Message>) {
        AlertDialog.Builder(this)
            .setTitle("Удалить ${messages.size} сообщений?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    for (msg in messages) {
                        // 1. Удаляем локально
                        Repository.deleteMessage(msg.id)

                        // 2. Отправляем на сервер
                        if (TransportManager.getMode() == TransportMode.SERVER) {
                            val transport = TransportManager.get() as ServerTransport
                            // ✅ Отправляем clientMessageId (не локальный id!)
                            transport.deleteMessage(
                                messageId = msg.id,  // сервер проигнорирует
                                clientMessageId = msg.clientMessageId  // ← ВАЖНО!
                            )
                        }
                    }
                    withContext(Dispatchers.Main) {
                        adapter.exitSelectionMode()
                        loadMessages()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onEditMessage(message: Message) {
        if (message.type != "text") {
            Toast.makeText(this, "Только текст", Toast.LENGTH_SHORT).show()
            return
        }
        val editText = EditText(this)
        editText.setText(message.text)
        editText.setSelection(message.text.length)
        AlertDialog.Builder(this)
            .setTitle("Редактировать")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    Repository.editMessage(message.id, newText)
                    adapter.exitSelectionMode()
                    loadMessages()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onForwardMessages(messages: List<Message>) {
        val users = Repository.getAllUsers(currentUser)
        if (users.isEmpty()) {
            Toast.makeText(this, "Нет пользователей", Toast.LENGTH_SHORT).show()
            return
        }
        val names = users.map { it.login }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Переслать ${messages.size} сообщений")
            .setItems(names) { _, which ->
                val target = names[which]
                lifecycleScope.launch {
                    for (msg in messages) {
                        val fwd = "↩ Переслано:\n${msg.text}"
                        SyncManager.sendMessage(currentUser, target, fwd)
                    }
                }
                Toast.makeText(this, "Переслано → $target", Toast.LENGTH_SHORT).show()
                adapter.exitSelectionMode()
            }.show()
    }

    override fun onReplyMessage(message: Message) {
        adapter.exitSelectionMode()
        replyToMessage = message
        replySenderName.text = message.sender
        replyPreviewText.text = message.text.take(50)
        replyPanel.visibility = View.VISIBLE
        messageInput.requestFocus()
    }

    override fun onToggleFavorite(messages: List<Message>) {
        var added = 0
        messages.forEach { if (Repository.toggleFavorite(it.id)) added++ }
        adapter.exitSelectionMode()
        loadMessages()
        Toast.makeText(this, "⭐ $added в избранное", Toast.LENGTH_SHORT).show()
    }

    override fun onSelectionChanged(count: Int) {
        if (count > 0) {
            selectionPanel.visibility = View.VISIBLE
            normalToolbar.visibility = View.GONE
            selectionCount.text = count.toString()
        } else {
            selectionPanel.visibility = View.GONE
            normalToolbar.visibility = View.VISIBLE
        }
    }

    // ==========================================
    // onCreate
    // ==========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        Repository.init(this)
        currentUser = SessionManager(this).getLogin() ?: ""
        chatWith = intent.getStringExtra("chatWith") ?: ""

        initViews()
        setupRecyclerView()
        setupToolbar()
        setupInputPanel()
        setupWebSocketCallbacks()
        setupAudioPlayer()

        loadMessages()
        markMessagesAsRead()
    }

    private fun initViews() {
        val titleText: TextView = findViewById(R.id.chat_title)
        val avatarText: TextView = findViewById(R.id.chat_avatar)

        normalToolbar = findViewById(R.id.normal_toolbar)
        selectionPanel = findViewById(R.id.selection_panel)
        selectionCount = findViewById(R.id.selection_count)

        sendButton = findViewById(R.id.send_button)
        voiceButton = findViewById(R.id.voice_button)
        attachButton = findViewById(R.id.attach_button)
        recyclerView = findViewById(R.id.messages_recycler)
        messageInput = findViewById(R.id.message_input)

        inputPanel = findViewById(R.id.input_panel)
        recordingPanel = findViewById(R.id.recording_panel)
        recordingTimer = findViewById(R.id.recording_timer)
        recordingDot = findViewById(R.id.recording_dot)
        recordingHint = findViewById(R.id.recording_hint)

        replyPanel = findViewById(R.id.reply_panel)
        replySenderName = findViewById(R.id.reply_sender_name)
        replyPreviewText = findViewById(R.id.reply_preview_text)

        statusText = findViewById(R.id.chat_status)

        // Заголовок
        titleText.text = chatWith
        avatarText.text = chatWith.first().uppercase()
        val colors = intArrayOf(
            0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt()
        )
        avatarText.background.setTint(colors[abs(chatWith.hashCode()) % colors.size])

        updateChatStatus()
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        adapter = MessageRecyclerAdapter(emptyList(), currentUser, this)
        recyclerView.adapter = adapter

        setupDragToSelect()
        setupSwipeToReply()
    }

    private fun setupToolbar() {
        val backButton: ImageButton = findViewById(R.id.back_button)
        // В initViews() или setupToolbar()
        searchCount = findViewById(R.id.search_count)
        searchPanel = findViewById(R.id.search_panel)
        searchInput = findViewById(R.id.search_messages_input)
        searchUpBtn = findViewById(R.id.search_up_btn)
        searchDownBtn = findViewById(R.id.search_down_btn)

        val searchBtn: ImageButton = findViewById(R.id.search_messages_btn)
        val closeSearchBtn: ImageButton = findViewById(R.id.close_search_btn)



// Открыть поиск
        searchBtn.setOnClickListener {
            searchPanel.visibility = View.VISIBLE
            searchInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

// Закрыть поиск
        closeSearchBtn.setOnClickListener {
            searchPanel.visibility = View.GONE
            searchInput.text.clear()
            adapter.clearSearchHighlight()
            searchResults.clear()
            currentSearchIndex = -1
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        }

// Поиск при вводе текста
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString().trim())
            }
        })

// Стрелка ВВЕРХ
        searchUpBtn.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex <= 0) searchResults.size - 1 else currentSearchIndex - 1
                scrollToSearchResult(currentSearchIndex)
                updateSearchCounter()
            }
        }

// Стрелка ВНИЗ
        searchDownBtn.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex >= searchResults.size - 1) 0 else currentSearchIndex + 1
                scrollToSearchResult(currentSearchIndex)
                updateSearchCounter()
            }
        }
        backButton.setOnClickListener {
            if (adapter.isInSelectionMode()) {
                adapter.exitSelectionMode()
            } else {
                finish()
            }
        }

        // Мультивыбор кнопки
        findViewById<ImageButton>(R.id.selection_close_btn).setOnClickListener {
            adapter.exitSelectionMode()
        }
        findViewById<ImageButton>(R.id.selection_delete_btn).setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.isNotEmpty()) onDeleteMessages(selected)
        }
        findViewById<ImageButton>(R.id.selection_forward_btn).setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.isNotEmpty()) onForwardMessages(selected)
        }
        findViewById<ImageButton>(R.id.selection_fav_btn).setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.isNotEmpty()) onToggleFavorite(selected)
        }

        // Закрыть reply
        findViewById<ImageButton>(R.id.reply_close_btn).setOnClickListener {
            replyToMessage = null
            replyPanel.visibility = View.GONE
        }
    }
    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            adapter.clearSearchHighlight()
            searchResults.clear()
            searchCount.text = ""
            return
        }

        val allMessages = Repository.getMessages(currentUser, chatWith)
        val adapterItems = MessageRecyclerAdapter.buildItemList(allMessages)

        searchResults.clear()

        adapterItems.forEachIndexed { index, item ->
            if (item is ChatListItem.MessageItem && item.message.text.contains(query, ignoreCase = true)) {
                searchResults.add(index)  // ← теперь индекс в адаптере!
            }
        }

        currentSearchIndex = if (searchResults.isNotEmpty()) 0 else -1
        updateSearchCounter()

        if (searchResults.isNotEmpty()) {
            val item = adapterItems[searchResults[0]]
            if (item is ChatListItem.MessageItem) {
                adapter.setSearchHighlight(query, item.message.id)
            }
            recyclerView.scrollToPosition(searchResults[0])
        }
    }

    private fun scrollToSearchResult(index: Int) {
        if (index in searchResults.indices) {
            val allMessages = Repository.getMessages(currentUser, chatWith)
            val adapterItems = MessageRecyclerAdapter.buildItemList(allMessages)

            val pos = searchResults[index]
            val item = adapterItems[pos]
            if (item is ChatListItem.MessageItem) {
                adapter.setSearchHighlight(searchInput.text.toString().trim(), item.message.id)
            }
            recyclerView.scrollToPosition(pos)
        }
    }

    // Обновить счётчик
    private fun updateSearchCounter() {
        if (searchResults.isEmpty()) {
            searchCount.text = "0/0"
        } else {
            searchCount.text = "${currentSearchIndex + 1}/${searchResults.size}"
        }
    }
    private fun showImagePickerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Добавить фото")
            .setItems(arrayOf("Камера", "Галерея")) { _, w ->
                if (w == 0) dispatchTakePictureIntent() else pickImageFromGallery()
            }
            .show()
    }
    private fun setupInputPanel() {
        // Переключение кнопок send/voice
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    voiceButton.visibility = View.VISIBLE
                    sendButton.visibility = View.GONE
                    sendTypingStatus(false)
                } else {
                    voiceButton.visibility = View.GONE
                    sendButton.visibility = View.VISIBLE
                    sendTypingStatus(true)
                }
            }
        })

        // Отправка сообщения
        // Отправка сообщения
        // Отправка сообщения
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            AudioPlayerManager.stop()
            val reply = replyToMessage
            messageInput.text.clear()
            replyToMessage = null
            replyPanel.visibility = View.GONE
            sendTypingStatus(false)

            // ✅ Генерируем clientMessageId
            val clientId = java.util.UUID.randomUUID().toString()

            lifecycleScope.launch(Dispatchers.IO) {
                // 1. Сохраняем локально с clientMessageId
                val msgId = if (reply != null) {
                    Repository.sendReplyMessage(currentUser, chatWith, text, reply, isSent = false, clientMessageId = clientId)
                } else {
                    Repository.sendMessage(currentUser, chatWith, text, isSent = false, clientMessageId = clientId)
                }

                withContext(Dispatchers.Main) { loadMessages() }

                // 2. Отправляем на сервер
                if (TransportManager.getMode() == TransportMode.SERVER) {
                    val success = SyncManager.sendMessage(currentUser, chatWith, text, clientId)
                    if (success) {
                        Repository.markMessageAsSent(msgId)

                    }
                } else {
                    Repository.markMessageAsSent(msgId)
                    withContext(Dispatchers.Main) { loadMessages() }
                }
            }
        }

        // Вложения
        attachButton.setOnClickListener { showImagePickerDialog() }

        // Голосовое сообщение
        setupVoiceButton()
    }

    private fun setupVoiceButton() {
        val swipeThresholdPx = SWIPE_UP_THRESHOLD_DP * resources.displayMetrics.density

        voiceButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    voiceTouchStartY = event.rawY
                    recordingCancelled = false

                    // Останавливаем воспроизведение аудио
                    AudioPlayerManager.stop()

                    startRecording()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isRecording && !recordingCancelled) {
                        val dy = voiceTouchStartY - event.rawY
                        if (dy > swipeThresholdPx) {
                            cancelRecording()
                        } else if (dy > swipeThresholdPx * 0.4f) {
                            recordingHint.text = "↑ Отпустите для отмены"
                        } else {
                            recordingHint.text = "↑ свайп — отмена"
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!recordingCancelled) stopRecordingAndSend()
                    true
                }

                else -> false
            }
        }
    }

    // ==========================================
    // WebSocket Callbacks
    // ==========================================



    private fun sendTypingStatus(isTyping: Boolean) {
        if (TransportManager.getMode() != TransportMode.SERVER) return

        val transport = TransportManager.get()
        if (transport !is ServerTransport) return

        // Отправляем typing только если статус изменился
        if (isTyping && !isTypingSent) {
            isTypingSent = true
            transport.sendTyping(chatWith, true)

            // Автоматически сбрасываем через 3 секунды
            typingJob?.cancel()
            typingJob = lifecycleScope.launch {
                delay(3000)
                if (isTypingSent) {
                    isTypingSent = false
                    transport.sendTyping(chatWith, false)
                }
            }
        } else if (!isTyping && isTypingSent) {
            isTypingSent = false
            typingJob?.cancel()
            transport.sendTyping(chatWith, false)
        }
    }

    private fun markMessagesAsRead() {
        Repository.markAsRead(currentUser, chatWith)

        if (TransportManager.getMode() == TransportMode.SERVER) {
            val transport = TransportManager.get()
            if (transport is ServerTransport) {
                transport.sendRead(chatWith)
            }
        }
    }

    // ==========================================
    // Audio Player
    // ==========================================

    private fun setupAudioPlayer() {
        AudioPlayerManager.onPlayStateChanged = { messageId, isPlaying ->
            // Обновляем UI конкретного сообщения
            AudioPlayerManager.seekTo(0)
            adapter.notifyDataSetChanged()
        }



        AudioPlayerManager.onPlaybackComplete = { messageId ->
            adapter.notifyDataSetChanged()
        }
    }

    // ==========================================
    // Chat Status
    // ==========================================

    private fun updateChatStatus(isOnline: Boolean? = null) {
        if (TransportManager.getMode() == TransportMode.LOCAL) {
            statusText.visibility = View.GONE
            return
        }

        statusText.visibility = View.VISIBLE

        if (isOnline != null) {
            statusText.text = if (isOnline) "🟢 в сети" else "не в сети"
            return
        }

        // Запрашиваем статус с сервера
        lifecycleScope.launch {
            val result = TransportManager.get().searchUsers(chatWith, currentUser)
            when (result) {
                is TransportResult.Success -> {
                    val user = result.data.find { it.login == chatWith }
                    statusText.text = if (user?.isOnline == true) "🟢 в сети" else "не в сети"
                }
                is TransportResult.Error -> {
                    statusText.text = "не в сети"
                }
            }
        }
    }

    private fun getOnlineStatus(): String {
        return statusText.text.toString().let {
            if (it == "печатает...") "🟢 в сети" else it
        }
    }

    // ==========================================
    // Drag-to-Select
    // ==========================================

    private fun setupDragToSelect() {
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private var longPressHandler = Handler(Looper.getMainLooper())
            private var longPressRunnable: Runnable? = null
            private var touchStartX = 0f
            private var touchStartY = 0f
            private var longPressTriggered = false
            private var isDragSelecting = false
            private var lastDragPosition = -1

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = e.x
                        touchStartY = e.y
                        longPressTriggered = false

                        if (adapter.isInSelectionMode()) {
                            isDragSelecting = true
                            lastDragPosition = -1
                            handleDragAt(rv, e)
                            return true
                        } else {
                            longPressRunnable = Runnable {
                                longPressTriggered = true
                                val child = rv.findChildViewUnder(touchStartX, touchStartY)
                                if (child != null) {
                                    val pos = rv.getChildAdapterPosition(child)
                                    val item = adapter.getItemAt(pos)
                                    if (item is ChatListItem.MessageItem) {
                                        vibrate()
                                        adapter.enterSelectionMode(item.message.id)
                                        isDragSelecting = true
                                        lastDragPosition = pos
                                    }
                                }
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, 400)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!longPressTriggered && (abs(e.x - touchStartX) > 20 || abs(e.y - touchStartY) > 20)) {
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }
                }
                return isDragSelecting && longPressTriggered
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (!isDragSelecting) return
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> handleDragAt(rv, e)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragSelecting = false
                        lastDragPosition = -1
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

            private fun handleDragAt(rv: RecyclerView, e: MotionEvent) {
                val child = rv.findChildViewUnder(e.x, e.y) ?: return
                val pos = rv.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION || pos == lastDragPosition) return
                lastDragPosition = pos
                adapter.addSelectionAt(pos)
            }
        })
    }

    private fun vibrate() {
        try {
            val vib = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vib?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vib?.vibrate(30)
            }
        } catch (_: Exception) {}
    }

    // ==========================================
    // Swipe to Reply
    // ==========================================

    private fun setupSwipeToReply() {
        val cb = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun isItemViewSwipeEnabled() = !adapter.isInSelectionMode()
            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder) = 0.25f

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val item = adapter.getItemAt(vh.adapterPosition)
                if (item is ChatListItem.MessageItem) onReplyMessage(item.message)
                adapter.notifyItemChanged(vh.adapterPosition)
            }

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val item = adapter.getItemAt(vh.adapterPosition)
                return if (item is ChatListItem.DateHeader) 0 else makeMovementFlags(0, ItemTouchHelper.RIGHT)
            }
        }
        ItemTouchHelper(cb).attachToRecyclerView(recyclerView)
    }

    // ==========================================
    // Голосовые сообщения
    // ==========================================

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 102)
            return
        }

        try {
            audioFile = File(externalCacheDir, "audio_${System.currentTimeMillis()}.m4a")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordingCancelled = false
            showRecordingUI()
        } catch (e: IOException) {
            Toast.makeText(this, "Ошибка записи", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelRecording() {
        if (!isRecording) return
        recordingCancelled = true
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            isRecording = false
            hideRecordingUI()
            audioFile?.delete()
        } catch (e: Exception) {
            hideRecordingUI()
        }
    }

    private fun stopRecordingAndSend() {
        if (!isRecording || recordingCancelled) return
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            isRecording = false
            val elapsed = System.currentTimeMillis() - recordingStartTime
            recordingDurationSeconds = (elapsed / 1000).toInt()
            hideRecordingUI()

            audioFile?.let { file ->
                if (recordingDurationSeconds < 1) {
                    file.delete()
                    Toast.makeText(this, "Слишком короткая", Toast.LENGTH_SHORT).show()
                    return
                }

                // ПРОСТО ВЫЗЫВАЕМ НАШ НОВЫЙ МЕТОД (старые Repository.send... мы удалили)
                sendMediaMessageToServer(file.absolutePath, "audio", recordingDurationSeconds)
            }
        } catch (e: Exception) {
            hideRecordingUI()
        }
    }

    private fun showRecordingUI() {
        inputPanel.visibility = View.GONE
        recordingPanel.visibility = View.VISIBLE
        recordingTimer.text = "0:00"
        recordingHint.text = "↑ свайп — отмена"
        recordingStartTime = System.currentTimeMillis()
        timerHandler.post(timerRunnable)
        dotAnimation = AlphaAnimation(1f, 0f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        recordingDot.startAnimation(dotAnimation)
    }

    private fun hideRecordingUI() {
        timerHandler.removeCallbacks(timerRunnable)
        recordingDot.clearAnimation()
        recordingPanel.visibility = View.GONE
        inputPanel.visibility = View.VISIBLE
    }

    // ==========================================
    // Фото
    // ==========================================

    private fun dispatchTakePictureIntent() {
        // ✅ Проверяем разрешение камеры
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION  // ← добавь константу
            )
            return
        }

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { i ->
            i.resolveActivity(packageManager)?.also {
                createImageFile()?.also { f ->
                    currentPhotoPath = f.absolutePath
                    i.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        f
                    ))
                    startActivityForResult(i, REQUEST_CODE_CAMERA)
                }
            }
        }
    }

    private fun pickImageFromGallery() {
        startActivityForResult(
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            REQUEST_CODE_PICK_IMAGE
        )
    }


    private fun createImageFile(): File? {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("JPEG_${ts}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
            .apply { currentPhotoPath = absolutePath }
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (res != RESULT_OK) return
        when (rc) {
            REQUEST_CODE_PICK_IMAGE -> data?.data?.let { uri ->
                saveImageToLocalFile(uri)?.let { path -> // <-- Добавили 'path ->'
                    sendMediaMessageToServer(path, "image")
                }
            }
            REQUEST_CODE_CAMERA -> currentPhotoPath?.let { path -> // <-- Добавили 'path ->'
                sendMediaMessageToServer(path, "image")
            }
        }
    }

    private fun saveImageToLocalFile(uri: Uri): String? {
        return try {
            val f = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            f.absolutePath
        } catch (e: Exception) { null }
    }

    // ==========================================
    // Жизненный цикл
    // ==========================================

    private fun loadMessages() {
        Log.e("ChatActivity", "🚨🚨🚨 loadMessages() CALLED for $chatWith")

        lifecycleScope.launch(Dispatchers.IO) {
            val msgs = Repository.getMessages(currentUser, chatWith)
            Log.e("ChatActivity", "🚨 Loaded ${msgs.size} messages from DB")

            if (msgs.isNotEmpty()) {
                val last = msgs.last()
                Log.e("ChatActivity", "🚨 Last message: ${last.sender}: ${last.text.take(30)}")
            }

            withContext(Dispatchers.Main) {
                val oldCount = adapter.itemCount
                adapter.updateMessages(msgs)
                val newCount = adapter.itemCount

                Log.e("ChatActivity", "🚨 Adapter updated: $oldCount → $newCount items")

                if (msgs.isNotEmpty()) {
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }





    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        mediaRecorder?.release()
        mediaRecorder = null
        typingJob?.cancel()

    }

    override fun onRetryMessage(message: Message) {
        Toast.makeText(this, "Попытка отправки...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false

            if (message.type == "text") {
                success = SyncManager.sendMessage(
                    sender = message.sender,
                    receiver = message.receiver,
                    text = message.text,
                    clientMessageId = message.clientMessageId
                )
            } else {
                val localFile = File(message.mediaUrl ?: "")
                if (localFile.exists()) {
                    val uploadResult = TransportManager.get().uploadFile(localFile)
                    if (uploadResult is TransportResult.Success) {
                        val serverUrl = uploadResult.data
                        success = SyncManager.sendMediaMessage(
                            sender = message.sender,
                            receiver = message.receiver,
                            text = message.text,
                            type = message.type,
                            mediaUrl = serverUrl,
                            duration = message.duration,
                            clientMessageId = message.clientMessageId
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Repository.markMessageAsSent(message.id)
                    // ✅ НЕ ВЫЗЫВАЕМ loadMessages() — WebSocket сам обновит
                    Toast.makeText(this@ChatActivity, "✅ Отправлено", Toast.LENGTH_SHORT).show()
                    withContext(Dispatchers.Main) { loadMessages() }
                } else {
                    Toast.makeText(this@ChatActivity, "Всё ещё нет сети :(", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMediaMessageToServer(path: String, type: String, duration: Int = 0) {
        lifecycleScope.launch(Dispatchers.IO) {
            val text = if (type == "image") "📷 Фото" else "🎤 Голосовое"
            val clientId = java.util.UUID.randomUUID().toString() // ✅

            // 1. Мгновенно сохраняем локально с clientMessageId
            val msgId = if (type == "image") {
                Repository.sendImageMessage(currentUser, chatWith, path, isSent = false, clientMessageId = clientId)
            } else {
                Repository.sendAudioMessage(currentUser, chatWith, path, duration, isSent = false, clientMessageId = clientId)
            }
            withContext(Dispatchers.Main) { loadMessages() }

            // 2. Пытаемся отправить
            if (TransportManager.getMode() == TransportMode.SERVER) {
                try {
                    val uploadResult = TransportManager.get().uploadFile(File(path))

                    if (uploadResult is TransportResult.Success) {
                        val serverUrl = uploadResult.data

                        val sent = if (TransportManager.get().isConnected()) {
                            // ✅ Передаём clientId
                            SyncManager.sendMediaMessage(
                                currentUser, chatWith, text, type, serverUrl, duration, clientId
                            )
                        } else {
                            // ✅ Fallback с clientId
                            sendMediaViaHttp(
                                currentUser, chatWith, text, type, serverUrl, duration, clientId
                            )
                        }

                        if (sent) {
                            Repository.markMessageAsSent(msgId)
                            withContext(Dispatchers.Main) { loadMessages() }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ChatActivity, "Файл загружен, но сообщение не отправлено", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ChatActivity, "Не удалось загрузить файл", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (TransportManager.getMode() == TransportMode.LOCAL) {
                Repository.markMessageAsSent(msgId)
                withContext(Dispatchers.Main) { loadMessages() }
            }
        }
    }

    private suspend fun sendMediaViaHttp(
        sender: String, receiver: String, text: String,
        type: String, mediaUrl: String, duration: Int, clientMessageId: String // ✅
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val transport = TransportManager.get()
                val result = transport.sendMediaMessage(
                    sender, receiver, text, type, mediaUrl, duration, clientMessageId
                )
                result is TransportResult.Success
            } catch (e: Exception) {
                false
            }
        }
    }



    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (adapter.isInSelectionMode()) {
            adapter.exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
    // Поле класса:
    private val wsListener: (Message) -> Unit = { message ->
        if ((message.sender == chatWith && message.receiver == currentUser) ||
            (message.sender == currentUser && message.receiver == chatWith)) {

            lifecycleScope.launch(Dispatchers.Main) {
                Log.e("ChatActivity", "🚨🔄 WS → loadMessages()")
                loadMessages()
                if (message.sender == chatWith) {
                    markMessagesAsRead()
                }
            }
        }
    }

    // В setupWebSocketCallbacks() заменить регистрацию:
    private fun setupWebSocketCallbacks() {
        val transport = TransportManager.get()
        transport.onMessageReceived(wsListener)

        if (transport is ServerTransport) {
            transport.onTypingReceived { sender, isTyping ->
                if (sender == chatWith) {
                    runOnUiThread {
                        statusText.text = if (isTyping) "печатает..." else getOnlineStatus()
                    }
                }
            }

            transport.onReadReceived { reader ->
                if (reader == chatWith) {
                    lifecycleScope.launch {
                        delay(200)
                        withContext(Dispatchers.Main) { loadMessages() }
                    }
                }
            }

            transport.onOnlineStatusReceived { login, isOnline ->
                if (login == chatWith) {
                    runOnUiThread {
                        val newStatus = if (isOnline) "🟢 в сети" else "не в сети"
                        statusText.text = newStatus
                    }
                }
            }

            // ✅ НОВОЕ: Обработка удаления от сервера
            transport.onDeleteMessageReceived { messageId ->
                Log.d("ChatActivity", "🗑️ Server deleted: $messageId")
                Repository.deleteMessageByClientId(messageId)
                loadMessages()
            }

            // ✅ НОВОЕ: Обработка удаления чата от сервера
            transport.onDeleteChatReceived { chatWithName ->
                if (chatWithName == chatWith) {
                    Log.d("ChatActivity", "🗑️ Chat deleted by $chatWithName")
                    Repository.deleteChat(currentUser, chatWith)
                    runOnUiThread {
                        Toast.makeText(this, "Чат удалён", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        markMessagesAsRead()
        loadMessages()
        val transport = TransportManager.get() as? ServerTransport
        if (transport?.isConnected() == true) {
            // WebSocket работает — статус уже актуальный
            // onOnlineStatusReceived уже обновил statusText
        } else {
            statusText.text = "не в сети"
        }
        // ✅ Переподключаем при возврате

    }

    override fun onPause() {
        super.onPause()
        sendTypingStatus(false)
        // ✅ Снимаем при уходе
        TransportManager.get().removeMessageCallback(wsListener)
    }
}