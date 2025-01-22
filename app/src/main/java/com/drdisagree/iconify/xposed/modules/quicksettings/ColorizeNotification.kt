package com.drdisagree.iconify.xposed.modules.quicksettings

import android.annotation.SuppressLint
import android.app.Notification
import android.app.WallpaperColors
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.drdisagree.iconify.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.COLORED_NOTIFICATION_ALTERNATIVE_SWITCH
import com.drdisagree.iconify.common.Preferences.COLORED_NOTIFICATION_ICON_SWITCH
import com.drdisagree.iconify.common.Preferences.COLORED_NOTIFICATION_VIEW_SWITCH
import com.drdisagree.iconify.utils.color.monet.quantize.QuantizerCelebi
import com.drdisagree.iconify.utils.color.monet.score.Score
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getAnyField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getExtraField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getExtraFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.isMethodAvailable
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setExtraField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setFieldSilently
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
@Suppress("deprecation", "UNCHECKED_CAST")
class ColorizeNotification(context: Context) : ModPack(context) {

    private var coloredNotificationIcon = false
    private var coloredNotificationView = false
    private var coloredNotificationAlternativeColor = false
    private var titleResId = 0
    private var subTextResId = 0
    private var schemeStyle: Any = "TONAL_SPOT"

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            coloredNotificationIcon = getBoolean(COLORED_NOTIFICATION_ICON_SWITCH, false)
            coloredNotificationView = getBoolean(COLORED_NOTIFICATION_VIEW_SWITCH, false)
            coloredNotificationAlternativeColor =
                getBoolean(COLORED_NOTIFICATION_ALTERNATIVE_SWITCH, false)
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
        val notificationHeaderViewWrapperClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper")

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

