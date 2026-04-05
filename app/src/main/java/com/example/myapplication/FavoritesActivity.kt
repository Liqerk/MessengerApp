package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var favAdapter: FavoriteAdapter
    private var allMessages: List<Message> = emptyList()
    private lateinit var login: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        Repository.init(this)
        login = SessionManager(this).getLogin() ?: ""

        val backBtn: ImageButton = findViewById(R.id.back_button)
        recyclerView = findViewById(R.id.favorites_recycler)
        emptyText = findViewById(R.id.empty_text)
        countText = findViewById(R.id.favorites_count)

        val searchBtn: ImageButton = findViewById(R.id.fav_search_btn)
        val searchPanel: LinearLayout = findViewById(R.id.fav_search_panel)
        val searchInput: EditText = findViewById(R.id.fav_search_input)
        val searchClose: ImageButton = findViewById(R.id.fav_search_close)

        backBtn.setOnClickListener { finish() }

        // Поиск
        searchBtn.setOnClickListener {
            searchPanel.visibility = View.VISIBLE
            searchInput.requestFocus()
        }

        searchClose.setOnClickListener {
            searchPanel.visibility = View.GONE
            searchInput.text.clear()
            displayMessages(allMessages)
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    val filtered = allMessages.filter {
                        it.text.contains(query, ignoreCase = true) ||
                                it.sender.contains(query, ignoreCase = true)
                    }
                    displayMessages(filtered)
                } else {
                    displayMessages(allMessages)
                }
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        favAdapter = FavoriteAdapter(emptyList(), login) { message ->
            // Клик — переход к сообщению в чате
            val chatWith = if (message.sender == login) message.receiver else message.sender
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("chatWith", chatWith)
            intent.putExtra("scrollToMessageId", message.id)
            startActivity(intent)
        }
        recyclerView.adapter = favAdapter

        loadFavorites()
    }

    override fun onResume() {
        super.onResume()
        loadFavorites()
    }

    private fun loadFavorites() {
        allMessages = Repository.getFavoriteMessages(login)
        displayMessages(allMessages)
    }

    private fun displayMessages(messages: List<Message>) {
        if (messages.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            countText.text = "0 сообщений"
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            countText.text = "${messages.size} сообщений"
            favAdapter.updateData(messages)
        }
    }
}

// ==========================================
// Адаптер избранного как в ТГ
// ==========================================

class FavoriteAdapter(
    private var messages: List<Message>,
    private val currentUser: String,
    private val onClick: (Message) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.FavViewHolder>() {

    class FavViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.fav_avatar)
        val sender: TextView = view.findViewById(R.id.fav_sender)
        val chatContext: TextView = view.findViewById(R.id.fav_chat_context)
        val time: TextView = view.findViewById(R.id.fav_time)
        val text: TextView = view.findViewById(R.id.fav_text)
        val image: ImageView = view.findViewById(R.id.fav_image)
        val audioLabel: TextView = view.findViewById(R.id.fav_audio_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_message, parent, false)
        return FavViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavViewHolder, position: Int) {
        val msg = messages[position]
        val context = holder.itemView.context

        // Аватарка
        holder.avatar.text = msg.sender.first().uppercase()
        val colors = intArrayOf(
            0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt()
        )
        holder.avatar.background.setTint(
            colors[Math.abs(msg.sender.hashCode()) % colors.size]
        )

        // Отправитель
        holder.sender.text = msg.sender

        // Контекст чата
        val chatWith = if (msg.sender == currentUser) msg.receiver else msg.sender
        holder.chatContext.text = if (msg.sender == currentUser) {
            "Вы → $chatWith"
        } else {
            "$chatWith → Вам"
        }

        // Время
        holder.time.text = if (msg.timestamp.length >= 16) {
            msg.timestamp.substring(0, 16).replace("T", " ")
        } else {
            msg.timestamp
        }

        // Контент сообщения
        when (msg.type) {
            "text" -> {
                holder.text.visibility = View.VISIBLE
                holder.text.text = msg.text
                holder.image.visibility = View.GONE
                holder.audioLabel.visibility = View.GONE
            }
            "image" -> {
                holder.text.visibility = if (msg.text == "📷 Фото") View.GONE else View.VISIBLE
                holder.text.text = msg.text
                holder.image.visibility = View.VISIBLE
                holder.audioLabel.visibility = View.GONE

                msg.mediaUrl?.let { url ->
                    if (url.startsWith("http")) {
                        Glide.with(context).load(url).into(holder.image)
                    } else {
                        Glide.with(context).load(File(url)).into(holder.image)
                    }
                }
            }
            "audio" -> {
                holder.text.visibility = View.GONE
                holder.image.visibility = View.GONE
                holder.audioLabel.visibility = View.VISIBLE
                val min = msg.duration / 60
                val sec = msg.duration % 60
                holder.audioLabel.text = "🎤 Голосовое сообщение ${min}:${String.format("%02d", sec)}"
            }
            else -> {
                holder.text.visibility = View.VISIBLE
                holder.text.text = msg.text
                holder.image.visibility = View.GONE
                holder.audioLabel.visibility = View.GONE
            }
        }

        // Клик — переход к сообщению
        holder.itemView.setOnClickListener { onClick(msg) }

        // Долгое нажатие — убрать из избранного
        holder.itemView.setOnLongClickListener {
            android.app.AlertDialog.Builder(context)
                .setTitle("Убрать из избранного?")
                .setPositiveButton("Убрать") { _, _ ->
                    Repository.toggleFavorite(msg.id)
                    val pos = messages.indexOf(msg)
                    messages = messages.toMutableList().also { it.removeAt(pos) }
                    notifyItemRemoved(pos)
                    Toast.makeText(context, "Убрано из избранного", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
            true
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateData(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}