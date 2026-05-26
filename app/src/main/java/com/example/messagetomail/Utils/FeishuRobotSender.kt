package com.example.messagetomail.Utils

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object FeishuRobotSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun send(webhookUrl: String, secret: String, title: String, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val text = if (title.isNotBlank()) "【短信转发】\n$title\n$content" else "【短信转发】\n$content"
                val json = JSONObject().apply {
                    put("msg_type", "text")
                    put("content", JSONObject().apply {
                        put("text", text)
                    })
                    if (secret.isNotBlank()) {
                        val timestamp = (System.currentTimeMillis() / 1000).toString()
                        val sign = genSign(secret, timestamp)
                        put("timestamp", timestamp)
                        put("sign", sign)
                    }
                }

                val body = json.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    AppLogger.i("FeishuRobot", "飞书机器人响应: code=${response.code}, body=$responseBody")

                    if (response.isSuccessful) {
                        val respJson = JSONObject(responseBody)
                        val code = respJson.optInt("code", -1)
                        if (code == 0) {
                            Result.success(Unit)
                        } else {
                            val msg = respJson.optString("msg", "未知错误")
                            Result.failure(Exception("飞书API错误($code): $msg"))
                        }
                    } else {
                        Result.failure(Exception("HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("FeishuRobot", "飞书机器人发送失败", e)
                Result.failure(e)
            }
        }
    }

    private fun genSign(secret: String, timestamp: String): String {
        val stringToSign = "$timestamp\n$secret"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(stringToSign.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signData = mac.doFinal(byteArrayOf())
        return Base64.encodeToString(signData, Base64.NO_WRAP)
    }
}
