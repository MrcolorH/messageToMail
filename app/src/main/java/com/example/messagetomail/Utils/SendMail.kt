package com.example.messagetomail.Utils

import android.content.Context
import com.example.messagetomail.repository.EmailConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

suspend fun sendEmail(context: Context, devicePhoneNumber: String?, sender: String?, messageBody: String) {
    AppLogger.i("SendMail", "=== 开始发送邮件流程 ===")
    AppLogger.d("SendMail", "参数: devicePhone=$devicePhoneNumber, sender=$sender, bodyLength=${messageBody.length}")

    val repository = EmailConfigRepository(context)

    AppLogger.d("SendMail", "正在读取邮件配置...")
    val senderConfig = repository.getSenderConfigBlocking()
    val receiverConfig = repository.getReceiverConfigBlocking()

    if (senderConfig == null) {
        AppLogger.e("SendMail", "发件人配置为空，终止发送")
        return
    }
    if (receiverConfig == null || receiverConfig.emails.isEmpty()) {
        AppLogger.e("SendMail", "收件人配置为空，终止发送")
        return
    }

    AppLogger.i("SendMail", "配置读取成功")
    AppLogger.d("SendMail", "发件人: ${senderConfig.account}, SMTP: ${senderConfig.smtpHost}:${senderConfig.smtpPort}, SSL=${senderConfig.useSsl}")
    AppLogger.d("SendMail", "收件人列表: ${receiverConfig.emails}")

    withContext(Dispatchers.IO) {
        val props = Properties().apply {
            put("mail.smtp.host", senderConfig.smtpHost)
            put("mail.smtp.port", senderConfig.smtpPort)
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
            if (senderConfig.useSsl) {
                put("mail.smtp.ssl.enable", "true")
                AppLogger.d("SendMail", "SMTP SSL 已启用")
            } else {
                put("mail.smtp.starttls.enable", "true")
                AppLogger.d("SendMail", "SMTP STARTTLS 已启用")
            }
        }

        AppLogger.d("SendMail", "正在创建邮件 Session...")
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(senderConfig.account, senderConfig.credential)
            }
        })

        try {
            AppLogger.d("SendMail", "正在构建邮件内容...")
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(senderConfig.account))
                receiverConfig.emails.forEach { email ->
                    addRecipient(Message.RecipientType.TO, InternetAddress(email.trim()))
                    AppLogger.d("SendMail", "添加收件人: $email")
                }
                subject = "收信号码:$devicePhoneNumber;来信号码：$sender"
                setText(messageBody)
            }
            AppLogger.i("SendMail", "邮件内容构建完成，准备发送...")

            Transport.send(message)
            AppLogger.i("SendMail", "邮件发送成功！")

        } catch (e: Exception) {
            AppLogger.e("SendMail", "发送邮件失败: ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        }
    }
}
