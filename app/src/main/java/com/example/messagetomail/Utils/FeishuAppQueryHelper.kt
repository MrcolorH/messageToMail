package com.example.messagetomail.Utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FeishuChatInfo(
    val chatId: String,
    val name: String
)

object FeishuAppQueryHelper {

    private const val TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"
    private const val CHAT_LIST_URL = "https://open.feishu.cn/open-apis/im/v1/chats?user_id_type=open_id&page_size=100"

    suspend fun queryChatList(appId: String, appSecret: String): Result<List<FeishuChatInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val tokenResult = fetchTenantAccessToken(appId, appSecret)
                tokenResult.onFailure { return@withContext Result.failure(it) }
                val token = tokenResult.getOrNull()
                    ?: return@withContext Result.failure(Exception("获取 token 失败"))

                val url = URL(CHAT_LIST_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                AppLogger.i("FeishuAppQuery", "查询群列表响应: code=$responseCode, body=$response")

                if (responseCode == 200) {
                    val respJson = JSONObject(response)
                    val code = respJson.optInt("code", -1)
                    if (code == 0) {
                        val data = respJson.optJSONObject("data")
                        val items = data?.optJSONArray("items")
                        val list = mutableListOf<FeishuChatInfo>()
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val chatId = item.optString("chat_id", "")
                                val name = item.optString("name", "未命名群聊")
                                if (chatId.isNotBlank()) {
                                    list.add(FeishuChatInfo(chatId, name))
                                }
                            }
                        }
                        Result.success(list)
                    } else {
                        val msg = respJson.optString("msg", "未知错误")
                        Result.failure(Exception("飞书API错误($code): $msg"))
                    }
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                AppLogger.e("FeishuAppQuery", "查询群列表失败", e)
                Result.failure(e)
            }
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

            if (responseCode == 200) {
                val respJson = JSONObject(response)
                val code = respJson.optInt("code", -1)
                if (code == 0) {
                    val token = respJson.optString("tenant_access_token", "")
                    if (token.isNotBlank()) Result.success(token) else Result.failure(Exception("token 为空"))
                } else {
                    Result.failure(Exception("获取token失败($code): ${respJson.optString("msg")}"))
                }
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
