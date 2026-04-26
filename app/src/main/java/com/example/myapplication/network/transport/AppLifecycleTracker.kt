package com.example.myapplication

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

    var isAppInForeground = true
        private set

    private var activeActivities = 0

    override fun onActivityStarted(activity: Activity) {
        activeActivities++
        isAppInForeground = true
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivities--
        if (activeActivities <= 0) {
            isAppInForeground = false
            activeActivities = 0
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}