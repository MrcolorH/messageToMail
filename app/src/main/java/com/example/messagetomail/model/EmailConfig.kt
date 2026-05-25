package com.example.messagetomail.model

enum class EmailType {
    NORMAL, QQ
}

data class SenderConfig(
    val emailType: EmailType,
    val account: String,
    val credential: String,
    val smtpHost: String,
    val smtpPort: String,
    val useSsl: Boolean
) {
    companion object {
        fun getPreset(type: EmailType): SenderConfig {
            return when (type) {
                EmailType.QQ -> SenderConfig(
                    emailType = EmailType.QQ,
                    account = "",
                    credential = "",
                    smtpHost = "smtp.qq.com",
                    smtpPort = "465",
                    useSsl = true
                )
                EmailType.NORMAL -> SenderConfig(
                    emailType = EmailType.NORMAL,
                    account = "",
                    credential = "",
                    smtpHost = "",
                    smtpPort = "465",
                    useSsl = true
                )
            }
        }
    }
}

data class ReceiverConfig(
    val emails: List<String>
) {
    companion object {
        const val MAX_RECEIVERS = 10
    }
}
