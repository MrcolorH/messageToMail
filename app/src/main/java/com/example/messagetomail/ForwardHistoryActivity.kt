package com.example.messagetomail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.messagetomail.database.AppDatabase
import com.example.messagetomail.model.ForwardRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForwardHistoryActivity : AppCompatActivity() {

    private lateinit var tvTotalSuccess: TextView
    private lateinit var tvTotalFailure: TextView
    private lateinit var btnClearHistory: Button
    private lateinit var containerHistory: LinearLayout
    private lateinit var tvEmptyHistory: TextView

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ForwardHistoryActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forward_history)
        supportActionBar?.title = "转发记录"

        tvTotalSuccess = findViewById(R.id.tvTotalSuccess)
        tvTotalFailure = findViewById(R.id.tvTotalFailure)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        containerHistory = findViewById(R.id.containerHistory)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)

        btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要删除所有转发记录吗？")
                .setPositiveButton("清空") { _, _ ->
                    lifecycleScope.launch {
                        AppDatabase.getDatabase(this@ForwardHistoryActivity).forwardRecordDao().clearAll()
                        Toast.makeText(this@ForwardHistoryActivity, "记录已清空", Toast.LENGTH_SHORT).show()
                        loadHistory()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(this@ForwardHistoryActivity).forwardRecordDao()
            val records = dao.getRecent(100)
            val successCount = dao.getSuccessCount()
            val failureCount = dao.getFailureCount()

            tvTotalSuccess.text = "成功：$successCount"
            tvTotalFailure.text = "失败：$failureCount"

            containerHistory.removeAllViews()
            if (records.isEmpty()) {
                tvEmptyHistory.visibility = TextView.VISIBLE
            } else {
                tvEmptyHistory.visibility = TextView.GONE
                records.forEach { addHistoryItem(it) }
            }
        }
    }

    private fun addHistoryItem(record: ForwardRecord) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_forward_record, containerHistory, false)

        val tvTime = itemView.findViewById<TextView>(R.id.tvRecordTime)
        val tvChannel = itemView.findViewById<TextView>(R.id.tvRecordChannel)
        val tvSender = itemView.findViewById<TextView>(R.id.tvRecordSender)
        val tvStatus = itemView.findViewById<TextView>(R.id.tvRecordStatus)
        val tvBody = itemView.findViewById<TextView>(R.id.tvRecordBody)

        val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        tvTime.text = sdf.format(Date(record.timestamp))
        tvChannel.text = channelLabel(record.channel)
        tvSender.text = "来自：${record.sender}"
        tvBody.text = record.body.take(120)

        if (record.success) {
            tvStatus.text = "✅ 成功"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "❌ ${record.errorReason ?: "失败"}"
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        }

        containerHistory.addView(itemView)
    }

    private fun channelLabel(channel: String): String {
        return when (channel) {
            "email" -> "📧 邮件"
            "wecom" -> "💬 企微"
            "feishu" -> "📎 飞书"
            "feishu_app" -> "🤖 飞书应用"
            else -> channel
        }
    }
}
