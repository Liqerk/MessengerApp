package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class ChatActivity : AppCompatActivity(), MessageRecyclerAdapter.MessageActionListener {

    private lateinit var currentUser: String
    private lateinit var chatWith: String
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

    // Reply
    private lateinit var replyPanel: LinearLayout
    private lateinit var replySenderName: TextView
    private lateinit var replyPreviewText: TextView
    private var replyToMessage: Message? = null

    // Поиск
    private var searchResultPositions: List<Int> = emptyList()
    private var currentSearchIndex: Int = -1

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
                messages.forEach { Repository.deleteMessage(it.id) }
                adapter.exitSelectionMode()
                loadMessages()
                Toast.makeText(this, "Удалено: ${messages.size}", Toast.LENGTH_SHORT).show()
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

        // Инициализация UI
        val titleText: TextView = findViewById(R.id.chat_title)
        val avatarText: TextView = findViewById(R.id.chat_avatar)
        val backButton: ImageButton = findViewById(R.id.back_button)
        normalToolbar = findViewById(R.id.normal_toolbar)
        selectionPanel = findViewById(R.id.selection_panel)

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
        findViewById<ImageButton>(R.id.reply_close_btn).setOnClickListener {
            replyToMessage = null
            replyPanel.visibility = View.GONE
        }

        // Заголовок и аватар
        titleText.text = chatWith
        avatarText.text = chatWith.first().uppercase()
        val colors = intArrayOf(0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt())
        avatarText.background.setTint(colors[abs(chatWith.hashCode()) % colors.size])
        statusText = findViewById(R.id.chat_status)
        updateChatStatus()
        // RecyclerView
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        adapter = MessageRecyclerAdapter(emptyList(), currentUser, this)
        recyclerView.adapter = adapter

        // Drag-to-select
        setupDragToSelect()

        // Swipe to reply
        setupSwipeToReply()

        // Навигация
        backButton.setOnClickListener {
            if (adapter.isInSelectionMode()) {
                adapter.exitSelectionMode()
            } else {
                finish()
            }
        }

        // Кнопки мультивыбора
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

        // Переключение кнопок
        messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrEmpty()) {
                    voiceButton.visibility = View.VISIBLE
                    sendButton.visibility = View.GONE
                } else {
                    voiceButton.visibility = View.GONE
                    sendButton.visibility = View.VISIBLE
                }
            }
        })

        // Отправка сообщения
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                val reply = replyToMessage

                if (reply != null) {
                    Repository.sendReplyMessage(currentUser, chatWith, text, reply)
                } else {
                    Repository.sendMessage(currentUser, chatWith, text)
                }

                messageInput.text.clear()
                replyToMessage = null
                replyPanel.visibility = View.GONE
                loadMessages()
            }
        }

        // Вложения
        attachButton.setOnClickListener { showImagePickerDialog() }

        // Голосовое
        val swipeThresholdPx = SWIPE_UP_THRESHOLD_DP * resources.displayMetrics.density
        voiceButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    voiceTouchStartY = event.rawY
                    recordingCancelled = false
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
    // DRAG-TO-SELECT
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
    private fun updateChatStatus() {
        // Если локальный режим - не показываем статус
        if (TransportManager.getMode() == TransportMode.LOCAL) {
            statusText.visibility = View.GONE
            return
        }

        statusText.visibility = View.VISIBLE

        // Проверяем через сервер
        lifecycleScope.launch {
            val result = TransportManager.get().searchUsers(chatWith, currentUser)
            when (result) {
                is TransportResult.Success -> {
                    val user = result.data.find { it.login == chatWith }
                    statusText.text = if (user?.isOnline == true) "🟢 в сети" else "не в сети"
                }
                is TransportResult.Error -> {
                    statusText.text = "⬜ не в сети"
                }
            }
        }
    }
    private fun vibrate() {
        try {
            val vib = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vib?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vib?.vibrate(30)
            }
        } catch (_: Exception) {}
    }

    // ==========================================
    // SWIPE TO REPLY
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
    // ЗАПИСЬ ГОЛОСА
    // ==========================================

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
                Repository.sendAudioMessage(currentUser, chatWith, file.absolutePath, recordingDurationSeconds)
                loadMessages()
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
    // ФОТО
    // ==========================================

    private fun showImagePickerDialog() {
        AlertDialog.Builder(this).setTitle("Добавить фото")
            .setItems(arrayOf("Камера", "Галерея")) { _, w ->
                if (w == 0) dispatchTakePictureIntent() else pickImageFromGallery()
            }.show()
    }

    private fun pickImageFromGallery() {
        startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_CODE_PICK_IMAGE)
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { i ->
            i.resolveActivity(packageManager)?.also {
                createImageFile()?.also { f ->
                    currentPhotoPath = f.absolutePath
                    i.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(this, "${packageName}.fileprovider", f))
                    startActivityForResult(i, REQUEST_CODE_CAMERA)
                }
            }
        }
    }

    private fun createImageFile(): File? {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("JPEG_${ts}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (res != RESULT_OK) return
        when (rc) {
            REQUEST_CODE_PICK_IMAGE -> data?.data?.let { uri ->
                saveImageToLocalFile(uri)?.let {
                    Repository.sendImageMessage(currentUser, chatWith, it)
                    loadMessages()
                }
            }
            REQUEST_CODE_CAMERA -> currentPhotoPath?.let {
                Repository.sendImageMessage(currentUser, chatWith, it)
                loadMessages()
            }
        }
    }

    private fun saveImageToLocalFile(uri: Uri): String? {
        return try {
            val f = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { i ->
                f.outputStream().use { o -> i.copyTo(o) }
            }
            f.absolutePath
        } catch (e: Exception) { null }
    }

    // ==========================================
    // ЖИЗНЕННЫЙ ЦИКЛ
    // ==========================================

    override fun onResume() {
        super.onResume()
        Repository.markAsRead(currentUser, chatWith)
        loadMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun loadMessages() {
        val msgs = Repository.getMessages(currentUser, chatWith)
        adapter.updateMessages(msgs)
        if (msgs.isNotEmpty()) {
            recyclerView.scrollToPosition(adapter.itemCount - 1)
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
}