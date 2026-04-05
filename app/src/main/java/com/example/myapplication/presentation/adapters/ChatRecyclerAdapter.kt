package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import com.bumptech.glide.Glide
class ChatRecyclerAdapter(
    private var chatItems: List<ChatItem>,
    private val onClick: (ChatItem) -> Unit,
    private val onLongClick: ((ChatItem) -> Unit)? = null
) : RecyclerView.Adapter<ChatRecyclerAdapter.ChatViewHolder>()  {

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
        if (item.isPinned) {
            holder.name.text = "📌 ${item.name}"
        } else {
            holder.name.text = item.name
        }
        // Проверяем есть ли аватарка
        val avatarPath = Repository.getAvatar(item.name)
        holder.itemView.setOnClickListener { onClick(item) }

// Долгое нажатие
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
        }
        if (avatarPath != null && java.io.File(avatarPath).exists()) {
            // Показываем фото
            holder.avatar.visibility = android.view.View.GONE
            holder.avatarImage.visibility = android.view.View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(java.io.File(avatarPath))
                .circleCrop()
                .into(holder.avatarImage)
        } else {
            // Показываем букву
            holder.avatar.visibility = android.view.View.VISIBLE
            holder.avatarImage.visibility = android.view.View.GONE
            holder.avatar.text = item.name.first().uppercase()
            val colors = intArrayOf(
                0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
                0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt(),
                0xFF795548.toInt(), 0xFF607D8B.toInt()
            )
            val colorIndex = Math.abs(item.name.hashCode()) % colors.size
            holder.avatar.background.setTint(colors[colorIndex])
        }

        holder.name.text = item.name
        holder.lastMessage.text = item.lastMessage.ifEmpty {
            holder.itemView.context.getString(R.string.no_messages)
        }

        if (item.lastMessageTime.isNotEmpty() && item.lastMessageTime.length >= 16) {
            holder.time.visibility = android.view.View.VISIBLE
            holder.time.text = item.lastMessageTime.substring(11, 16)
        } else {
            holder.time.visibility = android.view.View.GONE
        }

        if (item.unreadCount > 0) {
            holder.unread.visibility = android.view.View.VISIBLE
            holder.unread.text = item.unreadCount.toString()
        } else {
            holder.unread.visibility = android.view.View.GONE
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = chatItems.size

    fun updateData(newItems: List<ChatItem>) {
        chatItems = newItems
        notifyDataSetChanged()
    }
}