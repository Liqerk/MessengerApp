package com.example.myapplication

import android.app.Application
import com.example.myapplication.transport.TransportManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        TransportManager.init(this)
    }
}