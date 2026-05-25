package com.example.messagetomail.Utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WecomRobotSender {

    private const val WEBHOOK_URL_TEMPLATE =
        "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=%s"

    suspend fun send(webhookKey: String, title: String, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(WEBHOOK_URL_TEMPLATE.format(webhookKey))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val json = JSONObject().apply {
                    put("msgtype", "text")
                    put("text", JSONObject().apply {
                        put("content", "【短信转发】\n$title\n$content")
                    })
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                AppLogger.i("WecomRobot", "企微机器人响应: code=$responseCode, body=$response")

                if (responseCode == 200) {
                    val respJson = JSONObject(response)
                    val errcode = respJson.optInt("errcode", -1)
                    if (errcode == 0) {
                        Result.success(Unit)
                    } else {
                        val errmsg = respJson.optString("errmsg", "未知错误")
                        Result.failure(Exception("企微API错误($errcode): $errmsg"))
                    }
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                AppLogger.e("WecomRobot", "企微机器人发送失败", e)
                Result.failure(e)
            }
        }
    }
}
