package com.drdisagree.iconify.xposed.modules.quicksettings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.widget.LinearLayout
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.BLUR_RADIUS_VALUE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_SHADE_SWITCH
import com.drdisagree.iconify.common.Preferences.NOTIF_TRANSPARENCY_SWITCH
import com.drdisagree.iconify.common.Preferences.QSALPHA_LEVEL
import com.drdisagree.iconify.common.Preferences.QSPANEL_BLUR_SWITCH
import com.drdisagree.iconify.common.Preferences.QS_TRANSPARENCY_SWITCH
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedHelpers.findField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class QSTransparency(context: Context) : ModPack(context) {

    private val keyguardAlpha = 0.85f
    private var qsTransparencyActive = false
    private var onlyNotifTransparencyActive = false
    private var keepLockScreenShade = false
    private var alpha = 60f
    private var blurEnabled = false
    private var blurRadius = 23
    private var quickSettingsController: Any? = null

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            qsTransparencyActive = getBoolean(QS_TRANSPARENCY_SWITCH, false)
            onlyNotifTransparencyActive = getBoolean(NOTIF_TRANSPARENCY_SWITCH, false)
            keepLockScreenShade = getBoolean(LOCKSCREEN_SHADE_SWITCH, false)
            alpha = (getSliderInt(QSALPHA_LEVEL, 60).toFloat() / 100.0).toFloat()
            blurEnabled = getBoolean(QSPANEL_BLUR_SWITCH, false)
            blurRadius = getSliderInt(BLUR_RADIUS_VALUE, 23)
        }

        if (key.isNotEmpty() &&
            (key[0] == QS_TRANSPARENCY_SWITCH ||
                    key[0] == NOTIF_TRANSPARENCY_SWITCH ||
                    key[0] == QSALPHA_LEVEL)
        ) {
            updateQsScrimRadius()
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        setQsTransparency()
        setBlurRadius()
    }

    private fun setQsTransparency() {
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")

        scrimControllerClass
            .hookMethod("updateScrimColor")
            .runBefore { param ->
                if (!qsTransparencyActive && !onlyNotifTransparencyActive) return@runBefore

                val alphaIndex = if (param.args[2] is Float) 2 else 1
                val scrimState = param.thisObject.getField("mState").toString()

                if (scrimState == "KEYGUARD") {
                    if (!keepLockScreenShade) {
                        param.args[alphaIndex] = 0.0f
                    }
                } else if (scrimState.contains("BOUNCER")) {
                    param.args[alphaIndex] = param.args[alphaIndex] as Float * keyguardAlpha
                } else {
                    val scrimName = when {
                        findField(
                            scrimControllerClass,
                            "mScrimInFront"
                        )[param.thisObject] == param.args[0] -> {
                            "front_scrim"
                        }

                        findField(
                            scrimControllerClass,
                            "mScrimBehind"
                        )[param.thisObject] == param.args[0] -> {
                            "behind_scrim"
                        }

                        findField(
                            scrimControllerClass,
                            "mNotificationsScrim"
                        )[param.thisObject] == param.args[0] -> {
                            "notifications_scrim"
                        }

                        else -> {
                            "unknown_scrim"
                        }
                    }

                    when (scrimName) {
                        "behind_scrim" -> {
                            if (!onlyNotifTransparencyActive) {
                                param.args[alphaIndex] = param.args[alphaIndex] as Float * alpha
                            }
                        }

                        "notifications_scrim" -> {
                            param.args[alphaIndex] = param.args[alphaIndex] as Float * alpha
                        }

                        else -> {}
                    }
                }
            }

        // Compose implementation of QS Footer actions
        val footerActionsViewBinderClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.FooterActionsViewBinder",
            suppressError = true
        )

        footerActionsViewBinderClass
            .hookMethod("bind")
            .suppressError()
            .runAfter { param ->
                if (!qsTransparencyActive && !onlyNotifTransparencyActive) return@runAfter

                val view = param.args[0] as LinearLayout
                view.setBackgroundColor(Color.TRANSPARENT)
                view.elevation = 0f
            }

        val footerActionsViewModelClass =
            findClass("$SYSTEMUI_PACKAGE.qs.footer.ui.viewmodel.FooterActionsViewModel")

        footerActionsViewModelClass
            .hookConstructor()
            .runAfter { param ->
                if (!qsTransparencyActive && !onlyNotifTransparencyActive) return@runAfter

                val stateFlowImplClass = findClass("kotlinx.coroutines.flow.StateFlowImpl")!!
                val readonlyStateFlowClass =
                    findClass("kotlinx.coroutines.flow.ReadonlyStateFlow")!!

                try {
                    val zeroAlphaFlow = stateFlowImplClass
                        .getConstructor(Any::class.java)
                        .newInstance(0f)

                    val readonlyStateFlowInstance = try {
                        readonlyStateFlowClass.constructors[0].newInstance(zeroAlphaFlow)
                    } catch (ignored: Throwable) {
                        readonlyStateFlowClass.constructors[0].newInstance(zeroAlphaFlow, null)
                    }

                    param.thisObject.setField(
                        "backgroundAlpha",
                        readonlyStateFlowInstance
                    )
                } catch (throwable: Throwable) {
                    log(this@QSTransparency, throwable)
                }
            }

        val quickSettingsControllerClass =
            findClass("$SYSTEMUI_PACKAGE.shade.QuickSettingsController")

        quickSettingsControllerClass
            .hookConstructor()
            .runAfter { param -> quickSettingsController = param.thisObject }

        Resources::class.java
            .hookMethod("getDimensionPixelSize")
            .suppressError()
            .runBefore { param ->
                if (!qsTransparencyActive || onlyNotifTransparencyActive || (alpha * 100).toInt() != 0) return@runBefore

                try {
                    val resId = mContext.resources.getIdentifier(
                        "notification_scrim_corner_radius",
                        "dimen",
                        mContext.packageName
                    )

                    if (param.args[0] == resId) {
                        param.result = 0
                    }
                } catch (ignored: Throwable) {
                }
            }
    }

    private fun updateQsScrimRadius() {
        if (quickSettingsController == null) return

        quickSettingsController.setField(
            "mScrimCornerRadius",
            mContext.resources.getDimensionPixelSize(
                mContext.resources.getIdentifier(
                    "notification_scrim_corner_radius",
                    "dimen",
                    mContext.packageName
                )
            )
        )

        quickSettingsController.callMethod("setClippingBounds")
    }

    @SuppressLint("DiscouragedApi")
    private fun setBlurRadius() {
        Resources::class.java
            .hookMethod("getDimensionPixelSize")
            .suppressError()
            .runBefore { param ->
                if (!blurEnabled) return@runBefore

                try {
                    val resId = mContext.resources.getIdentifier(
                        "max_window_blur_radius",
                        "dimen",
                        mContext.packageName
                    )

                    if (param.args[0] == resId) {
                        param.result = blurRadius
                    }
                } catch (throwable: Throwable) {
                    log(this@QSTransparency, throwable)
                }
            }
    }
}
