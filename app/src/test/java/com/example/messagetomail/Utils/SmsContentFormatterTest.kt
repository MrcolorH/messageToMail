package com.example.messagetomail.Utils

import org.junit.Assert.*
import org.junit.Test

class SmsContentFormatterTest {

    @Test
    fun `format verification code with platform`() {
        val body = "【支付宝】验证码 9527，用于登录，请勿告知他人。"
        val result = SmsContentFormatter.format(body, "95188")
        assertTrue(result.contains("验证码：9527"))
        assertTrue(result.contains("平台：支付宝"))
        assertTrue(result.contains("来自：95188"))
    }

    @Test
    fun `format verification code without platform bracket`() {
        val body = "您的美团验证码是 123456，请勿泄露。"
        val result = SmsContentFormatter.format(body, "1069099988")
        assertTrue(result.contains("验证码：123456"))
        assertTrue(result.contains("平台：美团"))
    }

    @Test
    fun `format plain message without code`() {
        val body = "今晚一起吃饭吗？"
        val result = SmsContentFormatter.format(body, "13800138000")
        assertFalse(result.contains("验证码"))
        assertTrue(result.contains("来自：13800138000"))
        assertTrue(result.contains(body))
    }

    @Test
    fun `format includes simLabel when provided`() {
        val body = "测试短信"
        val result = SmsContentFormatter.format(body, "10086", "中国移动 [卡槽1]")
        assertTrue(result.contains("SIM卡：中国移动 [卡槽1]"))
    }

    @Test
    fun `extractCode finds 6 digit code`() {
        val code = SmsContentFormatter.extractCode("code: 123456")
        assertNotNull(code)
        assertEquals("123456", code?.code)
    }

    @Test
    fun `extractCode returns null for no code`() {
        val code = SmsContentFormatter.extractCode("hello world")
        assertNull(code)
    }

    @Test
    fun `extractPlatform finds bracketed name`() {
        val platform = SmsContentFormatter.extractPlatform("【微信团队】验证码是 8888")
        assertEquals("微信团队", platform)
    }

    @Test
    fun `extractPlatform returns null when absent`() {
        val platform = SmsContentFormatter.extractPlatform(" plain text without platform ")
        assertNull(platform)
    }
}
