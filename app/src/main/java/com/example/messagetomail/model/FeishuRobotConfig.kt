package com.example.messagetomail.model

data class FeishuRobotConfig(
    val webhookUrl: String,
    val secret: String,
    val enabled: Boolean
)
