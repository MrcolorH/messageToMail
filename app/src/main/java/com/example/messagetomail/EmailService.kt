package com.example.messagetomail

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.messagetomail.Utils.AppLogger
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
        AppLogger.i("EmailService", "EmailService onCreate")
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("EmailService", "EmailService onStartCommand, startId=$startId")

        val notification = NotificationHelper.createForegroundNotification(
            this,
            "短信转邮件服务",
            "正在发送邮件..."
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(FOREGROUND_SERVICE_ID, notification)
            AppLogger.d("EmailService", "前台服务已启动")
        }

        val devicePhone = intent?.getStringExtra(EXTRA_DEVICE_PHONE)
        val sender = intent?.getStringExtra(EXTRA_SENDER)
        val messageBody = intent?.getStringExtra(EXTRA_MESSAGE_BODY)

        AppLogger.i("EmailService", "参数: devicePhone=$devicePhone, sender=$sender, bodyLength=${messageBody?.length}")

        if (devicePhone != null && sender != null && messageBody != null) {
            serviceScope.launch {
                try {
                    AppLogger.i("EmailService", "开始发送邮件...")
                    sendEmail(this@EmailService, devicePhone, sender, messageBody)
                    AppLogger.i("EmailService", "邮件发送流程结束")

                    NotificationHelper.showNotification(
                        this@EmailService,
                        "邮件发送成功",
                        "短信已成功转发到邮箱",
                        2001
                    )
                } catch (e: Exception) {
                    AppLogger.e("EmailService", "邮件发送异常", e)

                    NotificationHelper.showNotification(
                        this@EmailService,
                        "邮件发送失败",
                        "短信转发失败: ${e.message}",
                        2002
                    )
                } finally {
                    AppLogger.i("EmailService", "停止前台服务")
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
            AppLogger.e("EmailService", "缺少必要参数")
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
        AppLogger.i("EmailService", "EmailService onDestroy")
    }
}
