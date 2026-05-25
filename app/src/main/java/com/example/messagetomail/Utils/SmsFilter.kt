package com.example.messagetomail.Utils

import com.example.messagetomail.model.FilterMode
import com.example.messagetomail.model.SmsFilterConfig

object SmsFilter {

    /**
     * 判断一条短信是否应该被转发
     */
    fun shouldForward(body: String, config: SmsFilterConfig?): Boolean {
        val mode = config?.filterMode ?: FilterMode.ALL
        return when (mode) {
            FilterMode.ALL -> true
            FilterMode.NOTIFICATION -> isNotificationSms(body)
            FilterMode.CUSTOM -> matchesCustomRules(body, config?.customRules ?: emptyList())
        }
    }

    /**
     * 是否为通知类短信（验证码/校验码/动态码等）
     */
    fun isNotificationSms(body: String): Boolean {
        val keywords = listOf(
            "验证码", "校验码", "动态码", "授权码", "确认码", "短信验证码",
            "verification code", "verification code", "auth code", "dynamic code",
            "one-time password", "OTP", "PIN码", "安全码"
        )
        val lowerBody = body.lowercase()
        return keywords.any { lowerBody.contains(it.lowercase()) }
                || containsCodeNumber(body)
    }

    /**
     * 短信中是否包含 4-8 位连续数字（常见验证码格式）
     */
    private fun containsCodeNumber(body: String): Boolean {
        return Regex("(?<![0-9])[0-9]{4,8}(?![0-9])").containsMatchIn(body)
    }

    /**
     * 是否匹配用户自定义的正则规则
     */
    private fun matchesCustomRules(body: String, rules: List<String>): Boolean {
        if (rules.isEmpty()) return false
        return rules.any { pattern ->
            pattern.isNotBlank() && runCatching {
                Regex(pattern).containsMatchIn(body)
            }.getOrDefault(false)
        }
    }
}