                if (mN.getExtraFieldSilently("mPrimaryTextColor") != null) {
                    val builder: Any = param.thisObject

                    val mParams = builder.getField("mParams")
                    builder.callMethod("getColors", mParams)

                    val mColors = builder.getField("mColors")

                    listOf(
                        "mProtectionColor",
                        "mPrimaryTextColor",
                        "mSecondaryTextColor",
                        "mPrimaryAccentColor",
                        "mSecondaryAccentColor",
                        "mTertiaryAccentColor",
                        "mOnTertiaryAccentTextColor",
                        "mTertiaryFixedDimAccentColor",
                        "mOnTertiaryFixedAccentTextColor"
                    ).forEach { fieldName ->
                        mColors.setField(
                            fieldName,
                            mN.getExtraField(fieldName)
                        )
                    }
                }
            }

        expandableNotificationRowClass
            .hookMethod("onNotificationUpdated")
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                val mEntry = param.thisObject.getFieldSilently("mEntry") ?: return@runAfter

                val mSbn = mEntry.getField("mSbn")
                val notification = mSbn.callMethod("getNotification") as Notification

                val overflowColor = notification.getExtraFieldSilently("mSecondaryTextColor")
                if (overflowColor != null) {
                    param.thisObject.setField("mNotificationColor", overflowColor)
                }

                val mNotifyBackgroundColor =
                    notification.getExtraFieldSilently("mNotifyBackgroundColor")
                if (mNotifyBackgroundColor != null) {
                    var bgColor = mNotifyBackgroundColor as Int
                    val mCurrentBackgroundTint = try {
                        param.thisObject.callMethod("getCurrentBackgroundTint")
                    } catch (ignore: Throwable) {
                        param.thisObject.getField("mCurrentBackgroundTint")
                    } as Int

                    if (mCurrentBackgroundTint != bgColor) {
                        bgColor = Color.argb(
                            255,
                            Color.red(bgColor),
                            Color.green(bgColor),
                            Color.blue(bgColor)
                        )
                        param.thisObject.callMethod("setBackgroundTintColor", bgColor)

                        param.thisObject.setFieldSilently("mCurrentBackgroundTint", bgColor)

                        val notificationBackgroundView = param.thisObject.getField(
                            "mBackgroundNormal"
                        ) as View

                        val bgDrawable = notificationBackgroundView.getFieldSilently(
                            "mBackground"
                        ) as? Drawable
                        if (bgDrawable != null) {
                            DrawableCompat.setTint(bgDrawable, bgColor)
                        }

                        notificationBackgroundView.setFieldSilently("mTintColor", bgColor)

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

                param.result = param.thisObject.getField("mBackgroundColor")
            }

        notificationContentViewClass
            .hookMethod("updateAllSingleLineViews")
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                if (param.thisObject == null) return@runAfter

                val mEntry = param.thisObject.getFieldSilently("mNotificationEntry")
                    ?: return@runAfter
                val singleLineView = param.thisObject.getFieldSilently("mSingleLineView")
                    ?: return@runAfter

                val mSbn = mEntry.getField("mSbn")
                val mN = mSbn.callMethod("getNotification") as Notification

                if (mN.getExtraFieldSilently("mSecondaryTextColor") != null) {
                    val hybridNotificationView = singleLineView as LinearLayout

                    val mTitleView = hybridNotificationView.getField("mTitleView") as TextView
                    mTitleView.setTextColor(mN.getExtraField("mPrimaryTextColor") as Int)

                    val mTextView = hybridNotificationView.getField("mTextView") as TextView
                    mTextView.setTextColor(mN.getExtraField("mSecondaryTextColor") as Int)
                }
            }

        fun initializeNotificationColors(
            mN: Notification,
            builder: Notification.Builder,
            packageContext: Context = mContext
        ) {
            if (callMethod(mN, "isColorized") as Boolean) return
            if (callMethod(mN, "isMediaNotification") as Boolean) return

            val applicationInfo =
                mN.extras.getParcelable<ApplicationInfo>("android.appInfo")
                    ?: return
            val pkgName = applicationInfo.packageName

            if (pkgName == FRAMEWORK_PACKAGE) return

            val fallbackColor = mContext.resources.getColor(
                mContext.resources.getIdentifier(
                    "android:color/system_accent1_600",
                    "color",
                    mContext.packageName
                ), mContext.theme
            )

            val packageManager = packageContext.packageManager
            val notifyIcon = try {
                packageManager.getApplicationIcon(pkgName)
            } catch (ignored: Throwable) {
                ColorDrawable(fallbackColor)
            }

            builder.callMethod("makeNotificationGroupHeader")

            val wallpaperColors: WallpaperColors?
            var primaryColor: Int?

            if (!coloredNotificationAlternativeColor) { // Use WallpaperColors to get the primary color
                wallpaperColors = WallpaperColors.fromDrawable(notifyIcon)
                primaryColor = wallpaperColors.primaryColor.toArgb()
            } else { // Use Monet Score and Quantizer to get the primary color
                val bitmap = notifyIcon.drawableToBitmap()
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                primaryColor = Score.score(QuantizerCelebi.quantize(pixels, 25)).firstOrNull()
                    ?: fallbackColor
                wallpaperColors = WallpaperColors.fromDrawable(ColorDrawable(primaryColor))
            }

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

            val paletteAccent1 = colorScheme.getAnyField("accent1", "mAccent1")
            val paletteAccent2 = colorScheme.getAnyField("accent2", "mAccent2")
            val paletteAccent3 = colorScheme.getAnyField("accent3", "mAccent3")
            val paletteNeutral1 = colorScheme.getAnyField("neutral1", "mNeutral1")
            val paletteNeutral2 = colorScheme.getAnyField("neutral2", "mNeutral2")

            val accent1 = paletteAccent1.getField("allShades") as List<Int>
            val accent2 = paletteAccent2.getField("allShades") as List<Int>
            val accent3 = paletteAccent3.getField("allShades") as List<Int>
            val neutral1 = paletteNeutral1.getField("allShades") as List<Int>
            val neutral2 = paletteNeutral2.getField("allShades") as List<Int>

            val bgColor = accent1[if (darkTheme) 10 else 2]
            val bgColorInverse = (if (darkTheme) 0xFFFFFFFF else 0xFF000000).toInt()
            val mParams = builder.getField("mParams")

            mParams.callMethod("reset")
            builder.callMethod("getColors", mParams)

            val mColors = builder.getField("mColors")
            val mProtectionColor = ColorUtils.blendARGB(bgColor, bgColorInverse, 0.15f)
            val mPrimaryTextColor = neutral1[if (darkTheme) 1 else 11]
            val mSecondaryTextColor = neutral2[if (darkTheme) 2 else 10]
            val mPrimaryAccentColor = accent1[if (darkTheme) 3 else 8]
            val mSecondaryAccentColor = accent2[if (darkTheme) 3 else 8]
            val mTertiaryAccentColor = accent3[if (darkTheme) 3 else 8]
            val mOnTertiaryAccentTextColor = accent3[if (darkTheme) 10 else 1]
            val mTertiaryFixedDimAccentColor = accent3[4]
            val mOnTertiaryFixedAccentTextColor = accent3[10]

            mapOf(
                "mBackgroundColor" to bgColor,
                "mProtectionColor" to mProtectionColor,
                "mPrimaryTextColor" to mPrimaryTextColor,
                "mSecondaryTextColor" to mSecondaryTextColor,
                "mPrimaryAccentColor" to mPrimaryAccentColor,
                "mSecondaryAccentColor" to mSecondaryAccentColor,
                "mTertiaryAccentColor" to mTertiaryAccentColor,
                "mOnTertiaryAccentTextColor" to mOnTertiaryAccentTextColor,
                "mTertiaryFixedDimAccentColor" to mTertiaryFixedDimAccentColor,
                "mOnTertiaryFixedAccentTextColor" to mOnTertiaryFixedAccentTextColor
            ).forEach { (fieldName, value) ->
                mColors.setField(fieldName, value)

                if (fieldName == "mBackgroundColor") {
                    mN.setExtraField("mNotifyBackgroundColor", value)
                } else {
                    mN.setExtraField(fieldName, value)
                }
            }
        }

        fun setNotificationTextColor(
            notification: Notification,
            inflationProgress: Any,
            packageContext: Context = mContext
        ) {
            if (titleResId == 0) {
                titleResId = packageContext.resources.getIdentifier(
                    "notification_title",
                    "id",
                    SYSTEMUI_PACKAGE
                )
                subTextResId = packageContext.resources.getIdentifier(
                    "notification_text",
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
                val baseContent = inflationProgress.getFieldSilently(contentType) as? RemoteViews

                if (baseContent != null && titleResId != 0 &&
                    notification.getExtraFieldSilently("mPrimaryTextColor") != null
                ) {
                    baseContent.setTextColor(
                        titleResId,
                        notification.getExtraField("mPrimaryTextColor") as Int
                    )
                    baseContent.setTextColor(
                        subTextResId,
                        notification.getExtraField("mSecondaryTextColor") as Int
                    )
                }
            }
        }

        if (isMethodAvailable(notificationContentInflaterClass, "createRemoteViews")) {
            notificationContentInflaterClass
                .hookMethod("createRemoteViews")
                .runBefore { param ->
                    if (!coloredNotificationView) return@runBefore

                    val builder = param.args[1] as Notification.Builder
                    val mN = builder.getField("mN") as Notification
                    val mContext = param.args[5] as Context

                    initializeNotificationColors(mN, builder, mContext)
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

                    setNotificationTextColor(notification, inflationProgress, mContext)
                }
        } else {
            findClass("android.app.Notification\$Builder")
                .hookMethod("recoverBuilder")
                .runAfter { param ->
                    if (!coloredNotificationView) return@runAfter

                    val builder = param.result as Notification.Builder
                    val mN = builder.getField("mN") as Notification

                    initializeNotificationColors(mN, builder)
                }
        }

        notificationHeaderViewWrapperClass
            .hookMethod("onContentUpdated")
            .runAfter { param ->
                if (!coloredNotificationIcon) return@runAfter

                val row = param.args[0]
                val notifyEntries = try {
                    row.callMethod("getEntry")
                } catch (ignored: Throwable) {
                    row.getField("mEntry")
                }
                val notifySbn = try {
                    notifyEntries.callMethod("getSbn")
                } catch (ignored: Throwable) {
                    notifyEntries.getField("mSbn")
                }
                val notification = notifySbn.callMethod("getNotification") as Notification
                val pkgName = notifySbn.callMethod("getPackageName") as? String ?: return@runAfter
                val appIcon: Drawable = try {
                    mContext.packageManager.getApplicationIcon(pkgName)
                } catch (ignored: Throwable) {
                    return@runAfter
                }
                val mIcon = param.thisObject.getFieldSilently("mIcon") as ImageView
                val mWorkProfileImage =
                    param.thisObject.getFieldSilently("mWorkProfileImage") as? ImageView
                val mImageTransformStateIconTag = mContext.resources.getIdentifier(
                    "image_icon_tag",
                    "id",
                    SYSTEMUI_PACKAGE
                )

                if (mWorkProfileImage != null) {
                    mIcon.setImageDrawable(appIcon);
                    mWorkProfileImage.setImageIcon(notification.smallIcon);
                    // The work profile image is always the same
                    // Lets just set the icon tag for it not to animate
                    mWorkProfileImage.setTag(
                        mImageTransformStateIconTag,
                        notification.smallIcon
                    )
                }
                mIcon.setTag(mImageTransformStateIconTag, notification.smallIcon);
            }
    }

    private fun Drawable.drawableToBitmap(): Bitmap {
        return if (this is BitmapDrawable && bitmap != null) {
            bitmap
        } else {
            val bitmap = Bitmap.createBitmap(
                intrinsicWidth.coerceAtLeast(1),
                intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        }
    }
}