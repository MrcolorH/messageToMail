package com.example.messagetomail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.messagetomail.model.EmailType
import com.example.messagetomail.model.ReceiverConfig
import com.example.messagetomail.model.SenderConfig
import com.example.messagetomail.repository.EmailConfigRepository
import com.example.messagetomail.Utils.AppLogger
import com.example.messagetomail.Utils.FeishuAppRobotSender
import com.example.messagetomail.Utils.FeishuRobotSender
import com.example.messagetomail.Utils.WecomRobotSender
import com.example.messagetomail.model.FeishuAppRobotConfig
import com.example.messagetomail.model.FeishuRobotConfig
import com.example.messagetomail.model.WecomRobotConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: EmailConfigRepository

    private lateinit var tvServiceRunningBanner: TextView

    // 邮箱卡片
    private lateinit var headerEmail: LinearLayout
    private lateinit var contentEmail: LinearLayout
    private lateinit var arrowEmail: ImageView

    // 企微卡片
    private lateinit var headerWecom: LinearLayout
    private lateinit var contentWecom: LinearLayout
    private lateinit var arrowWecom: ImageView

    // 飞书卡片
    private lateinit var headerFeishu: LinearLayout
    private lateinit var contentFeishu: LinearLayout
    private lateinit var arrowFeishu: ImageView

    // 飞书应用卡片
    private lateinit var headerFeishuApp: LinearLayout
    private lateinit var contentFeishuApp: LinearLayout
    private lateinit var arrowFeishuApp: ImageView

    // 发件人
    private lateinit var rgEmailType: RadioGroup
    private lateinit var rbNormal: RadioButton
    private lateinit var rbQQ: RadioButton
    private lateinit var etSenderAccount: EditText
    private lateinit var tvCredentialLabel: TextView
    private lateinit var etSenderCredential: EditText
    private lateinit var tvCredentialHint: TextView
    private lateinit var etSmtpHost: EditText
    private lateinit var etSmtpPort: EditText
    private lateinit var btnExportSender: Button
    private lateinit var btnImportSender: Button
    private lateinit var btnSaveSender: Button
    private lateinit var tvSenderStatus: TextView

    // 收件人
    private lateinit var tvReceiverCount: TextView
    private lateinit var containerReceivers: LinearLayout
    private lateinit var etNewReceiver: EditText
    private lateinit var btnAddReceiver: Button
    private lateinit var btnExportReceiver: Button
    private lateinit var btnImportReceiver: Button

    // 通知开关（改为 CompoundButton 兼容 SwitchCompat）
    private lateinit var cbEmailEnabled: CompoundButton
    private lateinit var cbWecomEnabled: CompoundButton
    private lateinit var etWecomWebhook: EditText
    private lateinit var btnTestWecom: Button
    private lateinit var btnExportWecom: Button
    private lateinit var btnImportWecom: Button
    private lateinit var tvWecomStatus: TextView

    private lateinit var cbFeishuEnabled: CompoundButton
    private lateinit var etFeishuWebhook: EditText
    private lateinit var etFeishuSecret: EditText
    private lateinit var btnTestFeishu: Button
    private lateinit var btnExportFeishu: Button
    private lateinit var btnImportFeishu: Button
    private lateinit var tvFeishuStatus: TextView

    private lateinit var cbFeishuAppEnabled: CompoundButton
    private lateinit var etFeishuAppId: EditText
    private lateinit var etFeishuAppSecret: EditText
    private lateinit var btnPickFeishuReceiver: Button
    private lateinit var tvFeishuReceiverCount: TextView
    private lateinit var containerFeishuReceivers: LinearLayout
    private lateinit var etFeishuReceiveId: EditText
    private lateinit var btnAddFeishuReceiver: Button
    private lateinit var btnTestFeishuApp: Button
    private lateinit var btnExportFeishuApp: Button
    private lateinit var btnImportFeishuApp: Button
    private lateinit var tvFeishuAppStatus: TextView

    private var currentSender: SenderConfig? = null
    private var currentReceivers: ReceiverConfig? = null
    private var currentWecom: WecomRobotConfig? = null
    private var currentFeishu: FeishuRobotConfig? = null
    private var currentFeishuApp: FeishuAppRobotConfig? = null
    private var serviceRunning = false

    private var emailExpandedSet = false
    private var wecomExpandedSet = false
    private var feishuExpandedSet = false
    private var feishuAppExpandedSet = false

    private val feishuIdPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val receiveId = result.data?.getStringExtra(FeishuIdPickerActivity.RESULT_RECEIVE_ID)
            if (!receiveId.isNullOrBlank()) {
                addFeishuReceiver(receiveId)
            }
        }
    }

    companion object {
        const val EXTRA_SERVICE_RUNNING = "service_running"
        fun createIntent(context: Context, serviceRunning: Boolean): Intent {
            return Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_SERVICE_RUNNING, serviceRunning)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "服务配置"
        AppLogger.i("Settings", "进入服务配置页面, serviceRunning=$serviceRunning")

        repository = EmailConfigRepository(this)
        serviceRunning = intent.getBooleanExtra(EXTRA_SERVICE_RUNNING, false)

        initViews()
        applyReadOnlyState()
        setupCollapsibleCards()
        setupSenderPanel()
        setupReceiverPanel()
        observeConfig()
    }

    private fun applyReadOnlyState() {
        if (serviceRunning) {
            tvServiceRunningBanner.visibility = TextView.VISIBLE
            etSenderAccount.isEnabled = false
            etSenderCredential.isEnabled = false
            etSmtpHost.isEnabled = false
            etSmtpPort.isEnabled = false
            rbNormal.isEnabled = false
            rbQQ.isEnabled = false
            btnSaveSender.isEnabled = false
            btnImportSender.isEnabled = false
            etNewReceiver.isEnabled = false
            btnAddReceiver.isEnabled = false
            btnImportReceiver.isEnabled = false
            cbEmailEnabled.isEnabled = false
            cbWecomEnabled.isEnabled = false
            etWecomWebhook.isEnabled = false
            btnTestWecom.isEnabled = false
            btnExportWecom.isEnabled = false
            btnImportWecom.isEnabled = false
            cbFeishuEnabled.isEnabled = false
            etFeishuWebhook.isEnabled = false
            etFeishuSecret.isEnabled = false
            btnTestFeishu.isEnabled = false
            btnExportFeishu.isEnabled = false
            btnImportFeishu.isEnabled = false
            cbFeishuAppEnabled.isEnabled = false
            etFeishuAppId.isEnabled = false
            etFeishuAppSecret.isEnabled = false
            btnPickFeishuReceiver.isEnabled = false
            etFeishuReceiveId.isEnabled = false
            btnAddFeishuReceiver.isEnabled = false
            btnTestFeishuApp.isEnabled = false
            btnExportFeishuApp.isEnabled = false
            btnImportFeishuApp.isEnabled = false
        } else {
            tvServiceRunningBanner.visibility = TextView.GONE
        }
    }

    private fun initViews() {
        tvServiceRunningBanner = findViewById(R.id.tvServiceRunningBanner)

        headerEmail = findViewById(R.id.headerEmail)
        contentEmail = findViewById(R.id.contentEmail)
        arrowEmail = findViewById(R.id.arrowEmail)

        headerWecom = findViewById(R.id.headerWecom)
        contentWecom = findViewById(R.id.contentWecom)
        arrowWecom = findViewById(R.id.arrowWecom)

        headerFeishu = findViewById(R.id.headerFeishu)
        contentFeishu = findViewById(R.id.contentFeishu)
        arrowFeishu = findViewById(R.id.arrowFeishu)

        headerFeishuApp = findViewById(R.id.headerFeishuApp)
        contentFeishuApp = findViewById(R.id.contentFeishuApp)
        arrowFeishuApp = findViewById(R.id.arrowFeishuApp)

        rgEmailType = findViewById(R.id.rgEmailType)
        rbNormal = findViewById(R.id.rbNormal)
        rbQQ = findViewById(R.id.rbQQ)
        etSenderAccount = findViewById(R.id.etSenderAccount)
        tvCredentialLabel = findViewById(R.id.tvCredentialLabel)
        etSenderCredential = findViewById(R.id.etSenderCredential)
        tvCredentialHint = findViewById(R.id.tvCredentialHint)
        etSmtpHost = findViewById(R.id.etSmtpHost)
        etSmtpPort = findViewById(R.id.etSmtpPort)
        btnExportSender = findViewById(R.id.btnExportSender)
        btnImportSender = findViewById(R.id.btnImportSender)
        btnSaveSender = findViewById(R.id.btnSaveSender)
        tvSenderStatus = findViewById(R.id.tvSenderStatus)

        tvReceiverCount = findViewById(R.id.tvReceiverCount)
        containerReceivers = findViewById(R.id.containerReceivers)
        etNewReceiver = findViewById(R.id.etNewReceiver)
        btnAddReceiver = findViewById(R.id.btnAddReceiver)
        btnExportReceiver = findViewById(R.id.btnExportReceiver)
        btnImportReceiver = findViewById(R.id.btnImportReceiver)

        cbEmailEnabled = findViewById<CompoundButton>(R.id.cbEmailEnabled)
        cbWecomEnabled = findViewById<CompoundButton>(R.id.cbWecomEnabled)
        etWecomWebhook = findViewById(R.id.etWecomWebhook)
        btnTestWecom = findViewById(R.id.btnTestWecom)
        btnExportWecom = findViewById(R.id.btnExportWecom)
        btnImportWecom = findViewById(R.id.btnImportWecom)
        tvWecomStatus = findViewById(R.id.tvWecomStatus)

        cbFeishuEnabled = findViewById<CompoundButton>(R.id.cbFeishuEnabled)
        etFeishuWebhook = findViewById(R.id.etFeishuWebhook)
        etFeishuSecret = findViewById(R.id.etFeishuSecret)
        btnTestFeishu = findViewById(R.id.btnTestFeishu)
        btnExportFeishu = findViewById(R.id.btnExportFeishu)
        btnImportFeishu = findViewById(R.id.btnImportFeishu)
        tvFeishuStatus = findViewById(R.id.tvFeishuStatus)

        cbFeishuAppEnabled = findViewById<CompoundButton>(R.id.cbFeishuAppEnabled)
        etFeishuAppId = findViewById(R.id.etFeishuAppId)
        etFeishuAppSecret = findViewById(R.id.etFeishuAppSecret)
        btnPickFeishuReceiver = findViewById(R.id.btnPickFeishuReceiver)
        tvFeishuReceiverCount = findViewById(R.id.tvFeishuReceiverCount)
        containerFeishuReceivers = findViewById(R.id.containerFeishuReceivers)
        etFeishuReceiveId = findViewById(R.id.etFeishuReceiveId)
        btnAddFeishuReceiver = findViewById(R.id.btnAddFeishuReceiver)
        btnTestFeishuApp = findViewById(R.id.btnTestFeishuApp)
        btnExportFeishuApp = findViewById(R.id.btnExportFeishuApp)
        btnImportFeishuApp = findViewById(R.id.btnImportFeishuApp)
        tvFeishuAppStatus = findViewById(R.id.tvFeishuAppStatus)

        // 初始化折叠箭头方向（所有卡片默认折叠）
        arrowEmail.rotation = -90f
        arrowWecom.rotation = -90f
        arrowFeishu.rotation = -90f
        arrowFeishuApp.rotation = -90f
    }

    private fun setupCollapsibleCards() {
        headerEmail.setOnClickListener { toggleCard(contentEmail, arrowEmail) }
        headerWecom.setOnClickListener { toggleCard(contentWecom, arrowWecom) }
        headerFeishu.setOnClickListener { toggleCard(contentFeishu, arrowFeishu) }
        headerFeishuApp.setOnClickListener { toggleCard(contentFeishuApp, arrowFeishuApp) }
    }

    private fun toggleCard(content: LinearLayout, arrow: ImageView) {
        if (content.visibility == LinearLayout.VISIBLE) {
            content.visibility = LinearLayout.GONE
            arrow.animate().rotation(-90f).setDuration(200).start()
        } else {
            content.visibility = LinearLayout.VISIBLE
            arrow.animate().rotation(0f).setDuration(200).start()
        }
    }

    private fun setCardExpanded(content: LinearLayout, arrow: ImageView, expanded: Boolean) {
        if (expanded) {
            content.visibility = LinearLayout.VISIBLE
            arrow.rotation = 0f
        } else {
            content.visibility = LinearLayout.GONE
            arrow.rotation = -90f
        }
    }

    private fun setupSenderPanel() {
        rgEmailType.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                R.id.rbNormal -> EmailType.NORMAL
                else -> EmailType.QQ
            }
            val preset = SenderConfig.getPreset(type)
            etSmtpHost.setText(preset.smtpHost)
            etSmtpPort.setText(preset.smtpPort)
            if (!serviceRunning) {
                etSmtpHost.isEnabled = type == EmailType.NORMAL
                etSmtpPort.isEnabled = type == EmailType.NORMAL
            }

            if (type == EmailType.QQ) {
                tvCredentialLabel.text = "授权码"
                etSenderCredential.hint = "请输入授权码"
                tvCredentialHint.text = "提示：QQ邮箱需要使用授权码而非登录密码"
                tvCredentialHint.visibility = TextView.VISIBLE
            } else {
                tvCredentialLabel.text = "邮箱密码"
                etSenderCredential.hint = "请输入邮箱密码"
                tvCredentialHint.visibility = TextView.GONE
            }
        }

        // 默认选中QQ邮箱，触发一次预设填充
        rgEmailType.check(R.id.rbQQ)

        btnSaveSender.setOnClickListener {
            if (serviceRunning) {
                AppLogger.w("Settings", "用户尝试保存发件人配置，但服务运行中")
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSenderConfig()
        }

        btnExportSender.setOnClickListener {
            AppLogger.i("Settings", "用户导出发件人配置到剪贴板")
            repository.exportToClipboard(currentSender, currentReceivers)
        }

        btnImportSender.setOnClickListener {
            if (serviceRunning) {
                AppLogger.w("Settings", "用户尝试导入配置，但服务运行中")
                Toast.makeText(this, "服务运行中，无法导入配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppLogger.i("Settings", "用户从剪贴板导入配置")
            lifecycleScope.launch {
                val result = repository.importFromClipboard()
                result.onSuccess {
                    AppLogger.i("Settings", "配置导入成功")
                    Toast.makeText(this@SettingsActivity, "配置导入成功", Toast.LENGTH_SHORT).show()
                    refreshSenderUI(it.first)
                    refreshReceiverUI(it.second)
                }.onFailure {
                    AppLogger.e("Settings", "配置导入失败: ${it.message}")
                    Toast.makeText(this@SettingsActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        cbEmailEnabled.setOnCheckedChangeListener { _, isChecked ->
            AppLogger.i("Settings", "邮件通知开关: $isChecked")
            lifecycleScope.launch {
                repository.setEmailEnabled(isChecked)
            }
        }

        cbWecomEnabled.setOnCheckedChangeListener { _, isChecked ->
            AppLogger.i("Settings", "企微通知开关: $isChecked")
            lifecycleScope.launch {
                repository.setWecomEnabled(isChecked)
            }
        }

        btnTestWecom.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val webhook = etWecomWebhook.text.toString().trim()
            if (webhook.isEmpty()) {
                Toast.makeText(this, "请输入企业微信 Webhook 地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvWecomStatus.text = "⏳ 正在测试企微机器人..."
            btnTestWecom.isEnabled = false
            lifecycleScope.launch {
                val result = WecomRobotSender.send(webhook, "测试消息", "这是一条来自短信转邮件App的测试消息")
                btnTestWecom.isEnabled = true
                result.onSuccess {
                    AppLogger.i("Settings", "企微机器人测试通过")
                    Toast.makeText(this@SettingsActivity, "企微机器人测试成功", Toast.LENGTH_SHORT).show()
                    tvWecomStatus.text = "✅ 企微机器人配置有效"
                    repository.saveWecomConfig(WecomRobotConfig(webhook, cbWecomEnabled.isChecked))
                }.onFailure {
                    AppLogger.e("Settings", "企微机器人测试失败: ${it.message}")
                    Toast.makeText(this@SettingsActivity, "测试失败：${it.message}", Toast.LENGTH_LONG).show()
                    tvWecomStatus.text = "❌ 测试失败：${it.message}"
                }
            }
        }

        cbFeishuEnabled.setOnCheckedChangeListener { _, isChecked ->
            AppLogger.i("Settings", "飞书通知开关: $isChecked")
            lifecycleScope.launch {
                repository.setFeishuEnabled(isChecked)
            }
        }

        btnTestFeishu.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val webhook = etFeishuWebhook.text.toString().trim()
            if (webhook.isEmpty()) {
                Toast.makeText(this, "请输入飞书 Webhook 地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvFeishuStatus.text = "⏳ 正在测试飞书机器人..."
            btnTestFeishu.isEnabled = false
            lifecycleScope.launch {
                val secret = etFeishuSecret.text.toString().trim()
                val result = FeishuRobotSender.send(webhook, secret, "测试消息", "这是一条来自短信转邮件App的测试消息")
                btnTestFeishu.isEnabled = true
                result.onSuccess {
                    AppLogger.i("Settings", "飞书机器人测试通过")
                    Toast.makeText(this@SettingsActivity, "飞书机器人测试成功", Toast.LENGTH_SHORT).show()
                    tvFeishuStatus.text = "✅ 飞书机器人配置有效"
                    repository.saveFeishuConfig(FeishuRobotConfig(webhook, secret, cbFeishuEnabled.isChecked))
                }.onFailure {
                    AppLogger.e("Settings", "飞书机器人测试失败: ${it.message}")
                    Toast.makeText(this@SettingsActivity, "测试失败：${it.message}", Toast.LENGTH_LONG).show()
                    tvFeishuStatus.text = "❌ 测试失败：${it.message}"
                }
            }
        }

        cbFeishuAppEnabled.setOnCheckedChangeListener { _, isChecked ->
            AppLogger.i("Settings", "飞书应用机器人通知开关: $isChecked")
            lifecycleScope.launch {
                repository.setFeishuAppEnabled(isChecked)
            }
        }

        btnPickFeishuReceiver.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val appId = etFeishuAppId.text.toString().trim()
            val appSecret = etFeishuAppSecret.text.toString().trim()
            if (appId.isEmpty() || appSecret.isEmpty()) {
                Toast.makeText(this, "请先填写 App ID 和 App Secret", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = FeishuIdPickerActivity.createIntent(this, appId, appSecret)
            feishuIdPickerLauncher.launch(intent)
        }

        btnAddFeishuReceiver.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val receiveId = etFeishuReceiveId.text.toString().trim()
            if (receiveId.isEmpty()) {
                Toast.makeText(this, "请输入接收者 ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addFeishuReceiver(receiveId)
            etFeishuReceiveId.text.clear()
        }

        btnTestFeishuApp.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val appId = etFeishuAppId.text.toString().trim()
            val appSecret = etFeishuAppSecret.text.toString().trim()
            val receivers = currentFeishuApp?.receivers ?: emptyList()
            if (appId.isEmpty() || appSecret.isEmpty()) {
                Toast.makeText(this, "请填写 App ID 和 App Secret", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (receivers.isEmpty()) {
                Toast.makeText(this, "请至少添加一个接收者", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvFeishuAppStatus.text = "⏳ 正在测试飞书应用机器人..."
            btnTestFeishuApp.isEnabled = false
            lifecycleScope.launch {
                val result = FeishuAppRobotSender.sendToMultiple(
                    appId, appSecret, receivers,
                    "测试消息", "这是一条来自短信转邮件App的测试消息"
                )
                btnTestFeishuApp.isEnabled = true
                result.onSuccess {
                    AppLogger.i("Settings", "飞书应用机器人测试通过")
                    Toast.makeText(this@SettingsActivity, "飞书应用机器人测试成功", Toast.LENGTH_SHORT).show()
                    tvFeishuAppStatus.text = "✅ 飞书应用机器人配置有效"
                    repository.saveFeishuAppConfig(
                        FeishuAppRobotConfig(appId, appSecret, receivers, cbFeishuAppEnabled.isChecked)
                    )
                }.onFailure {
                    AppLogger.e("Settings", "飞书应用机器人测试失败: ${it.message}")
                    Toast.makeText(this@SettingsActivity, "测试失败：${it.message}", Toast.LENGTH_LONG).show()
                    tvFeishuAppStatus.text = "❌ 测试失败：${it.message}"
                }
            }
        }

        // 企微导入导出
        btnExportWecom.setOnClickListener {
            AppLogger.i("Settings", "导出企微配置")
            repository.exportWecomToClipboard(currentWecom)
        }
        btnImportWecom.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法导入配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppLogger.i("Settings", "导入企微配置")
            lifecycleScope.launch {
                val result = repository.importWecomFromClipboard()
                result.onSuccess {
                    Toast.makeText(this@SettingsActivity, "企微配置导入成功", Toast.LENGTH_SHORT).show()
                    refreshWecomUI(it)
                }.onFailure {
                    Toast.makeText(this@SettingsActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // 飞书导入导出
        btnExportFeishu.setOnClickListener {
            AppLogger.i("Settings", "导出飞书配置")
            repository.exportFeishuToClipboard(currentFeishu)
        }
        btnImportFeishu.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法导入配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppLogger.i("Settings", "导入飞书配置")
            lifecycleScope.launch {
                val result = repository.importFeishuFromClipboard()
                result.onSuccess {
                    Toast.makeText(this@SettingsActivity, "飞书配置导入成功", Toast.LENGTH_SHORT).show()
                    refreshFeishuUI(it)
                }.onFailure {
                    Toast.makeText(this@SettingsActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // 飞书应用导入导出
        btnExportFeishuApp.setOnClickListener {
            AppLogger.i("Settings", "导出飞书应用配置")
            repository.exportFeishuAppToClipboard(currentFeishuApp)
        }
        btnImportFeishuApp.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法导入配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppLogger.i("Settings", "导入飞书应用配置")
            lifecycleScope.launch {
                val result = repository.importFeishuAppFromClipboard()
                result.onSuccess {
                    Toast.makeText(this@SettingsActivity, "飞书应用配置导入成功", Toast.LENGTH_SHORT).show()
                    refreshFeishuAppUI(it)
                }.onFailure {
                    Toast.makeText(this@SettingsActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupReceiverPanel() {
        btnAddReceiver.setOnClickListener {
            if (serviceRunning) {
                AppLogger.w("Settings", "用户尝试添加收件人，但服务运行中")
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val email = etNewReceiver.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "请输入邮箱地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "邮箱格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentList = currentReceivers?.emails?.toMutableList() ?: mutableListOf()
            if (currentList.size >= ReceiverConfig.MAX_RECEIVERS) {
                Toast.makeText(this, "最多支持${ReceiverConfig.MAX_RECEIVERS}个收件邮箱", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentList.contains(email)) {
                Toast.makeText(this, "该邮箱已存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentList.add(email)
            currentReceivers = ReceiverConfig(currentList)
            AppLogger.i("Settings", "添加收件人: $email, 当前共 ${currentList.size} 个")
            lifecycleScope.launch {
                repository.saveReceiverConfig(ReceiverConfig(currentList))
                refreshReceiverUI(ReceiverConfig(currentList))
            }
            etNewReceiver.text.clear()
        }

        btnExportReceiver.setOnClickListener {
            AppLogger.i("Settings", "用户导出收件人配置到剪贴板")
            repository.exportToClipboard(currentSender, currentReceivers)
        }

        btnImportReceiver.setOnClickListener {
            if (serviceRunning) {
                AppLogger.w("Settings", "用户尝试导入收件人配置，但服务运行中")
                Toast.makeText(this, "服务运行中，无法导入配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppLogger.i("Settings", "用户从剪贴板导入收件人配置")
            lifecycleScope.launch {
                val result = repository.importFromClipboard()
                result.onSuccess {
                    AppLogger.i("Settings", "收件人配置导入成功")
                    Toast.makeText(this@SettingsActivity, "配置导入成功", Toast.LENGTH_SHORT).show()
                    refreshSenderUI(it.first)
                    refreshReceiverUI(it.second)
                }.onFailure {
                    AppLogger.e("Settings", "收件人配置导入失败: ${it.message}")
                    Toast.makeText(this@SettingsActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeConfig() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.senderConfigFlow.collect { config ->
                        currentSender = config
                        refreshSenderUI(config)
                    }
                }
                launch {
                    repository.receiverConfigFlow.collect { config ->
                        currentReceivers = config
                        refreshReceiverUI(config)
                    }
                }
                launch {
                    repository.configValidFlow.collect { valid ->
                        AppLogger.d("Settings", "配置有效性变化: valid=$valid, sender=${currentSender != null}, receivers=${currentReceivers?.emails?.size ?: 0}")
                        val status = when {
                            serviceRunning -> "⚠️ 服务运行中"
                            valid -> "✅ 配置已验证通过"
                            currentSender == null -> "状态：未配置发件人"
                            currentReceivers == null || currentReceivers!!.emails.isEmpty() -> "状态：未配置收件人"
                            else -> "状态：配置未验证"
                        }
                        tvSenderStatus.text = status
                    }
                }
                launch {
                    repository.emailEnabledFlow.collect { enabled ->
                        cbEmailEnabled.isChecked = enabled
                        if (!emailExpandedSet) {
                            emailExpandedSet = true
                            setCardExpanded(contentEmail, arrowEmail, enabled)
                        }
                    }
                }
                launch {
                    repository.wecomEnabledFlow.collect { enabled ->
                        cbWecomEnabled.isChecked = enabled
                        if (!wecomExpandedSet) {
                            wecomExpandedSet = true
                            setCardExpanded(contentWecom, arrowWecom, enabled)
                        }
                    }
                }
                launch {
                    repository.wecomConfigFlow.collect { config ->
                        currentWecom = config
                        refreshWecomUI(config)
                    }
                }
                launch {
                    repository.feishuEnabledFlow.collect { enabled ->
                        cbFeishuEnabled.isChecked = enabled
                        if (!feishuExpandedSet) {
                            feishuExpandedSet = true
                            setCardExpanded(contentFeishu, arrowFeishu, enabled)
                        }
                    }
                }
                launch {
                    repository.feishuConfigFlow.collect { config ->
                        currentFeishu = config
                        refreshFeishuUI(config)
                    }
                }
                launch {
                    repository.feishuAppEnabledFlow.collect { enabled ->
                        cbFeishuAppEnabled.isChecked = enabled
                        if (!feishuAppExpandedSet) {
                            feishuAppExpandedSet = true
                            setCardExpanded(contentFeishuApp, arrowFeishuApp, enabled)
                        }
                    }
                }
                launch {
                    repository.feishuAppConfigFlow.collect { config ->
                        currentFeishuApp = config
                        refreshFeishuAppUI(config)
                    }
                }
            }
        }
    }

    private fun refreshSenderUI(config: SenderConfig?) {
        config ?: return
        when (config.emailType) {
            EmailType.QQ -> rgEmailType.check(R.id.rbQQ)
            EmailType.NORMAL -> rgEmailType.check(R.id.rbNormal)
        }
        etSenderAccount.setText(config.account)
        etSenderCredential.setText(config.credential)
        etSmtpHost.setText(config.smtpHost)
        etSmtpPort.setText(config.smtpPort)
    }

    private fun refreshReceiverUI(config: ReceiverConfig?) {
        val list = config?.emails ?: emptyList()
        tvReceiverCount.text = "已添加收件人 (${list.size}/${ReceiverConfig.MAX_RECEIVERS})"
        containerReceivers.removeAllViews()

        list.forEach { email ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_receiver, containerReceivers, false)
            val tvEmail = itemView.findViewById<TextView>(R.id.tvReceiverEmail)
            val btnDelete = itemView.findViewById<Button>(R.id.btnDeleteReceiver)

            tvEmail.text = email
            btnDelete.setOnClickListener {
                if (serviceRunning) {
                    AppLogger.w("Settings", "用户尝试删除收件人，但服务运行中")
                    Toast.makeText(this, "服务运行中，无法删除", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val newList = list.toMutableList().apply { remove(email) }
                currentReceivers = ReceiverConfig(newList)
                AppLogger.i("Settings", "删除收件人: $email, 剩余 ${newList.size} 个")
                lifecycleScope.launch {
                    repository.saveReceiverConfig(ReceiverConfig(newList))
                    refreshReceiverUI(ReceiverConfig(newList))
                }
            }
            containerReceivers.addView(itemView)
        }
    }

    private fun refreshWecomUI(config: WecomRobotConfig?) {
        config ?: return
        etWecomWebhook.setText(config.webhookUrl)
        val status = when {
            serviceRunning -> "⚠️ 服务运行中"
            config.webhookUrl.isEmpty() -> "企微状态：未配置"
            else -> "企微状态：已配置"
        }
        tvWecomStatus.text = status
    }

    private fun refreshFeishuUI(config: FeishuRobotConfig?) {
        config ?: return
        etFeishuWebhook.setText(config.webhookUrl)
        etFeishuSecret.setText(config.secret)
        val status = when {
            serviceRunning -> "⚠️ 服务运行中"
            config.webhookUrl.isEmpty() -> "飞书状态：未配置"
            else -> "飞书状态：已配置"
        }
        tvFeishuStatus.text = status
    }

    private fun addFeishuReceiver(receiveId: String) {
        val currentList = currentFeishuApp?.receivers?.toMutableList() ?: mutableListOf()
        if (currentList.size >= FeishuAppRobotConfig.MAX_RECEIVERS) {
            Toast.makeText(this, "最多支持${FeishuAppRobotConfig.MAX_RECEIVERS}个接收者", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentList.any { it.receiveId == receiveId }) {
            Toast.makeText(this, "该接收者已存在", Toast.LENGTH_SHORT).show()
            return
        }
        currentList.add(com.example.messagetomail.model.FeishuAppReceiver(receiveId))
        val appId = etFeishuAppId.text.toString().trim()
        val appSecret = etFeishuAppSecret.text.toString().trim()
        val newConfig = FeishuAppRobotConfig(appId, appSecret, currentList, cbFeishuAppEnabled.isChecked)
        currentFeishuApp = newConfig
        AppLogger.i("Settings", "添加飞书接收者: $receiveId, 当前共 ${currentList.size} 个")
        lifecycleScope.launch {
            repository.saveFeishuAppConfig(newConfig)
            refreshFeishuReceiverList(currentList)
        }
    }

    private fun refreshFeishuAppUI(config: FeishuAppRobotConfig?) {
        config ?: return
        etFeishuAppId.setText(config.appId)
        etFeishuAppSecret.setText(config.appSecret)
        refreshFeishuReceiverList(config.receivers)
        val status = when {
            serviceRunning -> "⚠️ 服务运行中"
            config.appId.isEmpty() -> "飞书应用机器人状态：未配置"
            else -> "飞书应用机器人状态：已配置"
        }
        tvFeishuAppStatus.text = status
    }

    private fun refreshFeishuReceiverList(receivers: List<com.example.messagetomail.model.FeishuAppReceiver>) {
        val list = receivers
        tvFeishuReceiverCount.text = "已添加接收者 (${list.size}/${FeishuAppRobotConfig.MAX_RECEIVERS})"
        containerFeishuReceivers.removeAllViews()

        list.forEach { receiver ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_feishu_app_receiver, containerFeishuReceivers, false)
            val tvInfo = itemView.findViewById<TextView>(R.id.tvFeishuReceiverInfo)
            val btnDelete = itemView.findViewById<Button>(R.id.btnDeleteFeishuReceiver)

            tvInfo.text = "[${receiver.receiveIdType}] ${receiver.receiveId}"
            btnDelete.setOnClickListener {
                if (serviceRunning) {
                    Toast.makeText(this, "服务运行中，无法删除", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val newList = list.toMutableList().apply { remove(receiver) }
                val appId = etFeishuAppId.text.toString().trim()
                val appSecret = etFeishuAppSecret.text.toString().trim()
                val newConfig = FeishuAppRobotConfig(appId, appSecret, newList, cbFeishuAppEnabled.isChecked)
                currentFeishuApp = newConfig
                AppLogger.i("Settings", "删除飞书接收者: ${receiver.receiveId}, 剩余 ${newList.size} 个")
                lifecycleScope.launch {
                    repository.saveFeishuAppConfig(newConfig)
                    refreshFeishuReceiverList(newList)
                }
            }
            containerFeishuReceivers.addView(itemView)
        }
    }

    private fun saveSenderConfig() {
        val type = when (rgEmailType.checkedRadioButtonId) {
            R.id.rbNormal -> EmailType.NORMAL
            else -> EmailType.QQ
        }
        val account = etSenderAccount.text.toString().trim()
        val credential = etSenderCredential.text.toString().trim()
        val smtpHost = etSmtpHost.text.toString().trim()
        val smtpPort = etSmtpPort.text.toString().trim()

        if (account.isEmpty() || credential.isEmpty() || smtpHost.isEmpty() || smtpPort.isEmpty()) {
            AppLogger.w("Settings", "保存发件人配置失败：信息不完整")
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            return
        }

        AppLogger.i("Settings", "准备保存发件人配置: type=$type, account=$account, smtp=$smtpHost:$smtpPort")
        val config = SenderConfig(
            emailType = type,
            account = account,
            credential = credential,
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            useSsl = true
        )

        tvSenderStatus.text = "⏳ 正在验证连通性..."
        btnSaveSender.isEnabled = false

        lifecycleScope.launch {
            AppLogger.i("Settings", "开始验证 SMTP 连通性...")
            val result = withContext(Dispatchers.IO) {
                repository.verifySender(config)
            }
            btnSaveSender.isEnabled = true
            result.onSuccess {
                repository.saveSenderConfig(config)
                repository.setConfigValid(true)
                currentSender = config
                AppLogger.i("Settings", "SMTP 验证通过，发件人配置已保存")
                Toast.makeText(this@SettingsActivity, "验证通过，配置已保存", Toast.LENGTH_SHORT).show()
                tvSenderStatus.text = "✅ 配置已验证通过"
            }.onFailure {
                repository.setConfigValid(false)
                AppLogger.e("Settings", "SMTP 验证失败: ${it.message}")
                Toast.makeText(this@SettingsActivity, "验证失败：${it.message}", Toast.LENGTH_LONG).show()
                tvSenderStatus.text = "❌ 验证失败：${it.message}"
            }
        }
    }
}
