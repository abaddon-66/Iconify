package com.drdisagree.iconify.xposed.modules.statusbar

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.STATUSBAR_LOGO_POSITION
import com.drdisagree.iconify.data.common.Preferences.STATUSBAR_LOGO_STYLE
import com.drdisagree.iconify.data.common.Preferences.STATUSBAR_LOGO_SWITCH
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.reAddView
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethodSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callStaticMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.views.logoview.LogoImageView
import com.drdisagree.iconify.xposed.modules.extras.views.logoview.LogoImageViewRight
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class StatusbarLogo(context: Context) : ModPack(context) {

    private var showLogo = false
    private var logoPosition = 0
    private var logoStyle = 0
    private var logoImageView: LogoImageView? = null
    private var logoImageViewRight: LogoImageViewRight? = null
    private var darkIconDispatcherClass: Class<*>? = null

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            showLogo = getBoolean(STATUSBAR_LOGO_SWITCH, false)
            logoPosition = getString(STATUSBAR_LOGO_POSITION, "0")!!.toInt()
            logoStyle = getString(STATUSBAR_LOGO_STYLE, "0")!!.toInt()
        }

        when (key.firstOrNull()) {
            STATUSBAR_LOGO_SWITCH,
            STATUSBAR_LOGO_POSITION,
            STATUSBAR_LOGO_STYLE -> {
                logoImageView?.updateSettings(showLogo, logoPosition, logoStyle)
                logoImageViewRight?.updateSettings(showLogo, logoPosition, logoStyle)
            }
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        darkIconDispatcherClass = findClass("$SYSTEMUI_PACKAGE.plugins.DarkIconDispatcher")

        val phoneStatusBarViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.PhoneStatusBarView")

        phoneStatusBarViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                val phoneStatusBarView = param.thisObject as ViewGroup

                val operatorName = phoneStatusBarView.findViewById<View>(
                    mContext.resources.getIdentifier(
                        "operator_name_stub",
                        "id",
                        mContext.packageName
                    )
                )
                val startSideExceptHeadsUp = operatorName.parent as ViewGroup
                val logoIndex = startSideExceptHeadsUp.indexOfChild(operatorName) + 1

                val systemIcons = phoneStatusBarView.findViewById<ViewGroup>(
                    mContext.resources.getIdentifier(
                        "system_icons",
                        "id",
                        mContext.packageName
                    )
                )

                if (logoImageView == null) {
                    logoImageView = LogoImageView(mContext).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        }
                        setPaddingRelative(
                            mContext.resources.getDimensionPixelSize(
                                mContext.resources.getIdentifier(
                                    "status_bar_left_clock_starting_padding",
                                    "dimen",
                                    mContext.packageName
                                )
                            ),
                            0,
                            mContext.resources.getDimensionPixelSize(
                                mContext.resources.getIdentifier(
                                    "status_bar_left_clock_end_padding",
                                    "dimen",
                                    mContext.packageName
                                )
                            ),
                            0
                        )
                        scaleType = ImageView.ScaleType.CENTER
                        visibility = View.GONE
                    }
                }

                if (logoImageViewRight == null) {
                    logoImageViewRight = LogoImageViewRight(mContext).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL or Gravity.END
                        }
                        setPaddingRelative(
                            mContext.resources.getDimensionPixelSize(
                                mContext.resources.getIdentifier(
                                    "status_bar_clock_starting_padding",
                                    "dimen",
                                    mContext.packageName
                                )
                            ),
                            0,
                            mContext.resources.getDimensionPixelSize(
                                mContext.resources.getIdentifier(
                                    "status_bar_clock_end_padding",
                                    "dimen",
                                    mContext.packageName
                                )
                            ),
                            0
                        )
                        scaleType = ImageView.ScaleType.CENTER
                        visibility = View.GONE
                    }
                }

                logoImageView!!.updateSettings(showLogo, logoPosition, logoStyle)
                logoImageViewRight!!.updateSettings(showLogo, logoPosition, logoStyle)

                startSideExceptHeadsUp.reAddView(logoImageView, logoIndex)
                systemIcons.reAddView(logoImageViewRight)
            }

        val headsUpAppearanceControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.HeadsUpAppearanceController")

        headsUpAppearanceControllerClass
            .hookMethod("updateTopEntry")
            .runBefore { param ->
                var newEntry: Any? = null
                val shouldBeVisible = (param.thisObject.callMethodSilently("shouldBeVisible")
                    ?: param.thisObject.callMethod("shouldBeVisible$1")) as Boolean

                if (shouldBeVisible) {
                    val mHeadsUpManager = param.thisObject.getField("mHeadsUpManager")

                    newEntry = try {
                        mHeadsUpManager.callMethod("getTopEntry")
                    } catch (_: Throwable) {
                        mHeadsUpManager.callMethod("getTopHeadsUpEntry")?.getFieldSilently("mEntry")
                    }
                }

                val headsUpStatusBarView = param.thisObject.getField("mView")
                val previousEntry = try {
                    headsUpStatusBarView.callMethod("getShowingEntry")
                } catch (_: Throwable) {
                    headsUpStatusBarView.getFieldSilently("mShowingEntry")
                }

                if (previousEntry != newEntry) {
                    if (newEntry == null) {
                        logoImageView?.alpha = 1f
                        logoImageViewRight?.alpha = 1f
                    } else if (previousEntry == null) {
                        logoImageView?.alpha = 0f
                        logoImageViewRight?.alpha = 0f
                    }
                }
            }

        val clockClass = findClass("$SYSTEMUI_PACKAGE.statusbar.policy.Clock")

        clockClass
            .hookMethod("onDarkChanged")
            .runAfter { param ->
                if (logoImageView == null || logoImageViewRight == null) return@runAfter

                val areas = param.args[0]
                val tint = param.args[2]

                val mTintColor = darkIconDispatcherClass.callStaticMethod(
                    "getTint",
                    areas,
                    logoImageView,
                    tint
                ) as Int

                logoImageView!!.mTintColor = mTintColor
                logoImageViewRight!!.mTintColor = mTintColor

                if (!showLogo) return@runAfter

                if (logoImageView!!.isLogoVisible) {
                    logoImageView!!.updateLogo()
                }
                if (logoImageViewRight!!.isLogoVisible) {
                    logoImageViewRight!!.updateLogo()
                }
            }
    }
}