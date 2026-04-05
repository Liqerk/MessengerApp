package com.example.myapplication.dpi.services

import com.example.myapplication.dpi.data.AppStatus
import com.example.myapplication.dpi.data.Mode

var appStatus = AppStatus.Halted to Mode.VPN
    private set

fun setStatus(status: AppStatus, mode: Mode) {
    appStatus = status to mode
}
