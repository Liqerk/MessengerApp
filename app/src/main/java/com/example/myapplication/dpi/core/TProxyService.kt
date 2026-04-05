package com.example.myapplication.dpi.core

object TProxyService {
    fun TProxyStartService(configPath: String, fd: Int) {
        hev.htproxy.TProxyService.TProxyStartService(configPath, fd)
    }

    fun TProxyStopService() {
        hev.htproxy.TProxyService.TProxyStopService()
    }
}
