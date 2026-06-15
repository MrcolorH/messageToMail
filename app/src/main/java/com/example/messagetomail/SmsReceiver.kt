package com.example.messagetomail

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import com.example.messagetomail.Utils.AppLogger

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.provider.Telephony.SMS_RECEIVED") {
            AppLogger.i("SmsReceiver", "=== SmsReceiver.onReceive 被调用 ===")

            val bundle = intent.extras
            val pdus = bundle?.get("pdus") as? Array<*>
            val format = bundle?.getString("format")

            if (pdus == null) {
                AppLogger.w("SmsReceiver", "PDU 数据为空")
                return
            }

            val messages = pdus.mapNotNull { pdu ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                } catch (e: Exception) {
                    AppLogger.e("SmsReceiver", "PDU 解析失败", e)
                    null
                }
            }

            if (messages.isEmpty()) {
                AppLogger.w("SmsReceiver", "未能解析出任何短信")
                return
            }

            val sender = messages[0].originatingAddress ?: "未知号码"
            val messageBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

            AppLogger.i("SmsReceiver", "短信解析完成: sender=$sender, bodyLength=${messageBody.length}")
            AppLogger.i("SmsReceiver", "将短信数据交给 SmsObserverService 统一处理")

            context?.let { ctx ->
                val serviceIntent = SmsObserverService.createIntent(ctx).apply {
                    putExtra(SmsObserverService.EXTRA_SMS_SENDER, sender)
                    putExtra(SmsObserverService.EXTRA_SMS_BODY, messageBody)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(serviceIntent)
                    } else {
                        ctx.startService(serviceIntent)
                    }
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    AppLogger.e("SmsReceiver", "无法从后台启动前台服务(Android 12+限制)", e)
                } catch (e: Exception) {
                    AppLogger.e("SmsReceiver", "启动短信处理服务异常", e)
                }
            }
        }
    }
}
