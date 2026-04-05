package com.example.myapplication

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.io.File
import android.Manifest

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
class ImageViewerActivity : AppCompatActivity() {

    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        imagePath = intent.getStringExtra("imagePath")

        val imageView: ImageView = findViewById(R.id.fullscreen_image)
        val closeButton: ImageButton = findViewById(R.id.close_button)
        val saveButton: ImageButton = findViewById(R.id.save_button)

        // Загружаем фото
        imagePath?.let { path ->
            if (path.startsWith("http")) {
                Glide.with(this).load(path).into(imageView)
            } else {
                Glide.with(this).load(File(path)).into(imageView)
            }
        }

        closeButton.setOnClickListener { finish() }

        saveButton.setOnClickListener { saveImageToGallery() }

        // Нажатие на фон — закрыть
        imageView.setOnClickListener {
            // Переключаем видимость кнопок
            val newVisibility = if (closeButton.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
            closeButton.visibility = newVisibility
            saveButton.visibility = newVisibility
        }
    }

    private fun saveImageToGallery() {
        val path = imagePath ?: return

        // Проверка для Android 9 (API 28) и ниже
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
                return // Ждем результат в onRequestPermissionsResult
            }
        }

        try {
            val file = File(path)
            if (!file.exists()) return

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val fileName = "MSG_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Messenger")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { stream ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.insertImage(contentResolver, bitmap, fileName, "Messenger photo")
            }
            Toast.makeText(this, "✅ Сохранено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show()
        }
    }

    // Обработка ответа пользователя
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveImageToGallery()
        } else {
            Toast.makeText(this, "Нужно разрешение на сохранение", Toast.LENGTH_SHORT).show()
        }
    }
}