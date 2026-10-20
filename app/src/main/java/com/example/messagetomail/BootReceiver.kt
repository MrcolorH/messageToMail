package com.example.messagetomail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.messagetomail.Utils.AppLogger
import com.example.messagetomail.repository.EmailConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机自启动广播接收器
 * 适配备用机场景：在手机重启或开机后自动检查配置并恢复短信监听服务
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AppLogger.i("BootReceiver", "收到系统广播: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val repository = EmailConfigRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val hasChannel = repository.isEmailEnabledBlocking() ||
                            repository.isWecomEnabledBlocking() ||
                            repository.isFeishuEnabledBlocking() ||
                            repository.isFeishuAppEnabledBlocking()

                    if (hasChannel) {
                        AppLogger.i("BootReceiver", "检测到已启用转发渠道，开机自动启动短信监听服务")
                        val serviceIntent = SmsObserverService.createIntent(context)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } else {
                        AppLogger.i("BootReceiver", "未启用任何转发渠道，跳过开机自启")
                    }
                } catch (e: Exception) {
                    AppLogger.e("BootReceiver", "开机自启服务失败", e)
                }
            }
        }
    }
}
