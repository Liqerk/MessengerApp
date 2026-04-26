package com.example.myapplication

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.util.Linkify
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.myapplication.User
import java.util.TimeZone

class MessageRecyclerAdapter(
    private var items: List<ChatListItem>,
    private val currentUser: String,
    private val onMessageAction: MessageActionListener? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface MessageActionListener {
        fun onDeleteMessages(messages: List<Message>)
        fun onEditMessage(message: Message)
        fun onForwardMessages(messages: List<Message>)
        fun onReplyMessage(message: Message)
        fun onToggleFavorite(messages: List<Message>)
        fun onSelectionChanged(count: Int)
        fun onRetryMessage(message: Message)
    }

    private val audioPositions = mutableMapOf<Int, Int>()
    private var activeAudioHolder: AudioVH? = null

    init {
        rebuildAudioPositions()

        AudioPlayerManager.onActiveProgressUpdate = { progressMs ->
            activeAudioHolder?.updateProgress(progressMs)
        }

        AudioPlayerManager.onPlayStateChanged = { messageId, _ ->
            val pos = audioPositions[messageId]
            if (pos != null && pos >= 0 && pos < itemCount) {
                Handler(Looper.getMainLooper()).post {
                    notifyItemChanged(pos)
                }
            }
        }

        AudioPlayerManager.onPlaybackComplete = { messageId ->
            val pos = audioPositions[messageId]
            if (pos != null && pos >= 0 && pos < itemCount) {
                Handler(Looper.getMainLooper()).post {
                    notifyItemChanged(pos)
                }
            }
        }
    }

    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<Int>()

    fun isInSelectionMode() = isSelectionMode

    fun enterSelectionMode(messageId: Int) {
        isSelectionMode = true
        selectedIds.clear()
        selectedIds.add(messageId)
        notifyDataSetChanged()
        onMessageAction?.onSelectionChanged(1)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onMessageAction?.onSelectionChanged(0)
    }

    fun toggleSelectionAt(pos: Int) {
        val item = items.getOrNull(pos) ?: return
        if (item !is ChatListItem.MessageItem) return
        val id = item.message.id
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
            if (selectedIds.isEmpty()) {
                exitSelectionMode()
                return
            }
        } else {
            selectedIds.add(id)
        }
        notifyItemChanged(pos)
        onMessageAction?.onSelectionChanged(selectedIds.size)
    }

    fun addSelectionAt(pos: Int) {
        val item = items.getOrNull(pos) ?: return
        if (item !is ChatListItem.MessageItem) return
        if (selectedIds.add(item.message.id)) {
            notifyItemChanged(pos)
            onMessageAction?.onSelectionChanged(selectedIds.size)
        }
    }

    fun getSelectedMessages(): List<Message> =
        items.filterIsInstance<ChatListItem.MessageItem>()
            .map { it.message }
            .filter { selectedIds.contains(it.id) }

    fun getSelectedCount() = selectedIds.size
    fun getItemAt(pos: Int) = items.getOrNull(pos)

    private var searchQuery = ""
    private var highlightedMessageId = -1

    fun setSearchHighlight(q: String, id: Int = -1) {
        searchQuery = q
        highlightedMessageId = id
        notifyDataSetChanged()
    }

    fun clearSearchHighlight() {
        searchQuery = ""
        highlightedMessageId = -1
        notifyDataSetChanged()
    }

    companion object {
        private const val TYPE_TEXT_MINE = 0
        private const val TYPE_TEXT_OTHER = 1
        private const val TYPE_IMAGE_MINE = 2
        private const val TYPE_IMAGE_OTHER = 3
        private const val TYPE_AUDIO_MINE = 4
        private const val TYPE_AUDIO_OTHER = 5
        private const val TYPE_DATE_HEADER = 6

        fun buildItemList(messages: List<Message>): List<ChatListItem> {
            val items = mutableListOf<ChatListItem>()
            var lastDate = ""

            for (msg in messages) {
                val d = extractDate(msg.timestamp)
                if (d != lastDate) {
                    items.add(ChatListItem.DateHeader(formatDateHeader(d)))
                    lastDate = d
                }
                items.add(ChatListItem.MessageItem(msg))
            }
            return items
        }

        private fun extractDate(ts: String) =
            if (ts.length >= 10) ts.substring(0, 10) else ts

        private fun formatDateHeader(s: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val d = sdf.parse(s) ?: return s
                val today = Calendar.getInstance()
                val msg = Calendar.getInstance().apply { time = d }

                when {
                    today.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
                            today.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR) -> "Сегодня"

                    today.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
                            today.get(Calendar.DAY_OF_YEAR) - msg.get(Calendar.DAY_OF_YEAR) == 1 -> "Вчера"

                    else -> SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(d)
                }
            } catch (e: Exception) {
                s
            }
        }
    }

    override fun getItemViewType(pos: Int) = when (val item = items[pos]) {
        is ChatListItem.DateHeader -> TYPE_DATE_HEADER
        is ChatListItem.MessageItem -> {
            val m = item.message
            val mine = m.sender == currentUser
            when (m.type) {
                "image" -> if (mine) TYPE_IMAGE_MINE else TYPE_IMAGE_OTHER
                "audio" -> if (mine) TYPE_AUDIO_MINE else TYPE_AUDIO_OTHER
                else -> if (mine) TYPE_TEXT_MINE else TYPE_TEXT_OTHER
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (vt) {
            TYPE_DATE_HEADER -> DateVH(inf.inflate(R.layout.item_message_date, parent, false))
            TYPE_TEXT_MINE, TYPE_TEXT_OTHER -> TextVH(inf.inflate(R.layout.item_message, parent, false))
            TYPE_IMAGE_MINE, TYPE_IMAGE_OTHER -> ImageVH(inf.inflate(R.layout.item_message_image, parent, false))
            else -> AudioVH(inf.inflate(R.layout.item_message_audio, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        when (val item = items[pos]) {
            is ChatListItem.DateHeader -> (holder as DateVH).bind(item.date)
            is ChatListItem.MessageItem -> when (holder) {
                is TextVH -> holder.bind(item.message)
                is ImageVH -> holder.bind(item.message)
                is AudioVH -> {
                    holder.bind(item.message)
                    if (AudioPlayerManager.currentPlayingId == item.message.id) {
                        activeAudioHolder = holder
                    }
                }
            }
        }


        }

    override fun getItemCount() = items.size

    fun updateMessages(newMessages: List<Message>) {
        Log.e("ADAPTER", "🚨🚨🚨 updateMessages called with ${newMessages.size} messages")

        if (newMessages.isNotEmpty()) {
            Log.e("ADAPTER", "🚨 First: ${newMessages.first().text.take(20)}")
            Log.e("ADAPTER", "🚨 Last: ${newMessages.last().text.take(20)}")
        }

        val newItems = buildItemList(newMessages)
        Log.e("ADAPTER", "🚨 newItems size: ${newItems.size}, old items size: ${items.size}")

        // ✅ ЕСЛИ КОЛИЧЕСТВО ЭЛЕМЕНТОВ ИЗМЕНИЛОСЬ - ПРИНУДИТЕЛЬНО ОБНОВЛЯЕМ
        if (newItems.size != items.size) {
            Log.e("ADAPTER", "🚨 Size changed! Force updating...")
            items = newItems
            rebuildAudioPositions()
            notifyDataSetChanged()
            Log.e("ADAPTER", "🚨✅ UI force updated, now ${items.size} items")
            return
        }

        // ✅ ИНАЧЕ ИСПОЛЬЗУЕМ DiffUtil
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = items[oldPos]
                val new = newItems[newPos]

                if (old is ChatListItem.MessageItem && new is ChatListItem.MessageItem) {
                    return old.message.id == new.message.id
                }
                if (old is ChatListItem.DateHeader && new is ChatListItem.DateHeader) {
                    return old.date == new.date
                }
                return false
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = items[oldPos]
                val new = newItems[newPos]

                if (old is ChatListItem.MessageItem && new is ChatListItem.MessageItem) {
                    return old.message.text == new.message.text &&
                            old.message.isRead == new.message.isRead &&
                            old.message.isSent == new.message.isSent
                }
                return old == new
            }

            // ✅ ВАЖНО: Уведомляем DiffUtil о возможных изменениях
            override fun getChangePayload(oldPos: Int, newPos: Int): Any? {
                return if (oldPos != newPos) "POSITION_CHANGED" else null
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items = newItems
        rebuildAudioPositions()
        diffResult.dispatchUpdatesTo(this)

        Log.e("ADAPTER", "🚨✅ UI updated, now ${items.size} items")
    }

    fun getItemsCount() = items.size

    private fun rebuildAudioPositions() {
        audioPositions.clear()
        items.forEachIndexed { index, item ->
            if (item is ChatListItem.MessageItem && item.message.type == "audio") {
                audioPositions[item.message.id] = index
            }
        }
    }

    private fun formatTime(ts: String): String {
        return try {
            // ts = "2026-04-20 15:30:00.123" (UTC)
            val utcFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            utcFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = utcFormat.parse(ts.substringBefore("."))  // без миллисекунд

            val localFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            localFormat.timeZone = TimeZone.getDefault()
            localFormat.format(date)
        } catch (e: Exception) {
            ts.substring(11, 16)  // fallback
        }
    }

    private fun applyBubbleStyle(
        bubble: View,
        time: TextView,
        readStatus: TextView,
        msg: Message,
        isMine: Boolean
    ) {
        val ctx = bubble.context
        val p = bubble.layoutParams as LinearLayout.LayoutParams

        if (isMine) {
            p.gravity = Gravity.END
            p.marginStart = 60
            p.marginEnd = 0
            bubble.setBackgroundResource(R.drawable.bubble_sent)
            time.setTextColor(ctx.getColor(R.color.text_time_sent))

            readStatus.visibility = View.VISIBLE

            if (!msg.isSent) {
                // СООБЩЕНИЕ НЕ ОТПРАВЛЕНО (ОФФЛАЙН)
                readStatus.text = "🔄" // Значок повтора (или поставь ❗️)
                readStatus.setTextColor(ctx.getColor(android.R.color.holo_red_light))

                // Делаем значок кликабельным для повторной отправки!
                readStatus.setOnClickListener {
                    onMessageAction?.onRetryMessage(msg)
                }
            } else {
                // СООБЩЕНИЕ ОТПРАВЛЕНО УСПЕШНО
                readStatus.setOnClickListener(null) // Убираем клик

                if (msg.isRead) {
                    readStatus.text = "✓✓"
                    readStatus.setTextColor(ctx.getColor(R.color.read_check))
                } else {
                    readStatus.text = "✓"
                    readStatus.setTextColor(ctx.getColor(R.color.text_time_sent))
                }
            }
        }else {
            // ✅ ДОБАВЬ ЭТО!
            p.gravity = Gravity.START
            p.marginStart = 0
            p.marginEnd = 60
            bubble.setBackgroundResource(R.drawable.bubble_received)  // ← ВАЖНО!
            time.setTextColor(ctx.getColor(R.color.text_time_received))
            readStatus.visibility = View.GONE  // чужие сообщения без статуса
        }

        bubble.layoutParams = p
        time.text = formatTime(msg.timestamp)
    }

    private fun applySelectionBg(itemView: View, msg: Message) {
        itemView.setBackgroundColor(
            if (isSelectionMode && selectedIds.contains(msg.id)) 0x302196F3 else 0x00000000
        )
    }

    inner class DateVH(v: View) : RecyclerView.ViewHolder(v) {
        private val t: TextView = v.findViewById(R.id.date_text)
        fun bind(date: String) {
            t.text = date
        }
    }

    inner class TextVH(v: View) : RecyclerView.ViewHolder(v) {
        private val bubble: LinearLayout = v.findViewById(R.id.message_bubble)
        private val text: TextView = v.findViewById(R.id.message_text)
        private val time: TextView = v.findViewById(R.id.message_time)
        private val readStatus: TextView = v.findViewById(R.id.message_read_status)

        fun bind(msg: Message) {
            if (searchQuery.isNotEmpty() && msg.text.contains(searchQuery, true)) {
                val sp = SpannableString(msg.text)
                var s = msg.text.lowercase().indexOf(searchQuery.lowercase())
                while (s >= 0) {
                    sp.setSpan(
                        BackgroundColorSpan(0xFFFFEB3B.toInt()),
                        s,
                        s + searchQuery.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    s = msg.text.lowercase().indexOf(searchQuery.lowercase(), s + searchQuery.length)
                }
                text.text = sp
            } else {
                text.text = msg.text
            }

            text.setTextColor(
                if (msg.sender == currentUser)
                    itemView.context.getColor(R.color.text_bubble_sent)
                else
                    itemView.context.getColor(R.color.text_bubble_received)
            )

            itemView.alpha = when {
                msg.id == highlightedMessageId -> 1f
                searchQuery.isNotEmpty() && !msg.text.contains(searchQuery, true) -> 0.4f
                else -> 1f
            }

            text.autoLinkMask = Linkify.WEB_URLS
            text.movementMethod = LinkMovementMethod.getInstance()
            text.setLinkTextColor(itemView.context.getColor(R.color.primary))

            val replyCont: LinearLayout = itemView.findViewById(R.id.reply_container)
            val replyTxt: TextView = itemView.findViewById(R.id.reply_text)

            if (!msg.replyToText.isNullOrEmpty()) {
                replyCont.visibility = View.VISIBLE
                replyTxt.text = msg.replyToText
            } else {
                replyCont.visibility = View.GONE
            }

            val fav: TextView = itemView.findViewById(R.id.message_favorite)
            fav.visibility = if (msg.isFavorite) View.VISIBLE else View.GONE

            itemView.findViewById<View>(R.id.select_checkbox)?.visibility = View.GONE

            applyBubbleStyle(bubble, time, readStatus, msg, msg.sender == currentUser)
            applySelectionBg(itemView, msg)

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) toggleSelectionAt(p)
                }
            }

            itemView.setOnLongClickListener(null)
        }
    }

    inner class ImageVH(v: View) : RecyclerView.ViewHolder(v) {
        private val bubble: LinearLayout = v.findViewById(R.id.image_bubble)
        private val img: ImageView = v.findViewById(R.id.message_image)
        private val time: TextView = v.findViewById(R.id.message_time)
        private val readStatus: TextView = v.findViewById(R.id.message_read_status)

        fun bind(msg: Message) {
            msg.mediaUrl?.let { url ->
                if (url.startsWith("http")) {
                    Glide.with(itemView.context)
                        .load(url)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(img)
                } else {
                    Glide.with(itemView.context)
                        .load(File(url))
                        .into(img)
                }
            }

            applyBubbleStyle(bubble, time, readStatus, msg, msg.sender == currentUser)
            itemView.alpha = if (searchQuery.isNotEmpty()) 0.4f else 1f
            itemView.findViewById<View>(R.id.select_checkbox)?.visibility = View.GONE
            applySelectionBg(itemView, msg)

            img.setOnClickListener {
                if (isSelectionMode) {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) toggleSelectionAt(p)
                } else {
                    val i = Intent(itemView.context, ImageViewerActivity::class.java)
                    i.putExtra("imagePath", msg.mediaUrl)
                    itemView.context.startActivity(i)
                }
            }

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) toggleSelectionAt(p)
                }
            }

            itemView.setOnLongClickListener(null)
        }
    }

    inner class AudioVH(v: View) : RecyclerView.ViewHolder(v) {
        private val bubble: LinearLayout = v.findViewById(R.id.audio_bubble)
        private val playBtn: ImageButton = v.findViewById(R.id.play_button)
        private val dur: TextView = v.findViewById(R.id.audio_duration)
        private val seekbar: SeekBar = v.findViewById(R.id.audio_seekbar)
        private val time: TextView = v.findViewById(R.id.message_time)
        private val readStatus: TextView = v.findViewById(R.id.message_read_status)

        private var boundMessageId: Int = -1

        fun bind(msg: Message) {
            boundMessageId = msg.id

            val fullDurationMs =
                if (msg.duration > 0) msg.duration * 1000
                else AudioPlayerManager.getSavedDuration(msg.id)

            val savedProgress = AudioPlayerManager.getSavedProgress(msg.id)

            seekbar.max = if (fullDurationMs > 0) fullDurationMs else 1

            val isCurrent = AudioPlayerManager.currentPlayingId == msg.id
            val isPlayingNow = AudioPlayerManager.isPlayingMessage(msg.id)

            val progressToShow = if (isCurrent) {
                AudioPlayerManager.currentPosition
            } else {
                savedProgress
            }.coerceAtMost(seekbar.max)

            seekbar.progress = progressToShow
            updateDurationText(progressToShow, msg.duration)

            playBtn.setImageResource(
                if (isPlayingNow) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )

            applyBubbleStyle(bubble, time, readStatus, msg, msg.sender == currentUser)
            itemView.alpha = if (searchQuery.isNotEmpty()) 0.4f else 1f
            itemView.findViewById<View>(R.id.select_checkbox)?.visibility = View.GONE
            applySelectionBg(itemView, msg)

            playBtn.setOnClickListener {
                if (isSelectionMode) {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) toggleSelectionAt(p)
                    return@setOnClickListener
                }

                msg.mediaUrl?.let { url ->
                    AudioPlayerManager.play(msg.id, url)
                }
            }

            seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, user: Boolean) {
                    if (user) {
                        updateDurationText(prog, msg.duration)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val target = sb?.progress ?: return

                    if (AudioPlayerManager.currentPlayingId == msg.id) {
                        AudioPlayerManager.seekTo(target)
                    } else {
                        AudioPlayerManager.saveProgressForMessage(msg.id, target)
                    }
                }
            })

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) toggleSelectionAt(p)
                }
            }

            itemView.setOnLongClickListener(null)
        }

        fun updateProgress(progressMs: Int) {
            if (boundMessageId == AudioPlayerManager.currentPlayingId) {
                seekbar.progress = progressMs.coerceAtMost(seekbar.max)
                val sec = progressMs / 1000
                dur.text = String.format("%d:%02d", sec / 60, sec % 60)
            }
        }

        private fun updateDurationText(progressMs: Int, fallbackDurationSec: Int) {
            val sec = progressMs / 1000
            if (progressMs > 0) {
                dur.text = String.format("%d:%02d", sec / 60, sec % 60)
            } else {
                dur.text = String.format("%d:%02d", fallbackDurationSec / 60, fallbackDurationSec % 60)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AudioVH && holder == activeAudioHolder) {
            activeAudioHolder = null
        }
    }
}