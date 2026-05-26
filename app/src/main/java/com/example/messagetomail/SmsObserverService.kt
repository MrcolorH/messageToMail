package com.example.messagetomail

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import com.example.messagetomail.Utils.AppLogger
import com.example.messagetomail.Utils.FeishuAppRobotSender
import com.example.messagetomail.Utils.FeishuRobotSender
import com.example.messagetomail.Utils.NotificationHelper
import com.example.messagetomail.Utils.SmsContentFormatter
import com.example.messagetomail.Utils.SmsFilter
import com.example.messagetomail.Utils.WecomRobotSender
import com.example.messagetomail.repository.EmailConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SmsObserverService : Service() {

    private var smsObserver: SmsContentObserver? = null
    private var lastProcessedId: Long = -1L
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: EmailConfigRepository

    private val recentForwards = mutableListOf<Triple<String, String, Long>>()

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("SmsObserver", "SmsObserverService onCreate")
        repository = EmailConfigRepository(this)
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("SmsObserver", "SmsObserverService onStartCommand")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = NotificationHelper.createForegroundNotification(
                this,
                "短信监听服务",
                "正在监听新短信..."
            )
            startForeground(FOREGROUND_SERVICE_ID, notification)
        }

        val smsSender = intent?.getStringExtra(EXTRA_SMS_SENDER)
        val smsBody = intent?.getStringExtra(EXTRA_SMS_BODY)
        if (smsSender != null && smsBody != null) {
            AppLogger.i("SmsObserver", "收到广播传入的短信: sender=$smsSender")
            forwardSms(smsSender, smsBody)
        }

        if (smsObserver == null) {
            smsObserver = SmsContentObserver(Handler(Looper.getMainLooper()))
            contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                smsObserver!!
            )
            AppLogger.i("SmsObserver", "短信数据库 ContentObserver 已注册")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
            AppLogger.i("SmsObserver", "短信数据库 ContentObserver 已注销")
        }
    }

    private fun isRecentlyForwarded(address: String, body: String): Boolean {
        val now = System.currentTimeMillis()
        val prefix = body.take(100)
        recentForwards.removeAll { now - it.third > 10_000 }
        return recentForwards.any { it.first == address && it.second == prefix }
    }

    private fun markForwarded(address: String, body: String) {
        recentForwards.add(Triple(address, body.take(100), System.currentTimeMillis()))
    }

    private fun forwardSms(address: String, body: String) {
        if (isRecentlyForwarded(address, body)) {
            AppLogger.d("SmsObserver", "短信短期内已转发过，跳过去重: sender=$address")
            return
        }
        markForwarded(address, body)

        val filterConfig = repository.getSmsFilterConfigBlocking()
        if (!SmsFilter.shouldForward(body, filterConfig)) {
            AppLogger.i("SmsObserver", "短信被过滤规则拦截，跳过转发")
            return
        }

        val formattedBody = SmsContentFormatter.format(body, address)
        AppLogger.d("SmsObserver", "格式化后消息: ${formattedBody.take(200)}")

        NotificationHelper.showNotification(
            this@SmsObserverService,
            "短信监听触发",
            "发现新短信来自 $address",
            address.hashCode()
        )

        val emailEnabled = repository.isEmailEnabledBlocking()
        val wecomEnabled = repository.isWecomEnabledBlocking()
        val wecomConfig = repository.getWecomConfigBlocking()
        val feishuEnabled = repository.isFeishuEnabledBlocking()
        val feishuConfig = repository.getFeishuConfigBlocking()
        val feishuAppEnabled = repository.isFeishuAppEnabledBlocking()
        val feishuAppConfig = repository.getFeishuAppConfigBlocking()

        AppLogger.i("SmsObserver", "渠道配置: emailEnabled=$emailEnabled, wecomEnabled=$wecomEnabled, feishuEnabled=$feishuEnabled, feishuAppEnabled=$feishuAppEnabled")

        if (emailEnabled) {
            val emailIntent = Intent(this@SmsObserverService, EmailService::class.java).apply {
                putExtra(EmailService.EXTRA_DEVICE_PHONE, "本机号码")
                putExtra(EmailService.EXTRA_SENDER, address)
                putExtra(EmailService.EXTRA_MESSAGE_BODY, formattedBody)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(emailIntent)
            } else {
                startService(emailIntent)
            }
            AppLogger.i("SmsObserver", "已启动 EmailService 发送邮件")
        }

        if (wecomEnabled && wecomConfig != null) {
            serviceScope.launch {
                val result = WecomRobotSender.send(
                    wecomConfig.webhookUrl,
                    "",
                    formattedBody
                )
                result.onSuccess {
                    AppLogger.i("SmsObserver", "企微机器人发送成功")
                }.onFailure {
                    AppLogger.e("SmsObserver", "企微机器人发送失败: ${it.message}")
                }
            }
        }

        if (feishuEnabled && feishuConfig != null) {
            serviceScope.launch {
                val result = FeishuRobotSender.send(
                    feishuConfig.webhookUrl,
                    feishuConfig.secret,
                    "",
                    formattedBody
                )
                result.onSuccess {
                    AppLogger.i("SmsObserver", "飞书机器人发送成功")
                }.onFailure {
                    AppLogger.e("SmsObserver", "飞书机器人发送失败: ${it.message}")
                }
            }
        }

        if (feishuAppEnabled && feishuAppConfig != null) {
            serviceScope.launch {
                val result = FeishuAppRobotSender.sendToMultiple(
                    feishuAppConfig.appId,
                    feishuAppConfig.appSecret,
                    feishuAppConfig.receivers,
                    "",
                    formattedBody
                )
                result.onSuccess {
                    AppLogger.i("SmsObserver", "飞书应用机器人发送成功")
                }.onFailure {
                    AppLogger.e("SmsObserver", "飞书应用机器人发送失败: ${it.message}")
                }
            }
        }
    }

    private inner class SmsContentObserver(val handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            AppLogger.i("SmsObserver", "短信数据库发生变化, uri=$uri")

            handler.postDelayed({
                queryAndProcess(uri)
            }, 300)
        }

        private fun queryAndProcess(specificUri: Uri?) {
            val queryUri = if (specificUri != null && specificUri != Telephony.Sms.CONTENT_URI) {
                specificUri
            } else {
                Telephony.Sms.CONTENT_URI
            }

            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            )

            val cursor = contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))

                    if (id == lastProcessedId) {
                        AppLogger.d("SmsObserver", "短信 id=$id 已处理过，跳过")
                        return
                    }

                    val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "未知号码"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    val sixtySecondsAgo = System.currentTimeMillis() - 60_000
                    if (date < sixtySecondsAgo) {
                        AppLogger.d("SmsObserver", "短信时间太久远 (${date}), 忽略")
                        return
                    }

                    lastProcessedId = id

                    AppLogger.i("SmsObserver", "检测到新短信: id=$id, sender=$address, bodyLength=${body.length}, date=$date")

                    forwardSms(address, body)
                } else {
                    AppLogger.d("SmsObserver", "cursor moveToFirst 失败，queryUri=$queryUri")
                }
            } ?: run {
                AppLogger.w("SmsObserver", "查询短信数据库返回 null cursor, uri=$queryUri")
            }
        }
    }

    companion object {
        private const val FOREGROUND_SERVICE_ID = 1002
        const val EXTRA_SMS_SENDER = "sms_sender"
        const val EXTRA_SMS_BODY = "sms_body"

        fun createIntent(context: android.content.Context): Intent {
            return Intent(context, SmsObserverService::class.java)
        }
    }
}
