package com.example.messagetomail.Utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WecomRobotSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun send(webhookUrl: String, title: String, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val text = if (title.isNotBlank()) "【短信转发】\n$title\n$content" else "【短信转发】\n$content"
                val json = JSONObject().apply {
                    put("msgtype", "text")
                    put("text", JSONObject().apply {
                        put("content", text)
                    })
                }

                val body = json.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    AppLogger.i("WecomRobot", "企微机器人响应: code=${response.code}, body=$responseBody")

                    if (response.isSuccessful) {
                        val respJson = JSONObject(responseBody)
                        val errcode = respJson.optInt("errcode", -1)
                        if (errcode == 0) {
                            Result.success(Unit)
                        } else {
                            val errmsg = respJson.optString("errmsg", "未知错误")
                            Result.failure(Exception("企微API错误($errcode): $errmsg"))
                        }
                    } else {
                        Result.failure(Exception("HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("WecomRobot", "企微机器人发送失败", e)
                Result.failure(e)
            }
        }
    }
}
