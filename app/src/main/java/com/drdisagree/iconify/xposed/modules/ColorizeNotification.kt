package com.drdisagree.iconify.xposed.modules

import android.app.Notification
import android.app.WallpaperColors
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.drdisagree.iconify.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.COLORED_NOTIFICATION_VIEW_SWITCH
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam


class ColorizeNotification(context: Context) : ModPack(context) {

    private var coloredNotificationView = false
    private var titleResId = 0
    private var subTextResId = 0
    private var schemeStyle: Any = "TONAL_SPOT"

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            coloredNotificationView = getBoolean(COLORED_NOTIFICATION_VIEW_SWITCH, false)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val colorSchemeClass = findClass("$SYSTEMUI_PACKAGE.monet.ColorScheme")
        val monetStyleClass = findClass("$SYSTEMUI_PACKAGE.monet.Style")!!
        val expandableNotificationRowClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.ExpandableNotificationRow")
        val notificationBackgroundViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.NotificationBackgroundView")
        val notificationViewWrapperClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.wrapper.NotificationViewWrapper")
        val notificationContentViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.NotificationContentView")
        val notificationContentInflaterClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.NotificationContentInflater")

        val styles: Array<out Any> = monetStyleClass.getEnumConstants()!!
        for (style in styles) {
            if (style.toString().contains("CONTENT")) {
                schemeStyle = style
                break
            }
        }

        findClass("android.app.Notification\$Builder")
            .hookConstructor()
            .parameters(
                Context::class.java,
                Notification::class.java
            )
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                val mN = param.args[1] as? Notification ?: return@runAfter

