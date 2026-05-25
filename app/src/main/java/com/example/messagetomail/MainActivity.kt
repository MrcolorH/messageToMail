package com.example.messagetomail

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
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
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog


class MainActivity : AppCompatActivity() {
    private var isServiceRunning = false
    private lateinit var viewModel: LogViewModel
    private val SMS_PERMISSION_REQUEST_CODE = 123

    private lateinit var repository: EmailConfigRepository
    private var configValid = false

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

        // 检查是否为vivo系统并提示设置
        checkVivoSystemSettings()

        // 请求权限
        requestSmsPermissions()

        val toggleButton: Button = findViewById(R.id.button)
        val btnSettings: Button = findViewById(R.id.btnSettings)
        val btnLogs: Button = findViewById(R.id.btnLogs)

        // 观察配置有效性
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.configValidFlow.collect { valid ->
                    configValid = valid
                    AppLogger.i("MainActivity", "配置有效性状态: valid=$valid, isServiceRunning=$isServiceRunning")
                    if (!valid && !isServiceRunning) {
                        viewModel.setContent("请完成邮件配置")
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
                    Toast.makeText(this, "请先完成邮件配置", Toast.LENGTH_SHORT).show()
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
                    viewModel.setContent("服务启用")
                    btnSettings.isEnabled = false
                    AppLogger.i("MainActivity", "服务已开启，短信监听服务已启动")
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
            }
        }

        btnSettings.setOnClickListener {
            AppLogger.i("MainActivity", "跳转到邮件配置页面")
            startActivity(SettingsActivity.createIntent(this, isServiceRunning))
        }

        btnLogs.setOnClickListener {
            AppLogger.i("MainActivity", "跳转到日志页面")
            startActivity(LogActivity.createIntent(this))
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
     * 检查vivo系统并提示用户进行必要设置
     */
    private fun checkVivoSystemSettings() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        AppLogger.i("VivoSystem", "设备信息: manufacturer=$manufacturer, brand=$brand, model=${Build.MODEL}")
        if (manufacturer.contains("vivo") || brand.contains("vivo")) {
            AppLogger.i("VivoSystem", "检测到vivo系统，显示设置引导")
            showVivoSettingsDialog()
        }
    }

    /**
     * 显示vivo系统设置引导对话框
     */
    private fun showVivoSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("vivo系统设置提醒")
            .setMessage("为确保短信转邮件功能正常工作，请进行以下设置：\n\n" +
                    "1. 开启自启动管理权限\n" +
                    "2. 允许后台高耗电\n" +
                    "3. 关闭省电模式限制\n" +
                    "4. 将应用加入白名单\n\n" +
                    "是否现在前往设置？")
            .setPositiveButton("前往设置") { _, _ ->
                try {
                    // 尝试打开vivo的自启动管理页面
                    val intent = Intent().apply {
                        component = android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                        )
                        putExtra("packagename", packageName)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // 如果无法打开特定页面，打开应用设置页面
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (ex: Exception) {
                        Toast.makeText(this, "请手动前往设置-应用管理中配置权限", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("稍后设置") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "请记得在设置中开启相关权限", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

}
