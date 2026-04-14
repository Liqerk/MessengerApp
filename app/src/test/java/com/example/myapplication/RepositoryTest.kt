package com.example.myapplication

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Robolectric нужен, чтобы Android-классы (SQLite) работали в тестах на ПК
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {

    private lateinit var dbHelper: DbHelper
    private lateinit var repository: Repository

    @Before
    fun setup() {
        // Создаем БД В ПАМЯТИ (:memory:), чтобы не создавать файлы на диске
        val context = RuntimeEnvironment.getApplication()
        dbHelper = object : SQLiteOpenHelper(context, ":memory:", null, 1) {
            override fun onCreate(db: SQLiteDatabase?) {
                // Копируем твою логику создания таблиц
                db?.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, login TEXT, password TEXT)")
                db?.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY, sender TEXT, text TEXT)")
            }
            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
        }
        repository = com.example.myapplication.Repository(context)
        // Хак: подменяем приватный dbHelper на наш тестовый (через рефлексию или сделав его public/internal)
        // Для простоты, давай представим, что Repository принимает DbHelper в конструкторе (сделай это!)
    }

    @Test
    fun `test addUser and getUser`() {
        val user = User(login = "test", mail = "test@test.ru", password = "123")

        // 1. Проверяем добавление
        val success = repository.register(user.login, user.mail, user.password)
        assertTrue(success)

        // 2. Проверяем получение
        val loadedUser = repository.getUser(user.login, user.password) // Если метод есть
        // Или проверим через поиск
        val found = repository.searchUsers(user.login, "")
        assertEquals(1, found.size)
        assertEquals("test", found[0].login)
    }
}