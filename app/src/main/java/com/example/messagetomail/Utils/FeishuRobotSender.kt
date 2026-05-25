package com.example.messagetomail.Utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

object FeishuRobotSender {

    suspend fun send(webhookUrl: String, secret: String, title: String, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(webhookUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val text = if (title.isNotBlank()) "【短信转发】\n$title\n$content" else "【短信转发】\n$content"
                val json = JSONObject().apply {
                    put("msg_type", "text")
                    put("content", JSONObject().apply {
                        put("text", text)
                    })
                    // 如果配置了 secret，添加签名校验
                    if (secret.isNotBlank()) {
                        val timestamp = (System.currentTimeMillis() / 1000).toString()
                        val sign = genSign(secret, timestamp)
                        put("timestamp", timestamp)
                        put("sign", sign)
                    }
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                AppLogger.i("FeishuRobot", "飞书机器人响应: code=$responseCode, body=$response")

                if (responseCode == 200) {
                    val respJson = JSONObject(response)
                    val code = respJson.optInt("code", -1)
                    if (code == 0) {
                        Result.success(Unit)
                    } else {
                        val msg = respJson.optString("msg", "未知错误")
                        Result.failure(Exception("飞书API错误($code): $msg"))
                    }
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
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