                if (getAdditionalInstanceField(mN, "mPrimaryTextColor") != null) {
                    val builder: Any = param.thisObject

                    val mParams = getObjectField(builder, "mParams")
                    callMethod(builder, "getColors", mParams)

                    val mColors = getObjectField(builder, "mColors")
                    setObjectField(
                        mColors,
                        "mProtectionColor",
                        getAdditionalInstanceField(mN, "mProtectionColor")
                    )
                    setObjectField(
                        mColors,
                        "mPrimaryTextColor",
                        getAdditionalInstanceField(mN, "mPrimaryTextColor")
                    )
                    setObjectField(
                        mColors,
                        "mSecondaryTextColor",
                        getAdditionalInstanceField(mN, "mSecondaryTextColor")
                    )
                }
            }

        expandableNotificationRowClass
            .hookMethod("onNotificationUpdated")
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                val mEntry = getObjectField(param.thisObject, "mEntry") ?: return@runAfter

                val mSbn = getObjectField(mEntry, "mSbn")
                val notification = callMethod(mSbn, "getNotification") as Notification

                val overflowColor = getAdditionalInstanceField(notification, "mSecondaryTextColor")
                if (overflowColor != null) {
                    setObjectField(param.thisObject, "mNotificationColor", overflowColor)
                }

                val mNotifyBackgroundColor = getAdditionalInstanceField(
                    notification,
                    "mNotifyBackgroundColor"
                )
                if (mNotifyBackgroundColor != null) {
                    var bgColor = mNotifyBackgroundColor as Int
                    val mCurrentBackgroundTint = callMethod(
                        param.thisObject,
                        "getCurrentBackgroundTint"
                    ) as Int
                    if (mCurrentBackgroundTint != bgColor) {
                        bgColor = Color.argb(
                            255,
                            Color.red(bgColor),
                            Color.green(bgColor),
                            Color.blue(bgColor)
                        )
                        callMethod(param.thisObject, "setBackgroundTintColor", bgColor)

                        val notificationBackgroundView = getObjectField(
                            param.thisObject,
                            "mBackgroundNormal"
                        ) as View

                        try {
                            val bgDrawable = getObjectField(
                                notificationBackgroundView,
                                "mBackground"
                            ) as Drawable
                            DrawableCompat.setTint(bgDrawable, bgColor)
                        } catch (ignored: Throwable) {
                        }

                        try {
                            setObjectField(notificationBackgroundView, "mTintColor", bgColor)
                        } catch (ignored: Throwable) {
                        }

                        Handler(Looper.getMainLooper()).post {
                            notificationBackgroundView.invalidate()
                        }
                    }
                }
            }

        notificationBackgroundViewClass
            .hookMethod("setTint")
            .parameters(Int::class.javaPrimitiveType)
            .runBefore { param ->
                if (!coloredNotificationView) return@runBefore

                if (param.args[0] as Int == 0) {
                    param.result = null
                }
            }

        notificationViewWrapperClass
            .hookMethod("getCustomBackgroundColor")
            .runBefore { param ->
                if (!coloredNotificationView) return@runBefore

                param.result = getObjectField(param.thisObject, "mBackgroundColor")
            }

        notificationContentViewClass
            .hookMethod("updateAllSingleLineViews")
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                val mEntry =
                    getObjectField(param.thisObject, "mNotificationEntry") ?: return@runAfter
                val singleLineView =
                    getObjectField(param.thisObject, "mSingleLineView") ?: return@runAfter

                val mSbn = getObjectField(mEntry, "mSbn")
                val mN = callMethod(mSbn, "getNotification") as Notification

                if (getAdditionalInstanceField(mN, "mSecondaryTextColor") != null) {
                    val hybridNotificationView = singleLineView as LinearLayout

                    val mTitleView =
                        getObjectField(hybridNotificationView, "mTitleView") as TextView
                    mTitleView.setTextColor(
                        getAdditionalInstanceField(
                            mN,
                            "mPrimaryTextColor"
                        ) as Int
                    )

                    val mTextView = getObjectField(hybridNotificationView, "mTextView") as TextView
                    mTextView.setTextColor(
                        getAdditionalInstanceField(
                            mN,
                            "mSecondaryTextColor"
                        ) as Int
                    )
                }
            }

        @Suppress("deprecation", "DiscouragedApi", "UNCHECKED_CAST")
        notificationContentInflaterClass
            .hookMethod("createRemoteViews")
            .runBefore { param ->
                if (!coloredNotificationView) return@runBefore

                val builder = param.args[1] as Notification.Builder
                val mN = getObjectField(builder, "mN") as Notification

                if (callMethod(mN, "isColorized") as Boolean) return@runBefore
                if (callMethod(mN, "isMediaNotification") as Boolean) return@runBefore

                val applicationInfo = mN.extras.getParcelable<ApplicationInfo>("android.appInfo")
                    ?: return@runBefore
                val packageContext = param.args[5] as Context
                val pkgName = applicationInfo.packageName

                if (pkgName == FRAMEWORK_PACKAGE) return@runBefore

                val packageManager = packageContext.packageManager
                val notifyIcon = try {
                    packageManager.getApplicationIcon(pkgName)
                } catch (ignored: Throwable) {
                    ColorDrawable(
                        mContext.resources.getColor(
                            mContext.resources.getIdentifier(
                                "android:color/system_accent1_600",
                                "color",
                                mContext.packageName
                            ), mContext.theme
                        )
                    )
                }

                callMethod(builder, "makeNotificationGroupHeader")

                val wallpaperColors = WallpaperColors.fromDrawable(notifyIcon)
                var primaryColor = wallpaperColors.primaryColor.toArgb()

                if (Color.luminance(primaryColor) > 0.9) {
                    wallpaperColors.secondaryColor?.let {
                        primaryColor = it.toArgb()
                    }
                }

                val darkTheme = packageContext.resources.configuration.isNightModeActive
                val colorScheme = try {
                    newInstance(
                        colorSchemeClass,
                        primaryColor,
                        darkTheme,
                        schemeStyle
                    )
                } catch (ignored: NoSuchMethodError) {
                    newInstance(
                        colorSchemeClass,
                        wallpaperColors,
                        darkTheme,
                        schemeStyle
                    )
                }

                val paletteAccent1 = getObjectField(colorScheme, "accent1")
                val paletteNeutral1 = getObjectField(colorScheme, "neutral1")
                val paletteNeutral2 = getObjectField(colorScheme, "neutral2")

                val accent1 = getObjectField(paletteAccent1, "allShades") as List<Int>
                val neutral1 = getObjectField(paletteNeutral1, "allShades") as List<Int>
                val neutral2 = getObjectField(paletteNeutral2, "allShades") as List<Int>

                val bgColor = accent1[if (darkTheme) 9 else 2]
                val mParams = getObjectField(builder, "mParams")

                callMethod(mParams, "reset")
                callMethod(builder, "getColors", mParams)

                val mColors = getObjectField(builder, "mColors")
                val mProtectionColor = ColorUtils.blendARGB(neutral1[1], bgColor, 0.7f)
                val mPrimaryTextColor = neutral1[if (darkTheme) 1 else 10]
                val mSecondaryTextColor = neutral2[if (darkTheme) 3 else 8]

                setObjectField(mColors, "mProtectionColor", mProtectionColor)
                setAdditionalInstanceField(mN, "mProtectionColor", mProtectionColor)
                setObjectField(mColors, "mPrimaryTextColor", mPrimaryTextColor)
                setAdditionalInstanceField(mN, "mPrimaryTextColor", mPrimaryTextColor)
                setObjectField(mColors, "mSecondaryTextColor", mSecondaryTextColor)
                setAdditionalInstanceField(mN, "mSecondaryTextColor", mSecondaryTextColor)
                setAdditionalInstanceField(mN, "mNotifyBackgroundColor", bgColor)
            }
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                val builder = param.args[1] as Notification.Builder
                val notification: Notification = builder.notification

                if (callMethod(
                        notification,
                        "isMediaNotification"
                    ) as Boolean
                ) return@runAfter

                val inflationProgress: Any = param.result
                val mContext = param.args[5] as Context

                if (titleResId == 0) {
                    titleResId = mContext.resources.getIdentifier(
                        "title",
                        "id",
                        SYSTEMUI_PACKAGE
                    )
                    subTextResId = mContext.resources.getIdentifier(
                        "text",
                        "id",
                        SYSTEMUI_PACKAGE
                    )
                }

                listOf(
                    "newPublicView",
                    "newContentView",
                    "newExpandedView",
                    "newHeadsUpView"
                ).forEach { contentType ->
                    val baseContent = getObjectField(inflationProgress, contentType) as? RemoteViews

                    if (baseContent != null &&
                        getAdditionalInstanceField(notification, "mPrimaryTextColor") != null
                    ) {
                        baseContent.setTextColor(
                            titleResId,
                            getAdditionalInstanceField(
                                notification,
                                "mPrimaryTextColor"
                            ) as Int
                        )
                        baseContent.setTextColor(
                            subTextResId,
                            getAdditionalInstanceField(
                                notification,
                                "mSecondaryTextColor"
                            ) as Int
                        )
                    }
                }
            }
    }
}