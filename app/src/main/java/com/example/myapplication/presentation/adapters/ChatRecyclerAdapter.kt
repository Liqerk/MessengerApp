package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class ChatRecyclerAdapter(
    private var chatItems: List<ChatItem>,
    private val onClick: (ChatItem) -> Unit,
    private val onLongClick: ((ChatItem) -> Unit)? = null
) : RecyclerView.Adapter<ChatRecyclerAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.chat_avatar)
        val avatarImage: ImageView = view.findViewById(R.id.chat_avatar_image)
        val name: TextView = view.findViewById(R.id.chat_name)
        val lastMessage: TextView = view.findViewById(R.id.chat_last_message)
        val time: TextView = view.findViewById(R.id.chat_time)
        val unread: TextView = view.findViewById(R.id.chat_unread)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = chatItems[position]

        holder.name.text = if (item.isPinned) "📌 ${item.name}" else item.name

        val avatarPath = Repository.getAvatar(item.name)

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
        }

        // ✅ ИСПРАВЛЕНО: Правильная загрузка HTTP и локальных аватарок
        if (!avatarPath.isNullOrEmpty()) {
            val isHttp = avatarPath.startsWith("http://") || avatarPath.startsWith("https://")

            if (isHttp || File(avatarPath).exists()) {
                holder.avatar.visibility = View.GONE
                holder.avatarImage.visibility = View.VISIBLE

                // ✅ Правильная загрузка в зависимости от типа пути
                val glideRequest = if (isHttp) {
                    Glide.with(holder.itemView.context).load(avatarPath)
                } else {
                    Glide.with(holder.itemView.context).load(File(avatarPath))
                }

                glideRequest
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery) // Заглушка пока грузится
                    .error(android.R.drawable.ic_menu_report_image)  // Если ошибка
                    .into(holder.avatarImage)
            } else {
                // Файл не существует → показываем букву
                showAvatarLetter(holder, item)
            }
        } else {
            // Нет аватарки → показываем букву
            showAvatarLetter(holder, item)
        }

        holder.lastMessage.text = item.lastMessage.ifEmpty {
            holder.itemView.context.getString(R.string.no_messages)
        }

        if (item.lastMessageTime.isNotEmpty() && item.lastMessageTime.length >= 16) {
            holder.time.visibility = View.VISIBLE

            holder.time.text = formatChatTime(item.lastMessageTime)
        } else {
            holder.time.visibility = View.GONE
        }

        if (item.unreadCount > 0) {
            holder.unread.visibility = View.VISIBLE
            holder.unread.text = item.unreadCount.toString()
        } else {
            holder.unread.visibility = View.GONE
        }
    }

    // ✅ ДОБАВЬТЕ этот метод в класс ChatRecyclerAdapter
    private fun showAvatarLetter(holder: ChatViewHolder, item: ChatItem) {
        holder.avatar.visibility = View.VISIBLE
        holder.avatarImage.visibility = View.GONE
        holder.avatar.text = item.name.firstOrNull()?.uppercase() ?: "?"

        val colors = intArrayOf(
            0xFF2196F3.toInt(),
            0xFF4CAF50.toInt(),
            0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(),
            0xFFE91E63.toInt(),
            0xFF00BCD4.toInt(),
            0xFF795548.toInt(),
            0xFF607D8B.toInt()
        )
        val colorIndex = abs(item.name.hashCode()) % colors.size
        holder.avatar.background.setTint(colors[colorIndex])
    }

    override fun getItemCount(): Int = chatItems.size
    private fun formatChatTime(timestamp: String): String {
        return try {
            val utcFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            utcFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = utcFormat.parse(timestamp.substringBefore("."))

            val now = Calendar.getInstance()
            val msgTime = Calendar.getInstance().apply { time = date }

            when {
                // Сегодня → только время
                now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) &&
                        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) ->
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)

                // Вчера → "Вчера"
                now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1 &&
                        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) -> "Вчера"

                // Этот год → день и месяц
                now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) ->
                    SimpleDateFormat("d MMM", Locale("ru")).format(date)

                // Прошлый год → день, месяц, год
                else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            if (timestamp.length >= 16) timestamp.substring(11, 16) else ""
        }
    }
    fun updateData(newItems: List<ChatItem>) {
        chatItems = newItems
        notifyDataSetChanged()
    }
}