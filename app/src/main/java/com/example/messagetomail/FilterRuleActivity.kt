package com.example.messagetomail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.messagetomail.model.FilterMode
import com.example.messagetomail.model.SmsFilterConfig
import com.example.messagetomail.repository.EmailConfigRepository
import kotlinx.coroutines.launch

class FilterRuleActivity : AppCompatActivity() {

    private lateinit var repository: EmailConfigRepository
    private lateinit var rgFilterMode: RadioGroup
    private lateinit var rbAll: RadioButton
    private lateinit var rbNotification: RadioButton
    private lateinit var rbCustom: RadioButton
    private lateinit var cardCustomRules: androidx.cardview.widget.CardView
    private lateinit var tvRuleCount: TextView
    private lateinit var containerRules: LinearLayout
    private lateinit var btnAddRule: Button
    private lateinit var btnSaveFilter: Button

    private val ruleEditTexts = mutableListOf<EditText>()
    private var currentConfig: SmsFilterConfig? = null

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, FilterRuleActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_rule)
        supportActionBar?.title = "转发规则"

        repository = EmailConfigRepository(this)
        initViews()
        setupListeners()
        loadConfig()
    }

    private fun initViews() {
        rgFilterMode = findViewById(R.id.rgFilterMode)
        rbAll = findViewById(R.id.rbAll)
        rbNotification = findViewById(R.id.rbNotification)
        rbCustom = findViewById(R.id.rbCustom)
        cardCustomRules = findViewById(R.id.cardCustomRules)
        tvRuleCount = findViewById(R.id.tvRuleCount)
        containerRules = findViewById(R.id.containerRules)
        btnAddRule = findViewById(R.id.btnAddRule)
        btnSaveFilter = findViewById(R.id.btnSaveFilter)
    }

    private fun setupListeners() {
        rgFilterMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbCustom -> cardCustomRules.visibility = LinearLayout.VISIBLE
                else -> cardCustomRules.visibility = LinearLayout.GONE
            }
        }

        btnAddRule.setOnClickListener {
            if (ruleEditTexts.size >= SmsFilterConfig.MAX_RULES) {
                Toast.makeText(this, "最多支持 ${SmsFilterConfig.MAX_RULES} 条规则", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addRuleItem("")
            updateRuleCount()
        }

        btnSaveFilter.setOnClickListener {
            saveConfig()
        }
    }

    private fun loadConfig() {
        lifecycleScope.launch {
            repository.smsFilterConfigFlow.collect { config ->
                currentConfig = config
                val mode = config?.filterMode ?: FilterMode.ALL
                when (mode) {
                    FilterMode.ALL -> rgFilterMode.check(R.id.rbAll)
                    FilterMode.NOTIFICATION -> rgFilterMode.check(R.id.rbNotification)
                    FilterMode.CUSTOM -> rgFilterMode.check(R.id.rbCustom)
                }

                containerRules.removeAllViews()
                ruleEditTexts.clear()
                val rules = config?.customRules ?: emptyList()
                rules.forEach { addRuleItem(it) }
                updateRuleCount()
            }
        }
    }

    private fun addRuleItem(text: String) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_filter_rule, containerRules, false)
        val etRule = itemView.findViewById<EditText>(R.id.etRulePattern)
        val btnDelete = itemView.findViewById<Button>(R.id.btnDeleteRule)

        etRule.setText(text)
        ruleEditTexts.add(etRule)

        btnDelete.setOnClickListener {
            containerRules.removeView(itemView)
            ruleEditTexts.remove(etRule)
            updateRuleCount()
        }

        containerRules.addView(itemView)
    }

    private fun updateRuleCount() {
        tvRuleCount.text = "已添加规则 (${ruleEditTexts.size}/${SmsFilterConfig.MAX_RULES})"
        btnAddRule.isEnabled = ruleEditTexts.size < SmsFilterConfig.MAX_RULES
    }

    private fun saveConfig() {
        val mode = when (rgFilterMode.checkedRadioButtonId) {
            R.id.rbNotification -> FilterMode.NOTIFICATION
            R.id.rbCustom -> FilterMode.CUSTOM
            else -> FilterMode.ALL
        }

        val rules = ruleEditTexts.map { it.text.toString().trim() }.filter { it.isNotBlank() }

        lifecycleScope.launch {
            repository.saveSmsFilterConfig(SmsFilterConfig(mode, rules))
            Toast.makeText(this@FilterRuleActivity, "规则已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
