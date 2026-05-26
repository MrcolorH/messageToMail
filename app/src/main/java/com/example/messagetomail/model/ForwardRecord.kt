package com.example.messagetomail.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forward_records")
data class ForwardRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String,
    val body: String,
    val simLabel: String?,
    val channel: String,
    val success: Boolean,
    val errorReason: String?
)
