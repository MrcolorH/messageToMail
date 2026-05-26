package com.example.messagetomail.model

data class SimFilterConfig(
    val enabledSubscriptions: Set<Int> = emptySet()
)
