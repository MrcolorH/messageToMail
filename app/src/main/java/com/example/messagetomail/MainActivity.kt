package com.example.messagetomail

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.messagetomail.databinding.ActivityMainBinding
import com.example.messagetomail.model.LogViewModel
import com.example.messagetomail.repository.EmailConfigRepository
import com.example.messagetomail.Utils.AppLogger
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.example.messagetomail.Utils.NotificationHelper
import com.example.messagetomail.database.AppDatabase
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import dev.doubledot.doki.ui.DokiActivity
import java.util.Calendar


class MainActivity : AppCompatActivity() {
    private var isServiceRunning = false
    private lateinit var viewModel: LogViewModel
    private val SMS_PERMISSION_REQUEST_CODE = 123

    private lateinit var repository: EmailConfigRepository
    private lateinit var tvStats: TextView
    private var configValid = false
    private var emailEnabled = true
    private var wecomEnabled = false
    private var feishuEnabled = false
    private var feishuAppEnabled = false

    private val forwardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == SmsObserverService.ACTION_FORWARD_COMPLETED) {
                refreshStats()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i("MainActivity", "主界面创建")
        val binding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewModel = LogViewModel()
        binding.logText = viewModel
        binding.lifecycleOwner = this

        repository = EmailConfigRepository(this)

        // 初始化通知渠道（Android 8.0+需要）
        NotificationHelper.createNotificationChannel(this)
        AppLogger.d("MainActivity", "通知渠道初始化完成")

        // 请求权限
        requestSmsPermissions()

        val toggleButton: Button = findViewById(R.id.button)
        val btnSettings: Button = findViewById(R.id.btnSettings)
        val btnFilterRule: Button = findViewById(R.id.btnFilterRule)
        val btnHistory: Button = findViewById(R.id.btnHistory)
        val btnLogs: Button = findViewById(R.id.btnLogs)
        val btnHelp: Button = findViewById(R.id.btnHelp)
        tvStats = findViewById(R.id.tvStats)

        // 观察配置有效性和各服务开关状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.configValidFlow.collect { valid ->
                        configValid = valid
                        AppLogger.i("MainActivity", "配置有效性状态: valid=$valid, isServiceRunning=$isServiceRunning")
                        if (!valid && !isServiceRunning) {
                            viewModel.setContent("请完成服务配置")
                        }
                    }
                }
                launch {
                    repository.emailEnabledFlow.collect { enabled ->
                        emailEnabled = enabled
                    }
                }
                launch {
                    repository.wecomEnabledFlow.collect { enabled ->
                        wecomEnabled = enabled
                    }
                }
                launch {
                    repository.feishuEnabledFlow.collect { enabled ->
                        feishuEnabled = enabled
                    }
                }
                launch {
                    repository.feishuAppEnabledFlow.collect { enabled ->
                        feishuAppEnabled = enabled
                    }
                }
            }
        }

        toggleButton.setOnClickListener {
            if (!isServiceRunning) {
                AppLogger.i("MainActivity", "用户点击开启服务")
                // 开启服务前检查配置
                if (!configValid) {
                    AppLogger.w("MainActivity", "配置未验证，阻止开启服务")
                    Toast.makeText(this, "请先完成服务配置", Toast.LENGTH_SHORT).show()
                    startActivity(SettingsActivity.createIntent(this, false))
                    return@setOnClickListener
                }

                val result = checkSmsPermission2()
                AppLogger.i("MainActivity", "权限检查结果: result=$result")
                if (result) {
                    isServiceRunning = !isServiceRunning
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(SmsObserverService.createIntent(this))
                    } else {
                        startService(SmsObserverService.createIntent(this))
                    }
                    Toast.makeText(this, "服务已开启", Toast.LENGTH_SHORT).show()
                    toggleButton.text = "关闭服务"
                    viewModel.setContent(buildActiveChannelsText())
                    btnSettings.isEnabled = false
                    AppLogger.i("MainActivity", "服务已开启，短信监听服务已启动")
                    showKeepaliveGuideOnce()
                    refreshStats()
                } else {
                    Toast.makeText(this,"权限不足",Toast.LENGTH_SHORT).show()
                    AppLogger.e("MainActivity", "启动失败：权限不足")
                }
            } else {
                AppLogger.i("MainActivity", "用户点击关闭服务")
                isServiceRunning = !isServiceRunning
                stopService(Intent(this, SmsObserverService::class.java))
                toggleButton.text = "开启服务"
                Toast.makeText(this, "服务已关闭", Toast.LENGTH_SHORT).show()
                viewModel.setContent("服务停用")
                btnSettings.isEnabled = true
                AppLogger.i("MainActivity", "服务已关闭")
                refreshStats()
            }
        }

        btnSettings.setOnClickListener {
            AppLogger.i("MainActivity", "跳转到服务配置页面")
            startActivity(SettingsActivity.createIntent(this, isServiceRunning))
        }

        btnFilterRule.setOnClickListener {
            AppLogger.i("MainActivity", "跳转到转发规则页面")
            startActivity(FilterRuleActivity.createIntent(this))
        }

        btnHistory.setOnClickListener {
            AppLogger.i("MainActivity", "跳转到转发记录页面")
            startActivity(ForwardHistoryActivity.createIntent(this))
        }

        btnLogs.setOnClickListener {
            AppLogger.i("MainActivity", "跳转到日志页面")
            startActivity(LogActivity.createIntent(this))
        }

        btnHelp.setOnClickListener {
            AppLogger.i("MainActivity", "跳转到使用说明页面")
            startActivity(HelpActivity.createIntent(this))
        }

        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        registerReceiver(forwardReceiver, IntentFilter(SmsObserverService.ACTION_FORWARD_COMPLETED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(forwardReceiver)
    }



    private fun buildActiveChannelsText(): String {
        val channels = mutableListOf<String>()
        if (emailEnabled) channels.add("邮件")
        if (wecomEnabled) channels.add("企微机器人")
        if (feishuEnabled) channels.add("飞书机器人")
        if (feishuAppEnabled) channels.add("飞书应用机器人")
        return if (channels.isEmpty()) {
            "服务已启用（未配置转发渠道）"
        } else {
            "已启用转发：${channels.joinToString("、")}"
        }
    }

    private fun refreshStats() {
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@MainActivity).forwardRecordDao()

                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val weekStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val todayRecords = dao.getRecordsSince(todayStart)
                val weekRecords = dao.getRecordsSince(weekStart)

                val todaySuccess = todayRecords.count { it.success }
                val todayFailure = todayRecords.count { !it.success }
                val weekSuccess = weekRecords.count { it.success }
                val weekFailure = weekRecords.count { !it.success }

                val channelStats = todayRecords.groupBy { it.channel }.map { (channel, records) ->
                    val s = records.count { it.success }
                    val f = records.count { !it.success }
                    val label = when (channel) {
                        "email" -> "邮件"
                        "wecom" -> "企微"
                        "feishu" -> "飞书"
                        "feishu_app" -> "飞书应用"
                        else -> channel
                    }
                    "$label：$s 成功 / $f 失败"
                }.joinToString("\n")

                val statsText = buildString {
                    appendLine("今日：${todayRecords.size} 条（$todaySuccess 成功 / $todayFailure 失败）")
                    appendLine("本周：${weekRecords.size} 条（$weekSuccess 成功 / $weekFailure 失败）")
                    if (channelStats.isNotBlank()) {
                        appendLine("---")
                        append(channelStats)
                    }
                }

                tvStats.text = statsText
            } catch (e: Exception) {
                tvStats.text = "统计加载失败"
            }
        }
    }

    private fun requestSmsPermissions() {
        AppLogger.i("Permission", "开始请求短信相关权限")
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.READ_PHONE_STATE
        )

        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            AppLogger.d("Permission", "Android 13+，追加通知权限")
        }

        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        AppLogger.i("Permission", "缺少的权限数量: ${permissionsNeeded.size}")
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                SMS_PERMISSION_REQUEST_CODE
            )
        }
    }

    //权限校验-v2-扩展权限检查
    private fun checkSmsPermission2(): Boolean{
        AppLogger.i("Permission", "执行扩展权限检查")
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_PHONE_STATE
        )

        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            //缺少权限 -> 获取权限
            AppLogger.w("Permission", "缺少权限: $permissionsNeeded")
            requestSmsPermissions()
            return false
        } else {
            //四个权限齐的
            AppLogger.i("Permission", "所有权限已获取")
            return true
        }
    }
    // 权限校验-v1-1个权限
    private fun checkSmsPermission1(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS),
                SMS_PERMISSION_REQUEST_CODE
            )
            return false
        } else {
            return true
        }
    }

    /**
     * 首次开启服务时，引导用户进行后台保活设置（仅提示一次）
     */
    private fun showKeepaliveGuideOnce() {
        val prefs = getSharedPreferences("keepalive_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("has_shown_guide", false)) return

        AlertDialog.Builder(this)
            .setTitle("后台保活设置建议")
            .setMessage("为确保短信转发服务稳定运行，建议进行后台保活设置。\n\n将根据您的手机品牌自动显示对应的设置指引。")
            .setPositiveButton("查看设置指引") { _, _ ->
                prefs.edit().putBoolean("has_shown_guide", true).apply()
                DokiActivity.start(this)
            }
            .setNegativeButton("不再提示") { dialog, _ ->
                prefs.edit().putBoolean("has_shown_guide", true).apply()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

}
