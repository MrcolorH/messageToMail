package com.example.messagetomail.Utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.messagetomail.model.SimInfo

object SimInfoHelper {

    fun getActiveSims(context: Context): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return emptyList()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            subscriptionManager.activeSubscriptionInfoList?.map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.carrierName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        info.number?.toString()
                    } else null
                )
            } ?: emptyList()
        } else {
            emptyList()
        }
    }
}
