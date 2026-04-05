package com.example.myapplication.dpi

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.myapplication.dpi.data.Mode
import com.example.myapplication.dpi.services.ServiceManager

class DpiManager(private val context: Context) {

    private fun isVpnPermissionGranted(): Boolean {
        return VpnService.prepare(context) == null
    }

    fun prepareVpn(): Intent? {
        return if (!isVpnPermissionGranted()) {
            VpnService.prepare(context)
        } else {
            null
        }
    }

    fun start() {
        if (!isVpnPermissionGranted()) return
        ServiceManager.start(context, Mode.VPN)
        saveState(true)
    }

    fun stop() {
        ServiceManager.stop(context)
        saveState(false)
    }

    fun isActive(): Boolean {
        // Можно проверять через глобальный статус
        return com.example.myapplication.dpi.services.appStatus.first == com.example.myapplication.dpi.data.AppStatus.Running
    }

    fun wasEnabled(): Boolean {
        return context.getSharedPreferences("dpi", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)
    }

    private fun saveState(enabled: Boolean) {
        context.getSharedPreferences("dpi", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", enabled)
            .apply()
    }
}