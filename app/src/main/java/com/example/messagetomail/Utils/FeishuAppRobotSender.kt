package com.example.messagetomail.Utils

import com.example.messagetomail.model.FeishuAppReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FeishuAppRobotSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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

                val tokenResult = fetchTenantAccessToken(appId, appSecret)
                tokenResult.onFailure { return@withContext Result.failure(it) }
                val tenantAccessToken = tokenResult.getOrNull()
                    ?: return@withContext Result.failure(Exception("获取 token 失败"))

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
            val text = if (title.isNotBlank()) "【短信转发】\n$title\n$content" else "【短信转发】\n$content"
            val contentJson = JSONObject().apply {
                put("text", text)
            }
            val json = JSONObject().apply {
                put("receive_id", receiveId)
                put("msg_type", "text")
                put("content", contentJson.toString())
            }

            val body = json.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            val request = Request.Builder()
                .url(MESSAGE_URL_TEMPLATE.format(receiveIdType))
                .post(body)
                .header("Authorization", "Bearer $tenantAccessToken")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                AppLogger.i("FeishuAppRobot", "发送给 $receiveId 响应: code=${response.code}, body=$responseBody")

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
            AppLogger.e("FeishuAppRobot", "发送给 $receiveId 失败", e)
            Result.failure(e)
        }
    }

    private fun fetchTenantAccessToken(appId: String, appSecret: String): Result<String> {
        return try {
            val json = JSONObject().apply {
                put("app_id", appId)
                put("app_secret", appSecret)
            }

            val body = json.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                AppLogger.i("FeishuAppRobot", "获取token响应: code=${response.code}, body=$responseBody")

                if (response.isSuccessful) {
                    val respJson = JSONObject(responseBody)
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
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("FeishuAppRobot", "获取tenant_access_token失败", e)
            Result.failure(e)
        }
    }
}
