package com.example.messagetomail.Utils

import com.example.messagetomail.model.FilterMode
import com.example.messagetomail.model.SmsFilterConfig
import org.junit.Assert.*
import org.junit.Test

class SmsFilterTest {

    @Test
    fun `ALL mode forwards everything`() {
        val config = SmsFilterConfig(FilterMode.ALL, emptyList())
        assertTrue(SmsFilter.shouldForward("any content", "any sender", config))
        assertTrue(SmsFilter.shouldForward("", "", config))
    }

    @Test
    fun `NOTIFICATION mode matches verification code keywords`() {
        val config = SmsFilterConfig(FilterMode.NOTIFICATION, emptyList())
        assertTrue(SmsFilter.shouldForward("您的验证码是 123456", "10086", config))
        assertTrue(SmsFilter.shouldForward("Your verification code is 8888", "Google", config))
        assertFalse(SmsFilter.shouldForward("今晚一起吃饭吧", "朋友", config))
    }

    @Test
    fun `NOTIFICATION mode matches 4-8 digit code`() {
        val config = SmsFilterConfig(FilterMode.NOTIFICATION, emptyList())
        assertTrue(SmsFilter.shouldForward("【银行】您的动态码为 987654", "95588", config))
        assertFalse(SmsFilter.shouldForward("123", "test", config)) // too short
    }

    @Test
    fun `CUSTOM mode matches body`() {
        val config = SmsFilterConfig(FilterMode.CUSTOM, listOf("快递", "包裹"))
        assertTrue(SmsFilter.shouldForward("您的快递已到达", "顺丰", config))
        assertFalse(SmsFilter.shouldForward("广告促销", "10086", config))
    }

    @Test
    fun `CUSTOM mode matches sender`() {
        val config = SmsFilterConfig(FilterMode.CUSTOM, listOf("10086"))
        assertTrue(SmsFilter.shouldForward("余额不足", "10086", config))
        assertFalse(SmsFilter.shouldForward("余额不足", "10010", config))
    }

    @Test
    fun `CUSTOM mode matches sender name`() {
        val config = SmsFilterConfig(FilterMode.CUSTOM, listOf("顺丰"))
        assertTrue(SmsFilter.shouldForward("任何内容", "顺丰速运", config))
    }

    @Test
    fun `CUSTOM mode with empty rules blocks everything`() {
        val config = SmsFilterConfig(FilterMode.CUSTOM, emptyList())
        assertFalse(SmsFilter.shouldForward("anything", "any", config))
    }

    @Test
    fun `null config defaults to ALL`() {
        assertTrue(SmsFilter.shouldForward("anything", "any", null))
    }

    @Test
    fun `invalid regex is safely ignored`() {
        val config = SmsFilterConfig(FilterMode.CUSTOM, listOf("[invalid", "valid"))
        assertTrue(SmsFilter.shouldForward("this is valid", "sender", config))
    }
}
