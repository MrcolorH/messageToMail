package com.example.messagetomail

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
import com.example.messagetomail.Utils.AppLogger
import com.example.messagetomail.Utils.NotificationHelper
import com.example.messagetomail.Utils.SmsContentFormatter
import com.example.messagetomail.Utils.SmsFilter
import com.example.messagetomail.repository.EmailConfigRepository

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        AppLogger.i("SmsReceiver", "=== SmsReceiver.onReceive 被调用 ===")
        AppLogger.d("SmsReceiver", "Intent action: ${intent?.action}")
        AppLogger.d("SmsReceiver", "Context: ${context?.javaClass?.simpleName}")
        AppLogger.d("SmsReceiver", "系统信息: ${Build.MANUFACTURER} ${Build.MODEL}")

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
            AppLogger.i("SmsReceiver", "收到 SMS_RECEIVED 广播")
            val bundle = intent.extras
            val pdus = bundle?.get("pdus") as Array<*>
            val format = bundle?.getString("format")
            AppLogger.d("SmsReceiver", "PDU 数量: ${pdus.size}, format=$format")

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
            AppLogger.i("SmsReceiver", "短信解析完成: sender=$sender, bodyLength=${messageBody.length}")

            if (context != null) {
                val repository = EmailConfigRepository(context)

                // 应用过滤规则
                val filterConfig = repository.getSmsFilterConfigBlocking()
                if (!SmsFilter.shouldForward(messageBody, filterConfig)) {
                    AppLogger.i("SmsReceiver", "短信被过滤规则拦截，跳过转发")
                    return
                }

                // 格式化消息内容
                val formattedBody = SmsContentFormatter.format(messageBody, sender)
                AppLogger.d("SmsReceiver", "格式化后消息: ${formattedBody.take(200)}")

                // 发送邮件
                val emailEnabled = repository.isEmailEnabledBlocking()
                if (!emailEnabled) {
                    AppLogger.i("SmsReceiver", "邮件转发已禁用，跳过")
                    return
                }
                AppLogger.i("SmsReceiver", "触发邮件转发流程")
                var devicePhoneNumber = "未知号码"
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    devicePhoneNumber = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            telephonyManager.line1Number ?: "未知号码"
                        } else {
                            @Suppress("DEPRECATION")
                            telephonyManager.line1Number ?: "未知号码"
                        }
                    } catch (e: SecurityException) {
                        AppLogger.e("SmsReceiver", "无法获取手机号码", e)
                        "未知号码"
                    }
                    // 处理手机号码格式
                    if (devicePhoneNumber != "未知号码") {
                        if (devicePhoneNumber.startsWith("+86")) {
                            devicePhoneNumber = devicePhoneNumber.substring(3)
                        }
                        if (devicePhoneNumber.length >= 11) {
                            devicePhoneNumber = devicePhoneNumber.substring(0, 3) + "****" + devicePhoneNumber.substring(7)
                        }
                    }
                    AppLogger.i("SmsReceiver", "设备号码获取成功: $devicePhoneNumber")
                } else {
                    AppLogger.w("SmsReceiver", "缺少READ_PHONE_STATE权限，无法获取设备号码")
                }

                // 使用前台服务发送邮件（适配Android 8.1+）
                val emailIntent = Intent(context, EmailService::class.java).apply {
                    putExtra(EmailService.EXTRA_DEVICE_PHONE, devicePhoneNumber)
                    putExtra(EmailService.EXTRA_SENDER, sender)
                    putExtra(EmailService.EXTRA_MESSAGE_BODY, formattedBody)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(emailIntent)
                        AppLogger.i("SmsReceiver", "前台邮件服务已启动 (Android O+)")
                    } else {
                        context.startService(emailIntent)
                        AppLogger.i("SmsReceiver", "普通邮件服务已启动")
                    }
                } catch (e: Exception) {
                    AppLogger.e("SmsReceiver", "启动邮件服务失败", e)
                    NotificationHelper.showNotification(
                        context,
                        "短信转邮件失败",
                        "无法启动邮件发送服务",
                        3001
                    )
                }
            } else {
                AppLogger.e("SmsReceiver", "context 为 null，无法处理短信")
            }
        } else {
            AppLogger.w("SmsReceiver", "未处理的广播 action: ${intent?.action}")
        }
    }
}
