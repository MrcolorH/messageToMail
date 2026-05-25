package com.example.messagetomail.repository

import android.content.ClipboardManager
import android.content.Context
import android.content.ClipData
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.messagetomail.model.EmailType
import com.example.messagetomail.model.ReceiverConfig
import com.example.messagetomail.model.SenderConfig
import com.example.messagetomail.model.WecomRobotConfig
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import java.util.Properties

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "email_config")

class EmailConfigRepository(private val context: Context) {

    private val gson = Gson()
    private val dataStore = context.dataStore

    companion object {
        val SENDER_CONFIG_KEY = stringPreferencesKey("sender_config")
        val RECEIVER_CONFIG_KEY = stringPreferencesKey("receiver_config")
        val CONFIG_VALID_KEY = booleanPreferencesKey("config_valid")
        val WECOM_CONFIG_KEY = stringPreferencesKey("wecom_config")
        val EMAIL_ENABLED_KEY = booleanPreferencesKey("email_enabled")
        val WECOM_ENABLED_KEY = booleanPreferencesKey("wecom_enabled")
    }

    // === 读取 ===
    val senderConfigFlow: Flow<SenderConfig?> = dataStore.data.map { prefs ->
        prefs[SENDER_CONFIG_KEY]?.let { gson.fromJson(it, SenderConfig::class.java) }
    }

    val receiverConfigFlow: Flow<ReceiverConfig?> = dataStore.data.map { prefs ->
        prefs[RECEIVER_CONFIG_KEY]?.let { gson.fromJson(it, ReceiverConfig::class.java) }
    }

    val configValidFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[CONFIG_VALID_KEY] ?: false
    }

    val wecomConfigFlow: Flow<WecomRobotConfig?> = dataStore.data.map { prefs ->
        prefs[WECOM_CONFIG_KEY]?.let { gson.fromJson(it, WecomRobotConfig::class.java) }
    }

    val emailEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[EMAIL_ENABLED_KEY] ?: true
    }

    val wecomEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[WECOM_ENABLED_KEY] ?: false
    }

    // === 写入 ===
    suspend fun saveSenderConfig(config: SenderConfig) {
        dataStore.edit { prefs ->
            prefs[SENDER_CONFIG_KEY] = gson.toJson(config)
        }
    }

    suspend fun saveReceiverConfig(config: ReceiverConfig) {
        dataStore.edit { prefs ->
            prefs[RECEIVER_CONFIG_KEY] = gson.toJson(config)
        }
    }

    suspend fun setConfigValid(valid: Boolean) {
        dataStore.edit { prefs ->
            prefs[CONFIG_VALID_KEY] = valid
        }
    }

    suspend fun saveWecomConfig(config: WecomRobotConfig) {
        dataStore.edit { prefs ->
            prefs[WECOM_CONFIG_KEY] = gson.toJson(config)
        }
    }

    suspend fun setEmailEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[EMAIL_ENABLED_KEY] = enabled
        }
    }

    suspend fun setWecomEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WECOM_ENABLED_KEY] = enabled
        }
    }

    // === 阻塞读取（供同步调用使用） ===
    fun getSenderConfigBlocking(): SenderConfig? = runBlocking {
        senderConfigFlow.first()
    }

    fun getReceiverConfigBlocking(): ReceiverConfig? = runBlocking {
        receiverConfigFlow.first()
    }

    fun isConfigValidBlocking(): Boolean = runBlocking {
        configValidFlow.first()
    }

    fun getWecomConfigBlocking(): WecomRobotConfig? = runBlocking {
        wecomConfigFlow.first()
    }

    fun isEmailEnabledBlocking(): Boolean = runBlocking {
        emailEnabledFlow.first()
    }

    fun isWecomEnabledBlocking(): Boolean = runBlocking {
        wecomEnabledFlow.first()
    }

    // === 清空 ===
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    // === 验证连通性 ===
    suspend fun verifySender(config: SenderConfig): Result<Unit> {
        return try {
            val props = Properties().apply {
                put("mail.smtp.host", config.smtpHost)
                put("mail.smtp.port", config.smtpPort)
                put("mail.smtp.auth", "true")
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
                if (config.useSsl) {
                    put("mail.smtp.ssl.enable", "true")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                }
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.account, config.credential)
                }
            })
            val transport = session.getTransport("smtp")
            transport.connect(config.smtpHost, config.account, config.credential)
            transport.close()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = when {
                e is javax.mail.AuthenticationFailedException -> "账号或授权码错误"
                e is java.net.UnknownHostException -> "无法连接SMTP服务器，请检查网络"
                e is java.net.SocketTimeoutException -> "连接超时，请检查网络或端口配置"
                e is java.net.ConnectException -> "连接被拒绝，请检查SMTP服务器地址和端口"
                e.message?.contains("SSL", ignoreCase = true) == true -> "SSL连接失败，请检查端口和加密设置"
                e.message?.isNotBlank() == true -> e.message!!
                else -> "验证失败(${e.javaClass.simpleName})"
            }
            Result.failure(Exception(errorMsg, e))
        }
    }

    // === 导入导出（剪贴板） ===
    fun exportToClipboard(sender: SenderConfig?, receivers: ReceiverConfig?) {
        val exportMap = mutableMapOf<String, Any>()
        sender?.let { exportMap["sender"] = it }
        receivers?.let { exportMap["receivers"] = it }
        val json = gson.toJson(exportMap)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("email_config", json))
        Toast.makeText(context, "配置已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    suspend fun importFromClipboard(): Result<Pair<SenderConfig?, ReceiverConfig?>> {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return Result.failure(Exception("剪贴板为空"))
        }
        val text = clip.getItemAt(0).text?.toString()
            ?: return Result.failure(Exception("剪贴板内容无效"))

        return try {
            val map = gson.fromJson(text, Map::class.java)
            val sender = (map["sender"] as? Map<*, *>)?.let {
                SenderConfig(
                    emailType = EmailType.valueOf(it["emailType"] as String),
                    account = it["account"] as String,
                    credential = it["credential"] as String,
                    smtpHost = it["smtpHost"] as String,
                    smtpPort = it["smtpPort"] as String,
                    useSsl = it["useSsl"] as Boolean
                )
            }
            val receivers = (map["receivers"] as? Map<*, *>)?.let {
                ReceiverConfig(emails = (it["emails"] as List<*>).map { e -> e as String })
            }
            if (sender != null) {
                saveSenderConfig(sender)
            }
            if (receivers != null) {
                saveReceiverConfig(receivers)
            }
            setConfigValid(sender != null && receivers != null && receivers.emails.isNotEmpty())
            Result.success(Pair(sender, receivers))
        } catch (e: Exception) {
            Result.failure(Exception("导入失败：${e.message}"))
        }
    }
}
