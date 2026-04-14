package com.example.myapplication.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // Получаем сообщения для чата (авто-обновление!)
    @Query("SELECT * FROM messages WHERE (sender = :u1 AND receiver = :u2) OR (sender = :u2 AND receiver = :u1) ORDER BY id ASC")
    fun getChatMessages(u1: String, u2: String): Flow<List<MessageEntity>>

    // Вставка
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    // Удаление
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Int)

    // Обновление текста
    @Query("UPDATE messages SET text = :text WHERE id = :id")
    suspend fun updateText(id: Int, text: String)
}