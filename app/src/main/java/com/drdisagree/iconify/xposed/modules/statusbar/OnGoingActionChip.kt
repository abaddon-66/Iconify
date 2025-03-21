package com.drdisagree.iconify.xposed.modules.statusbar

import android.annotation.SuppressLint
import android.content.Context
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.ONGOING_ACTION_CHIP_SWITCH
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.reAddView
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.views.ongoingactionchip.OnGoingActionChipView
import com.drdisagree.iconify.xposed.modules.extras.views.ongoingactionchip.OnGoingActionProgressController
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class OnGoingActionChip(context: Context) : ModPack(context) {

    private var onGoingActionChipEnabled = false
    private var mOnGoingActionChipView: OnGoingActionChipView? = null
    private var mOnGoingActionProgressController: OnGoingActionProgressController? = null

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            onGoingActionChipEnabled = getBoolean(ONGOING_ACTION_CHIP_SWITCH, false)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val phoneStatusBarViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.PhoneStatusBarView")

        phoneStatusBarViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                val phoneStatusBarView = param.thisObject as ViewGroup
                val secondaryChip = phoneStatusBarView.findViewById<View>(
                    mContext.resources.getIdentifier(
                        "ongoing_activity_chip_secondary",
                        "id",
                        mContext.packageName
                    )
                )
                val startSideExceptHeadsUp = secondaryChip.parent as ViewGroup
                val secondaryChipIndex = startSideExceptHeadsUp.indexOfChild(secondaryChip)

                if (mOnGoingActionChipView == null) {
                    mOnGoingActionChipView = OnGoingActionChipView(mContext)
                }

                if (mOnGoingActionProgressController == null) {
                    mOnGoingActionProgressController = OnGoingActionProgressController(
                        mContext,
                        mOnGoingActionChipView!!
                    ) { onGoingActionChipEnabled }
                }

                startSideExceptHeadsUp.reAddView(mOnGoingActionChipView, secondaryChipIndex + 1)
            }

        val notificationListenerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.NotificationListener")

        notificationListenerClass
            .hookMethod("onNotificationPosted")
            .runAfter { param ->
                val sbn = param.args[0] as StatusBarNotification
                mOnGoingActionProgressController?.onNotificationPosted(sbn)
            }

        notificationListenerClass
            .hookMethod("onNotificationRemoved")
            .runAfter { param ->
                val sbn = param.args[0] as StatusBarNotification
                mOnGoingActionProgressController?.onNotificationRemoved(sbn)
            }

        val keyguardStateControllerImplClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.KeyguardStateControllerImpl")

        keyguardStateControllerImplClass
            .hookMethod("notifyKeyguardState")
            .runAfter { param ->
                val showing = param.args[0] as Boolean
                mOnGoingActionProgressController?.setForceHidden(showing)
            }
    }
}