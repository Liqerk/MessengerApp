package com.example.myapplication.dpi.data

const val STARTED_BROADCAST = "com.example.myapplication.dpi.STARTED"
const val STOPPED_BROADCAST = "com.example.myapplication.dpi.STOPPED"
const val FAILED_BROADCAST = "com.example.myapplication.dpi.FAILED"

const val SENDER = "sender"

enum class Sender(val senderName: String) {
    Proxy("Proxy"),
    VPN("VPN")
}
