package com.example.messagetomail.Utils

import com.example.messagetomail.model.FeishuAppReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object FeishuAppRobotSender {

    private const val TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"
    private const val MESSAGE_URL_TEMPLATE =
        "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=%s"

    suspend fun send(
        appId: String,
        appSecret: String,
        receiveId: String,
        receiveIdType: String,
        title: String,
        content: String
    ): Result<Unit> {
        return sendToMultiple(appId, appSecret, listOf(FeishuAppReceiver(receiveId, receiveIdType)), title, content)
    }

    suspend fun sendToMultiple(
        appId: String,
        appSecret: String,
        receivers: List<FeishuAppReceiver>,
        title: String,
        content: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (receivers.isEmpty()) {
                    return@withContext Result.failure(Exception("接收者列表为空"))
                }

                // 1. 获取 tenant_access_token（只获取一次，复用）
                val tokenResult = fetchTenantAccessToken(appId, appSecret)
                tokenResult.onFailure { return@withContext Result.failure(it) }
                val tenantAccessToken = tokenResult.getOrNull()
                    ?: return@withContext Result.failure(Exception("获取 token 失败"))

                // 2. 遍历发送给每个接收者
                val failures = mutableListOf<String>()
                receivers.forEach { receiver ->
                    val result = sendSingle(tenantAccessToken, receiver.receiveId, receiver.receiveIdType, title, content)
                    result.onFailure {
                        failures.add("${receiver.receiveId}: ${it.message}")
                    }
                }

                if (failures.isEmpty()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("部分发送失败: ${failures.joinToString("; ")}"))
                }
            } catch (e: Exception) {
                AppLogger.e("FeishuAppRobot", "飞书应用机器人发送失败", e)
                Result.failure(e)
            }
        }
    }

    private fun sendSingle(
        tenantAccessToken: String,
        receiveId: String,
        receiveIdType: String,
        title: String,
        content: String
    ): Result<Unit> {
        return try {
            val url = URL(MESSAGE_URL_TEMPLATE.format(receiveIdType))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Authorization", "Bearer $tenantAccessToken")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val contentJson = JSONObject().apply {
                put("text", "【短信转发】\n$title\n$content")
            }

            val json = JSONObject().apply {
                put("receive_id", receiveId)
                put("msg_type", "text")
                put("content", contentJson.toString())
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            AppLogger.i("FeishuAppRobot", "发送给 $receiveId 响应: code=$responseCode, body=$response")

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
            AppLogger.e("FeishuAppRobot", "发送给 $receiveId 失败", e)
            Result.failure(e)
        }
    }

    private fun fetchTenantAccessToken(appId: String, appSecret: String): Result<String> {
        return try {
            val url = URL(TOKEN_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val json = JSONObject().apply {
                put("app_id", appId)
                put("app_secret", appSecret)
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            AppLogger.i("FeishuAppRobot", "获取token响应: code=$responseCode, body=$response")

            if (responseCode == 200) {
                val respJson = JSONObject(response)
                val code = respJson.optInt("code", -1)
                if (code == 0) {
                    val token = respJson.optString("tenant_access_token", "")
                    if (token.isNotBlank()) {
                        Result.success(token)
                    } else {
                        Result.failure(Exception("token 为空"))
                    }
                } else {
                    val msg = respJson.optString("msg", "未知错误")
                    Result.failure(Exception("获取token失败($code): $msg"))
                }
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            AppLogger.e("FeishuAppRobot", "获取tenant_access_token失败", e)
            Result.failure(e)
        }
    }
}
