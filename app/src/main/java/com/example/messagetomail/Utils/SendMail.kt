package com.example.messagetomail.Utils

import android.content.Context
import com.example.messagetomail.repository.EmailConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

fun sendEmail(context: Context, devicePhoneNumber: String?, sender: String?, messageBody: String) {
    AppLogger.i("SendMail", "=== 开始发送邮件流程 ===")
    AppLogger.d("SendMail", "参数: devicePhone=$devicePhoneNumber, sender=$sender, bodyLength=${messageBody.length}")

    val repository = EmailConfigRepository(context)

    // 读取配置（DataStore 是 Flow，需要阻塞读取最新值）
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

    val props = Properties().apply {
        put("mail.smtp.host", senderConfig.smtpHost)
        put("mail.smtp.port", senderConfig.smtpPort)
        put("mail.smtp.auth", "true")
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppLogger.i("SendMail", "正在调用 Transport.send()...")
                Transport.send(message)
                AppLogger.i("SendMail", "邮件发送成功！")
            } catch (e: MessagingException) {
                AppLogger.e("SendMail", "Transport.send() 失败", e)
            }
        }

    } catch (e: MessagingException) {
        AppLogger.e("SendMail", "构建或发送邮件时发生异常", e)
    }
}
