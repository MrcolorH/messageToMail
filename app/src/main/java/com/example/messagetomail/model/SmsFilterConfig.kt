package com.example.messagetomail.model

enum class FilterMode {
    ALL,
    NOTIFICATION,
    CUSTOM
}

data class SmsFilterConfig(
    val filterMode: FilterMode = FilterMode.ALL,
    val customRules: List<String> = emptyList()
) {
    companion object {
        const val MAX_RULES = 10
    }
}
