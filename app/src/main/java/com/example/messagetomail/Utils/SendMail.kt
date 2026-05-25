package com.example.messagetomail.Utils

import android.content.Context
import android.util.Log
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
    val repository = EmailConfigRepository(context)

    // 读取配置（DataStore 是 Flow，需要阻塞读取最新值）
    var senderConfig = repository.getSenderConfigBlocking()
    var receiverConfig = repository.getReceiverConfigBlocking()

    if (senderConfig == null || receiverConfig == null || receiverConfig.emails.isEmpty()) {
        Log.e("SmsService", "邮件配置不完整，无法发送")
        return
    }

    val props = Properties().apply {
        put("mail.smtp.host", senderConfig.smtpHost)
        put("mail.smtp.port", senderConfig.smtpPort)
        put("mail.smtp.auth", "true")
        if (senderConfig.useSsl) {
            put("mail.smtp.ssl.enable", "true")
        } else {
            put("mail.smtp.starttls.enable", "true")
        }
    }

    val session = Session.getInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(senderConfig.account, senderConfig.credential)
        }
    })

    try {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(senderConfig.account))
            receiverConfig.emails.forEach { email ->
                addRecipient(Message.RecipientType.TO, InternetAddress(email.trim()))
            }
            subject = "收信号码:$devicePhoneNumber;来信号码：$sender"
            setText(messageBody)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Transport.send(message)
            } catch (e: MessagingException) {
                e.printStackTrace()
            }
        }

        Log.d("SmsService", "Email sent successfully!")
    } catch (e: MessagingException) {
        Log.e("SmsService", "Failed to send email", e)
    }
}
