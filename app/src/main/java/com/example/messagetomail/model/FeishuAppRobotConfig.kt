package com.example.messagetomail.model

data class FeishuAppReceiver(
    val receiveId: String,
    val receiveIdType: String = autoDetectType(receiveId)
) {
    companion object {
        fun autoDetectType(receiveId: String): String {
            return when {
                receiveId.startsWith("oc_") -> "chat_id"
                receiveId.startsWith("ou_") -> "open_id"
                receiveId.startsWith("on_") -> "union_id"
                receiveId.contains("@") -> "email"
                else -> "chat_id"
            }
        }
    }
}

data class FeishuAppRobotConfig(
    val appId: String,
    val appSecret: String,
    val receivers: List<FeishuAppReceiver>,
    val enabled: Boolean
) {
    companion object {
        const val MAX_RECEIVERS = 10
    }
}
