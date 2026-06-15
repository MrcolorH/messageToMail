package com.example.messagetomail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, HelpActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        supportActionBar?.title = "使用说明"

        val tvHelpContent = findViewById<TextView>(R.id.tvHelpContent)
        tvHelpContent.text = HELP_TEXT
    }
}

private const val HELP_TEXT = """
⚠️ 小米手机特别注意事项
━━━━━━━━━━━━━━━━━━━━

小米手机用户开启服务后，若发现收不到验证码/银行/快递等通知类短信，请前往：

设置 → 应用设置 → 应用管理 → 短信转邮件 → 权限管理 → 其它权限

找到「允许获取通知类短信」并设置为「始终允许」。

该权限直接影响验证码、银行提醒、物流通知等系统短信的读取，未开启时应用无法监听此类短信。

━━━━━━━━━━━━━━━━━━━━
本应用用于将手机短信实时转发到其他通知渠道，支持邮件、企业微信机器人和飞书机器人。

━━━━━━━━━━━━━━━━━━━━
一、通用使用流程
━━━━━━━━━━━━━━━━━━━━

1. 首次打开应用，先点击「服务配置」完成至少一个渠道的配置
2. 返回主界面，点击「开启服务」启动短信监听
3. 收到新短信时，应用会自动推送到已启用的渠道
4. 可在「查看日志」中排查发送异常

━━━━━━━━━━━━━━━━━━━━
二、邮件通知配置
━━━━━━━━━━━━━━━━━━━━

1. 选择邮箱类型（QQ 邮箱或普通邮箱）
2. 填写发件人账号和密码/授权码
   - QQ 邮箱必须使用「授权码」而非登录密码
   - 授权码在 QQ 邮箱网页版 → 设置 → 账户 → 生成授权码
3. 填写收件人邮箱地址（最多10个）
4. 勾选「启用邮件通知」
5. 点击「测试并保存」验证连通性

━━━━━━━━━━━━━━━━━━━━
三、企业微信机器人配置
━━━━━━━━━━━━━━━━━━━━

1. 在企微群聊中点击右上角「···」→「群机器人」→「添加机器人」
2. 选择「新建机器人」，设置名称后复制完整的 Webhook 地址
   格式：https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
3. 将完整的 Webhook 地址粘贴到 App 的「企业微信 Webhook 地址」输入框
4. 勾选「启用企业微信机器人通知」
5. 点击「测试企微机器人」验证

━━━━━━━━━━━━━━━━━━━━
四、飞书群机器人配置（自定义机器人）
━━━━━━━━━━━━━━━━━━━━

1. 在飞书群聊中点击右上角「···」→「群机器人」→「添加机器人」
2. 选择「自定义机器人」，设置名称后复制完整的 Webhook 地址
   格式：https://open.feishu.cn/open-apis/bot/v2/hook/xxx
3. 将完整的 Webhook 地址粘贴到 App 的「飞书 Webhook 地址」输入框
4. Secret（可选）：如开启了签名校验，填写机器人详情页中的 Secret
5. 勾选「启用飞书机器人通知」
6. 点击「测试飞书机器人」验证

━━━━━━━━━━━━━━━━━━━━
五、飞书应用机器人配置
━━━━━━━━━━━━━━━━━━━━

此方式需要你在飞书开放平台创建应用，适合需要发到多个群或个人的场景。

【5.1 获取 App ID 和 App Secret】
1. 访问飞书开放平台 https://open.feishu.cn
2. 登录后点击右上角「开发者后台」
3. 创建企业自建应用 → 进入应用详情页
4. 在「凭证与基础信息」中复制：
   - App ID（例如 cli_xxxxxxxx）
   - App Secret（点击眼睛图标查看）
5. 将两者分别填入 App 的「飞书 App ID」和「飞书 App Secret」输入框

【5.2 获取 chat_id（群聊ID）】
1. 打开目标飞书群聊 → 右上角「···」→「设置」
2. 在「群信息」中找到「群ID」，格式为 oc_xxx
3. 如未显示，需联系管理员在管理后台开启「群ID可见」
4. 在 App 中点击「添加接收者」，ID 填 oc_xxx 即可，类型会自动识别

【5.3 获取 open_id（个人用户ID）】
方式一（推荐）：
1. 进入飞书开放平台开发者后台
2. 左侧菜单「测试企业和人员」
3. 添加目标用户为测试人员后即可看到其 Open ID（ou_xxx）

方式二（管理员权限）：
1. 访问飞书管理后台 https://admin.feishu.cn
2. 通讯录 → 组织架构 → 点击具体成员
3. 展开「开发者信息」→ 复制 Open ID

方式三（API 查询）：
如已知对方飞书邮箱，可调用接口查询：
POST /open-apis/contact/v3/users/batch_get_id
Body: {"emails":["user@company.com"]}

【5.4 添加接收者】
方式一（推荐）：快速从群聊选择
1. 填写 App ID 和 App Secret 后，点击「📋 快速获取接收者（从群聊选择）」
2. App 会自动查询你的应用已加入的所有群聊
3. 在列表中点击「选择」即可将群聊添加为接收者

方式二：手动输入
1. 在输入框粘贴接收者 ID
2. 类型会根据 ID 前缀自动识别：
   - oc_ 开头 → 自动识别为 chat_id（群聊）
   - ou_ 开头 → 自动识别为 open_id（个人）
   - on_ 开头 → 自动识别为 union_id
   - 其他 → 默认 chat_id
3. 点击「添加接收者」加入列表

- 最多支持 10 个接收者
- 删除列表中的接收者请点击右侧「删除」按钮

【5.5 完成配置】
1. 填写 App ID 和 App Secret
2. 添加至少一个接收者
3. 勾选「启用飞书应用机器人通知」
4. 点击「测试飞书应用机器人」验证

━━━━━━━━━━━━━━━━━━━━
六、常见问题
━━━━━━━━━━━━━━━━━━━━

Q：测试机器人成功但收不到短信通知？
A：请确认「启用」开关已打开，并在「查看日志」中检查是否有 SMS 触发记录。

Q：飞书应用机器人提示「权限不足」？
A：请确保你的应用已开通「机器人」能力，且应用已发布到企业或添加到了目标群聊。

Q：如何导出/导入配置？
A：在服务配置页面点击「导出配置」可将当前配置复制到剪贴板，在新设备上点击「导入配置」粘贴即可。
"""
