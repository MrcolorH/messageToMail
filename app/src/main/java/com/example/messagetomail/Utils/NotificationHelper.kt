package com.example.messagetomail.Utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.messagetomail.R

object NotificationHelper {
    private const val CHANNEL_ID = "sms_email_channel"
    private const val CHANNEL_NAME = "短信转邮件通知"
    private const val CHANNEL_DESCRIPTION = "用于显示短信转邮件的状态通知"
    
    /**
     * 创建通知渠道（Android 8.0+需要）
     */
    fun createNotificationChannel(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(false)
                    enableVibration(false)
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "通知渠道创建失败(权限不足)", e)
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "通知渠道创建异常", e)
        }
    }
    
    /**
     * 创建前台服务通知
     */
    fun createForegroundNotification(context: Context, title: String, content: String): android.app.Notification {
        createNotificationChannel(context)
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 显示普通通知
     */
    fun showNotification(context: Context, title: String, content: String, notificationId: Int = 1) {
        createNotificationChannel(context)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // 处理通知权限被拒绝的情况
            android.util.Log.w("NotificationHelper", "通知权限被拒绝", e)
        }
    }
}