package com.example.messagetomail

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.messagetomail.Utils.AppLogger

class LogActivity : AppCompatActivity() {

    private lateinit var listViewLogs: ListView
    private lateinit var btnClearLogs: Button
    private lateinit var adapter: LogAdapter

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LogActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        supportActionBar?.title = "运行日志"

        listViewLogs = findViewById(R.id.listViewLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)

        adapter = LogAdapter()
        listViewLogs.adapter = adapter

        btnClearLogs.setOnClickListener {
            AppLogger.getInstance(this).clearLogs()
            adapter.refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
    }

    private inner class LogAdapter : BaseAdapter() {
        private var logs = listOf<AppLogger.LogEntry>()

        init {
            refresh()
        }

        fun refresh() {
            logs = AppLogger.getInstance(this@LogActivity).getLogs()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = logs.size
        override fun getItem(position: Int): Any = logs[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@LogActivity)
                .inflate(R.layout.item_log, parent, false)

            val entry = logs[position]
            view.findViewById<TextView>(R.id.tvLogTime).text = entry.formatTime()

            val tvLevel = view.findViewById<TextView>(R.id.tvLogLevel)
            tvLevel.text = entry.level
            tvLevel.setTextColor(
                when (entry.level) {
                    AppLogger.LEVEL_DEBUG -> Color.GRAY
                    AppLogger.LEVEL_INFO -> Color.parseColor("#2E7D32")
                    AppLogger.LEVEL_WARN -> Color.parseColor("#F9A825")
                    AppLogger.LEVEL_ERROR -> Color.parseColor("#C62828")
                    else -> Color.BLACK
                }
            )

            view.findViewById<TextView>(R.id.tvLogTag).text = entry.tag
            view.findViewById<TextView>(R.id.tvLogMessage).text = entry.message

            return view
        }
    }
}
