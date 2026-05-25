package com.example.v11

import android.app.Application
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.example.v11.Utils.NotificationHelper

class MyApplication : MultiDexApplication() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化通知渠道
        NotificationHelper.createNotificationChannel(this)
    }
    
    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        // 初始化MultiDex以支持vivo系统
        MultiDex.install(this)
    }
}