package com.example.messagetomail.Utils

object SmsContentFormatter {

    private val codePatterns = listOf(
        // 验证码/校验码等 + 是为：: + 数字
        Regex("验证码[是为：:\\s]+([0-9]{4,8})"),
        Regex("校验码[是为：:\\s]+([0-9]{4,8})"),
        Regex("动态码[是为：:\\s]+([0-9]{4,8})"),
        Regex("授权码[是为：:\\s]+([0-9]{4,8})"),
        Regex("确认码[是为：:\\s]+([0-9]{4,8})"),
        // 数字 + 为/是 + ... + 验证码（如：291972为本次登录验证的手机验证码）
        Regex("([0-9]{4,8})[是为]?[的]?.*验证码"),
        // 数字 + ... + 验证码（更宽松，30字符内）
        Regex("([0-9]{4,8}).{0,30}验证码"),
        // 【平台】验证码是123456
        Regex("[【\\[]?([A-Za-z0-9一-龥]+)[】\\]]?.*验证码[是为：:\\s]+([0-9]{4,8})"),
        // 您的XX验证码是123456
        Regex("您的[\\s]*([A-Za-z0-9一-龥]{2,10})[\\s]*验证码[是为：:\\s]+([0-9]{4,8})"),
        // 英文验证码模式
        Regex("code[\\s]*[is:：][\\s]*([0-9]{4,8})", RegexOption.IGNORE_CASE),
        Regex("verification[\\s]+code[\\s]*[is:：]?[\\s]*([0-9]{4,8})", RegexOption.IGNORE_CASE),
        // 验证码在前，数字在后（宽松间隔）
        Regex("验证码.{0,30}([0-9]{4,8})"),
        // 数字在前，验证码在后（带"您的"）
        Regex("([0-9]{4,8})[是为]*[您你]的验证码")
    )

    private val bracketPlatformPattern = Regex("[【\\[]([一-龥A-Za-z0-9\\s]+)[】\\]]")

    /**
     * 格式化短信内容
     * - 包含验证码时：验证码 + 平台 + 来自 + SIM卡 + 正文
     * - 不包含验证码时：发送者 + SIM卡 + 正文
     */
    fun format(body: String, sender: String? = null, simLabel: String? = null): String {
        val codeResult = extractCode(body)
        val senderLine = if (!sender.isNullOrBlank() && sender != "未知号码") "来自：$sender" else ""
        val simLine = if (!simLabel.isNullOrBlank()) "SIM卡：$simLabel" else ""
        return if (codeResult != null) {
            val platform = extractPlatform(body) ?: ""
            val codeLine = "验证码：${codeResult.code}"
            val platformLine = if (platform.isNotBlank()) "平台：$platform" else ""
            val bodyLine = "正文：$body"
            listOf(codeLine, platformLine, senderLine, simLine, bodyLine).filter { it.isNotBlank() }.joinToString("\n")
        } else {
            listOf(senderLine, simLine, body).filter { it.isNotBlank() }.joinToString("\n")
        }
    }

    /**
     * 提取验证码和平台信息
     */
    fun extractCode(body: String): CodeResult? {
        for (pattern in codePatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val groups = match.groupValues
                // 不同正则的捕获组位置不同，找 4-8 位数字
                val code = groups.find { it.matches(Regex("^\\d{4,8}$")) }
                if (code != null) {
                    // 尝试提取平台名（如果正则中有平台捕获组）
                    val platform = groups.find {
                        it != code && it.isNotBlank() && !it.matches(Regex("^\\d+$"))
                    }
                    return CodeResult(code, platform)
                }
            }
        }
        return null
    }

    /**
     * 提取平台名
     * 1. 匹配【】或[]中的内容
     * 2. 匹配"您的XX验证码"中的XX
     * 3. 匹配验证码前面的 2-6 个汉字
     */
    fun extractPlatform(body: String): String? {
        // 1. 【】或[]
        bracketPlatformPattern.find(body)?.groupValues?.get(1)?.trim()?.let {
            if (it.isNotBlank()) return it
        }

        // 2. 您的XX验证码
        Regex("您的[\\s]*([A-Za-z0-9\\u4e00-\\u9fa5]{2,10})[\\s]*验证码").find(body)?.groupValues?.get(1)?.trim()?.let {
            if (it.isNotBlank()) return it
        }

        // 3. 验证码前最多 6 个汉字（作为平台名）
        Regex("([\\u4e00-\\u9fa5]{2,6})[\\s]*验证码").find(body)?.groupValues?.get(1)?.trim()?.let {
            if (it.isNotBlank() && it != "您的" && it != "你") return it
        }

        return null
    }

    data class CodeResult(val code: String, val platformHint: String?)
}
