package com.example.messagetomail.Utils

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object KeepAliveHelper {

    fun showGuideDialog(activity: Activity, onDismiss: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("后台保活设置建议")
            .setMessage(
                "为确保短信转发服务稳定运行，请根据您的手机品牌完成以下设置：\n\n" +
                        "1. 关闭本应用的电池优化/省电限制\n" +
                        "2. 开启自启动权限\n" +
                        "3. 将应用加入后台白名单"
            )
            .setPositiveButton("前往设置") { _, _ ->
                openPowerSettings(activity)
                onDismiss()
            }
            .setNegativeButton("不再提示") { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun openPowerSettings(activity: Activity) {
        val ctx = activity.applicationContext

        val launched = tryManufacturerIntent(ctx)
        if (!launched) {
            openBatteryOptimization(activity)
        }
    }

    private fun tryManufacturerIntent(ctx: android.content.Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val intent = when {
            // 小米 / 红米 (MIUI / HyperOS)
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || brand.contains("xiaomi") || brand.contains("redmi") -> {
                tryLaunch(
                    ctx,
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                )
            }
            // 华为 / 老荣耀 (EMUI) 与 新荣耀 (MagicOS)
            manufacturer.contains("honor") || brand.contains("honor") -> {
                tryLaunch(
                    ctx,
                    ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                ) ?: tryLaunch(
                    ctx,
                    ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                )
            }
            manufacturer.contains("huawei") || brand.contains("huawei") -> {
                tryLaunch(
                    ctx,
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                )
            }
            // OPPO / 一加 / Realme (ColorOS)
            manufacturer.contains("oppo") || brand.contains("oppo") || manufacturer.contains("realme") || brand.contains("realme") -> {
                tryLaunch(
                    ctx,
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    ComponentName("com.oplus.battery", "com.oplus.battery.PowerConsumptionOptimizationActivity")
                ) ?: tryLaunch(
                    ctx,
                    ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                    null
                )
            }
            // vivo / iQOO (OriginOS / FuntouchOS)
            manufacturer.contains("vivo") || brand.contains("vivo") || manufacturer.contains("iqoo") || brand.contains("iqoo") -> {
                val vivoIntent = Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                    )
                    putExtra("packagename", ctx.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (ctx.packageManager.resolveActivity(vivoIntent, 0) != null) {
                    vivoIntent
                } else {
                    tryLaunch(
                        ctx,
                        ComponentName("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"),
                        null
                    )
                }
            }
            // 三星 (OneUI)
            manufacturer.contains("samsung") || brand.contains("samsung") -> {
                tryLaunch(
                    ctx,
                    ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.AppSleepListActivity"),
                    ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.AppSleepListActivity")
                )
            }
            // 一加 (原生/早期系统)
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> {
                tryLaunch(
                    ctx,
                    ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                    null
                )
            }
            // 魅族 (Flyme)
            manufacturer.contains("meizu") || brand.contains("meizu") -> {
                tryLaunch(
                    ctx,
                    ComponentName("com.meizu.safe", "com.meizu.safe.powerui.PowerAppPermissionActivity"),
                    null
                )
            }
            else -> null
        }

        if (intent != null) {
            return tryStartActivity(ctx, intent)
        }
        return false
    }

    private fun tryLaunch(
        ctx: android.content.Context,
        primary: ComponentName,
        fallback: ComponentName?
    ): Intent? {
        val intent = Intent().apply {
            component = primary
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (ctx.packageManager.resolveActivity(intent, 0) != null) return intent

        if (fallback != null) {
            val fallbackIntent = Intent().apply {
                component = fallback
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (ctx.packageManager.resolveActivity(fallbackIntent, 0) != null) return fallbackIntent
        }
        return null
    }

    private fun tryStartActivity(ctx: android.content.Context, intent: Intent): Boolean {
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openBatteryOptimization(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                return
            } catch (e: Exception) {
                // 部分定制ROM可能禁用此Action，回退到设置列表
            }
        }
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (ex: Exception) {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
}
