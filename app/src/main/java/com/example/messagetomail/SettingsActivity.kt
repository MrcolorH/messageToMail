package com.example.messagetomail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
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
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: EmailConfigRepository

    private lateinit var tvServiceRunningBanner: TextView
    private lateinit var btnTabSender: Button
    private lateinit var btnTabReceiver: Button
    private lateinit var panelSender: LinearLayout
    private lateinit var panelReceiver: LinearLayout

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

    private lateinit var tvReceiverCount: TextView
    private lateinit var containerReceivers: LinearLayout
    private lateinit var etNewReceiver: EditText
    private lateinit var btnAddReceiver: Button
    private lateinit var btnExportReceiver: Button
    private lateinit var btnImportReceiver: Button

    private var currentSender: SenderConfig? = null
    private var currentReceivers: ReceiverConfig? = null
    private var serviceRunning = false

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
        supportActionBar?.title = "邮件配置"

        repository = EmailConfigRepository(this)
        serviceRunning = intent.getBooleanExtra(EXTRA_SERVICE_RUNNING, false)

        initViews()
        applyReadOnlyState()
        setupTabs()
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
        } else {
            tvServiceRunningBanner.visibility = TextView.GONE
        }
    }

    private fun initViews() {
        tvServiceRunningBanner = findViewById(R.id.tvServiceRunningBanner)
        btnTabSender = findViewById(R.id.btnTabSender)
        btnTabReceiver = findViewById(R.id.btnTabReceiver)
        panelSender = findViewById(R.id.panelSender)
        panelReceiver = findViewById(R.id.panelReceiver)

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
    }

    private fun setupTabs() {
        btnTabSender.setOnClickListener { switchTab(Tab.SENDER) }
        btnTabReceiver.setOnClickListener { switchTab(Tab.RECEIVER) }
    }

    private fun switchTab(tab: Tab) {
        when (tab) {
            Tab.SENDER -> {
                panelSender.visibility = LinearLayout.VISIBLE
                panelReceiver.visibility = LinearLayout.GONE
                btnTabSender.setTextColor(getColor(R.color.purple_500))
                btnTabReceiver.setTextColor(getColor(android.R.color.darker_gray))
            }
            Tab.RECEIVER -> {
                panelSender.visibility = LinearLayout.GONE
                panelReceiver.visibility = LinearLayout.VISIBLE
                btnTabSender.setTextColor(getColor(android.R.color.darker_gray))
                btnTabReceiver.setTextColor(getColor(R.color.purple_500))
            }
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
                Toast.makeText(this, "服务运行中，无法修改配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSenderConfig()
        }

        btnExportSender.setOnClickListener {
            repository.exportToClipboard(currentSender, currentReceivers)
        }

        btnImportSender.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法导入配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val result = repository.importFromClipboard()
                result.onSuccess {
                    Toast.makeText(this@SettingsActivity, "配置导入成功", Toast.LENGTH_SHORT).show()
                    refreshSenderUI(it.first)
                    refreshReceiverUI(it.second)
                }.onFailure {
                    Toast.makeText(this@SettingsActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupReceiverPanel() {
        btnAddReceiver.setOnClickListener {
            if (serviceRunning) {
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
            lifecycleScope.launch {
                repository.saveReceiverConfig(ReceiverConfig(currentList))
                refreshReceiverUI(ReceiverConfig(currentList))
            }
            etNewReceiver.text.clear()
        }

        btnExportReceiver.setOnClickListener {
            repository.exportToClipboard(currentSender, currentReceivers)
        }

        btnImportReceiver.setOnClickListener {
            if (serviceRunning) {
                Toast.makeText(this, "服务运行中，无法导入配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val result = repository.importFromClipboard()
                result.onSuccess {
                    Toast.makeText(this@SettingsActivity, "配置导入成功", Toast.LENGTH_SHORT).show()
                    refreshSenderUI(it.first)
                    refreshReceiverUI(it.second)
                }.onFailure {
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
                    Toast.makeText(this, "服务运行中，无法删除", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val newList = list.toMutableList().apply { remove(email) }
                currentReceivers = ReceiverConfig(newList)
                lifecycleScope.launch {
                    repository.saveReceiverConfig(ReceiverConfig(newList))
                    refreshReceiverUI(ReceiverConfig(newList))
                }
            }
            containerReceivers.addView(itemView)
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
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            return
        }

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
            val result = repository.verifySender(config)
            btnSaveSender.isEnabled = true
            result.onSuccess {
                repository.saveSenderConfig(config)
                repository.setConfigValid(true)
                currentSender = config
                Toast.makeText(this@SettingsActivity, "验证通过，配置已保存", Toast.LENGTH_SHORT).show()
                tvSenderStatus.text = "✅ 配置已验证通过"
            }.onFailure {
                repository.setConfigValid(false)
                Toast.makeText(this@SettingsActivity, "验证失败：${it.message}", Toast.LENGTH_LONG).show()
                tvSenderStatus.text = "❌ 验证失败：${it.message}"
            }
        }
    }

    enum class Tab { SENDER, RECEIVER }
}
