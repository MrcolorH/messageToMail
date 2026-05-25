package com.example.messagetomail

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.messagetomail.Utils.NotificationHelper
import com.example.messagetomail.Utils.sendEmail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EmailService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    companion object {
        private const val FOREGROUND_SERVICE_ID = 1001
        const val EXTRA_DEVICE_PHONE = "device_phone"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_MESSAGE_BODY = "message_body"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("EmailService", "EmailService created")
        
        // 创建通知渠道
        NotificationHelper.createNotificationChannel(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EmailService", "EmailService started")
        
        // 启动前台服务
        val notification = NotificationHelper.createForegroundNotification(
            this,
            "短信转邮件服务",
            "正在发送邮件..."
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(FOREGROUND_SERVICE_ID, notification)
        }
        
        // 获取传递的参数
        val devicePhone = intent?.getStringExtra(EXTRA_DEVICE_PHONE)
        val sender = intent?.getStringExtra(EXTRA_SENDER)
        val messageBody = intent?.getStringExtra(EXTRA_MESSAGE_BODY)
        
        if (devicePhone != null && sender != null && messageBody != null) {
            // 在协程中发送邮件
            serviceScope.launch {
                try {
                    sendEmail(this@EmailService, devicePhone, sender, messageBody)
                    Log.d("EmailService", "邮件发送完成")
                    
                    // 显示成功通知
                    NotificationHelper.showNotification(
                        this@EmailService,
                        "邮件发送成功",
                        "短信已成功转发到邮箱",
                        2001
                    )
                } catch (e: Exception) {
                    Log.e("EmailService", "邮件发送失败", e)
                    
                    // 显示失败通知
                    NotificationHelper.showNotification(
                        this@EmailService,
                        "邮件发送失败",
                        "短信转发失败: ${e.message}",
                        2002
                    )
                } finally {
                    // 停止前台服务
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
        } else {
            Log.e("EmailService", "缺少必要参数")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("EmailService", "EmailService destroyed")
    }
}