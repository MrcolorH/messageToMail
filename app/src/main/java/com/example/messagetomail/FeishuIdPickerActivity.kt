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
import com.example.messagetomail.Utils.AppLogger
import com.example.messagetomail.Utils.FeishuAppQueryHelper
import kotlinx.coroutines.launch

class FeishuIdPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_APP_SECRET = "app_secret"
        const val RESULT_RECEIVE_ID = "receive_id"

        fun createIntent(context: Context, appId: String, appSecret: String): Intent {
            return Intent(context, FeishuIdPickerActivity::class.java).apply {
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_APP_SECRET, appSecret)
            }
        }
    }

    private lateinit var tvQueryStatus: TextView
    private lateinit var containerChatList: LinearLayout
    private lateinit var btnManualInput: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feishu_id_picker)
        supportActionBar?.title = "选择群聊"

        tvQueryStatus = findViewById(R.id.tvQueryStatus)
        containerChatList = findViewById(R.id.containerChatList)
        btnManualInput = findViewById(R.id.btnManualInput)

        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: ""
        val appSecret = intent.getStringExtra(EXTRA_APP_SECRET) ?: ""

        if (appId.isEmpty() || appSecret.isEmpty()) {
            tvQueryStatus.text = "❌ 请先填写 App ID 和 App Secret"
            return
        }

        queryChatList(appId, appSecret)

        btnManualInput.setOnClickListener {
            showManualInputDialog()
        }
    }

    private fun queryChatList(appId: String, appSecret: String) {
        tvQueryStatus.text = "⏳ 正在查询群列表..."
        lifecycleScope.launch {
            val result = FeishuAppQueryHelper.queryChatList(appId, appSecret)
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    tvQueryStatus.text = "⚠️ 未找到任何群聊，请确认机器人已添加到群聊中"
                } else {
                    tvQueryStatus.text = "✅ 共找到 ${list.size} 个群聊，点击「选择」添加"
                    renderChatList(list)
                }
            }.onFailure {
                AppLogger.e("FeishuIdPicker", "查询群列表失败: ${it.message}")
                tvQueryStatus.text = "❌ 查询失败：${it.message}"
            }
        }
    }

    private fun renderChatList(list: List<com.example.messagetomail.Utils.FeishuChatInfo>) {
        containerChatList.removeAllViews()
        val inflater = LayoutInflater.from(this)

        list.forEach { chat ->
            val itemView = inflater.inflate(R.layout.item_feishu_chat, containerChatList, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvChatName)
            val tvId = itemView.findViewById<TextView>(R.id.tvChatId)
            val btnSelect = itemView.findViewById<Button>(R.id.btnSelectChat)

            tvName.text = chat.name
            tvId.text = chat.chatId
            btnSelect.setOnClickListener {
                val intent = Intent().apply {
                    putExtra(RESULT_RECEIVE_ID, chat.chatId)
                }
                setResult(RESULT_OK, intent)
                finish()
            }

            containerChatList.addView(itemView)
        }
    }

    private fun showManualInputDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "例如：oc_xxx 或 ou_xxx"
        }
        AlertDialog.Builder(this)
            .setTitle("手动输入接收者 ID")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val id = editText.text.toString().trim()
                if (id.isNotEmpty()) {
                    val intent = Intent().apply {
                        putExtra(RESULT_RECEIVE_ID, id)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    Toast.makeText(this, "ID 不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
