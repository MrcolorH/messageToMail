package com.example.messagetomail.Utils

import android.util.Log
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

fun sendEmail(devicePhoneNumber:String?, sender: String?, messageBody: String) {
//    var
    // QQ 邮箱配置
    val host = "smtp.qq.com" // QQ 邮箱的 SMTP 服务器
    val port = "465" // SSL 端口
    val fromEmail = "q1419046431@qq.com" // 发件人邮箱
    val password = "nzghzvtcrloodgeb" // 邮箱授权码
    val toEmail = "1419046431@qq.com" // 收件人邮箱

    // 设置邮件属性
    val props = Properties().apply {
        put("mail.smtp.host", host)
        put("mail.smtp.port", port)
        put("mail.smtp.auth", "true")
        put("mail.smtp.ssl.enable", "true") // 启用 SSL
    }

    // 创建会话
    val session = Session.getInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(fromEmail, password)
        }
    })

    try {
        // 创建邮件
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromEmail))
            // 处理多个收件人地址
            toEmail.split(",").forEach { email ->
                addRecipient(Message.RecipientType.TO, InternetAddress(email.trim()))
            }
            subject = "收信号码:$devicePhoneNumber;来信号码：$sender" // 邮件主题
            setText(messageBody) // 邮件内容
        }

        // 发送邮件
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Transport.send(message)
            }
            catch (e: MessagingException){
                e.printStackTrace()
            }
        }

        Log.d("SmsService", "Email sent successfully!")
    } catch (e: MessagingException) {
        Log.e("SmsService", "Failed to send email", e)

    }
}