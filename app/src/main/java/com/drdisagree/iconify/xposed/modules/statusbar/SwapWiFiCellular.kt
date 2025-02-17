package com.drdisagree.iconify.xposed.modules.statusbar

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.STATUSBAR_SWAP_WIFI_CELLULAR
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.reAddView
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class SwapWiFiCellular(context: Context) : ModPack(context) {

    private var swapWifiAndCellularIcon = false

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            swapWifiAndCellularIcon = getBoolean(STATUSBAR_SWAP_WIFI_CELLULAR, false)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val statusIconContainerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.StatusIconContainer")

        statusIconContainerClass
            .hookMethod("onViewAdded")
            .runAfter { param ->
                if (!swapWifiAndCellularIcon) return@runAfter

                val parent = param.thisObject as ViewGroup

                val wifiView = parent.findViewById<View>(
                    mContext.resources.getIdentifier(
                        "wifi_combo",
                        "id",
                        mContext.packageName
                    )
                )
                val mobileView = parent.findViewById<View>(
                    mContext.resources.getIdentifier(
                        "mobile_combo",
                        "id",
                        mContext.packageName
                    )
                )
                val wifiIndex = parent.indexOfChild(wifiView)
                val mobileIndex = parent.indexOfChild(mobileView)

                if (wifiIndex != -1 && mobileIndex != -1 && mobileIndex > wifiIndex) {
                    parent.reAddView(wifiView, mobileIndex)
                }
            }
    }
}