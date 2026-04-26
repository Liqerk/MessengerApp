package com.example.myapplication

import android.app.Application
import com.example.myapplication.transport.ServerTransport
import com.example.myapplication.transport.TransportManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLifecycleTracker)
        NotificationHelper.createChannel(applicationContext)
        Repository.init(this)
        TransportManager.init(this)
        ServerTransport.appContext = this
    }
}