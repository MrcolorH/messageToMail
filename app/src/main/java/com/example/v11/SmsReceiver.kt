package com.example.v11

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import com.example.v11.Utils.NotificationHelper
import com.example.v11.model.LogViewModel

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("SmsService", "=== SmsReceiver.onReceive 被调用 ===")
        Log.d("SmsService", "Intent action: ${intent?.action}")
        Log.d("SmsService", "Context: ${context?.javaClass?.simpleName}")
        Log.d("SmsService", "系统信息: ${Build.MANUFACTURER} ${Build.MODEL}")
        
        // 显示通知确认接收器被触发
        context?.let {
            NotificationHelper.showNotification(
                it,
                "短信接收器触发",
                "检测到短信广播，准备处理...",
                9999
            )
        }
        
        if (intent?.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            val pdus = bundle?.get("pdus") as Array<*>
            val format = bundle?.getString("format")
            val messages = pdus.map { pdu -> 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }
            }
            val sender = messages[0].originatingAddress
            val messageBody = messages.joinToString(separator = "") { it.messageBody }
            // 发送邮件
            if (context != null) {
                Log.d("SmsService", "触发sms接收器")
                Log.d("SmsService", context.javaClass.name)
                var devicePhoneNumber = "未知号码"
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    devicePhoneNumber = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Android 13+ 可能需要额外的权限处理
                            telephonyManager.line1Number ?: "未知号码"
                        } else {
                            @Suppress("DEPRECATION")
                            telephonyManager.line1Number ?: "未知号码"
                        }
                    } catch (e: SecurityException) {
                        Log.w("SmsService", "无法获取手机号码", e)
                        "未知号码"
                    }
                    // 处理手机号码格式
                    if (devicePhoneNumber != "未知号码") {
                        // 去掉+86前缀
                        if (devicePhoneNumber.startsWith("+86")) {
                            devicePhoneNumber = devicePhoneNumber.substring(3)
                        }
                        // 对中间4位进行脱敏处理
                        if (devicePhoneNumber.length >= 11) {
                            devicePhoneNumber = devicePhoneNumber.substring(0, 3) + "****" + devicePhoneNumber.substring(7)
                        }
                    }
                } else {
                    Log.w("SmsService", "缺少READ_PHONE_STATE权限，无法获取设备号码")
                }
                
                // 使用前台服务发送邮件（适配Android 8.1+）
                val emailIntent = Intent(context, EmailService::class.java).apply {
                    putExtra(EmailService.EXTRA_DEVICE_PHONE, devicePhoneNumber)
                    putExtra(EmailService.EXTRA_SENDER, sender)
                    putExtra(EmailService.EXTRA_MESSAGE_BODY, messageBody)
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Android 8.0+ 使用前台服务
                        context.startForegroundService(emailIntent)
                    } else {
                        // Android 8.0以下使用普通服务
                        context.startService(emailIntent)
                    }
                    Log.d("SmsService", "邮件发送服务已启动")
                } catch (e: Exception) {
                    Log.e("SmsService", "启动邮件服务失败", e)
                    // 显示错误通知
                    NotificationHelper.showNotification(
                        context,
                        "短信转邮件失败",
                        "无法启动邮件发送服务",
                        3001
                    )
                }
            }
        }
    }
}
