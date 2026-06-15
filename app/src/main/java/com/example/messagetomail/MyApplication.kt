package com.example.messagetomail

import androidx.multidex.MultiDexApplication
import com.example.messagetomail.Utils.AppLogger
import com.example.messagetomail.Utils.NotificationHelper

class MyApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        // 初始化日志
        AppLogger.getInstance(this)
        AppLogger.i("App", "应用启动")

        // 初始化通知渠道
        try {
            NotificationHelper.createNotificationChannel(this)
        } catch (e: Exception) {
            AppLogger.e("App", "通知渠道初始化失败", e)
        }
    }
}