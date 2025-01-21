package com.drdisagree.iconify.xposed.modules.lockscreen.lockscreenweather

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.drdisagree.iconify.common.Const.ACTION_LS_CLOCK_INFLATED
import com.drdisagree.iconify.common.Const.ACTION_WEATHER_INFLATED
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_CLOCK_TAG
import com.drdisagree.iconify.common.Preferences.LSCLOCK_SWITCH
import com.drdisagree.iconify.common.Preferences.WEATHER_CENTER_VIEW
import com.drdisagree.iconify.common.Preferences.WEATHER_CUSTOM_MARGINS_BOTTOM
import com.drdisagree.iconify.common.Preferences.WEATHER_CUSTOM_MARGINS_SIDE
import com.drdisagree.iconify.common.Preferences.WEATHER_CUSTOM_MARGINS_TOP
import com.drdisagree.iconify.common.Preferences.WEATHER_ICON_SIZE
import com.drdisagree.iconify.common.Preferences.WEATHER_SHOW_CONDITION
import com.drdisagree.iconify.common.Preferences.WEATHER_SHOW_HUMIDITY
import com.drdisagree.iconify.common.Preferences.WEATHER_SHOW_LOCATION
import com.drdisagree.iconify.common.Preferences.WEATHER_SHOW_WIND
import com.drdisagree.iconify.common.Preferences.WEATHER_STYLE
import com.drdisagree.iconify.common.Preferences.WEATHER_SWITCH
import com.drdisagree.iconify.common.Preferences.WEATHER_TEXT_COLOR
import com.drdisagree.iconify.common.Preferences.WEATHER_TEXT_COLOR_SWITCH
import com.drdisagree.iconify.common.Preferences.WEATHER_TEXT_SIZE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.applyTo
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.clear
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.clone
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.connect
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.constraintSetInstance
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.assignIdsToViews
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.setMargins
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.views.CurrentWeatherView
import com.drdisagree.iconify.xposed.modules.lockscreen.Lockscreen.Companion.isComposeLockscreen
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LockscreenWeatherA15(context: Context) : ModPack(context) {

    private var customLockscreenClock = false
    private var weatherEnabled = false
    private var weatherShowLocation = true
    private var weatherShowCondition = true
    private var weatherShowHumidity = false
    private var weatherShowWind = false
    private var weatherCustomColor = false
    private var weatherColor = Color.WHITE
    private var weatherTextSize: Int = 16
    private var weatherImageSize: Int = 18
    private var mSideMargin: Int = 0
    private var mTopMargin: Int = 0
    private var mBottomMargin: Int = 0
    private var mWeatherBackground = 0
    private var mCenterWeather = false
    private var mLockscreenRootView: ViewGroup? = null
    private var mLockscreenClockInflated = false
    private lateinit var mWeatherContainer: LinearLayout

    private var mBroadcastRegistered = false
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action != null) {
                if (intent.action == ACTION_LS_CLOCK_INFLATED && weatherEnabled) {
                    mLockscreenClockInflated = true
                    placeWeatherView()
                }
            }
        }
    }

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized || !isComposeLockscreen) return

        Xprefs.apply {
            customLockscreenClock = getBoolean(LSCLOCK_SWITCH, false)
            weatherEnabled = getBoolean(WEATHER_SWITCH, false)
            weatherShowLocation = getBoolean(WEATHER_SHOW_LOCATION, true)
            weatherShowCondition = getBoolean(WEATHER_SHOW_CONDITION, true)
            weatherShowHumidity = getBoolean(WEATHER_SHOW_HUMIDITY, false)
            weatherShowWind = getBoolean(WEATHER_SHOW_WIND, false)
            weatherCustomColor = getBoolean(WEATHER_TEXT_COLOR_SWITCH, false)
            weatherColor = getInt(WEATHER_TEXT_COLOR, Color.WHITE)
            weatherTextSize = getSliderInt(WEATHER_TEXT_SIZE, 16)
            weatherImageSize = getSliderInt(WEATHER_ICON_SIZE, 18)
            mSideMargin = getSliderInt(WEATHER_CUSTOM_MARGINS_SIDE, 32)
            mTopMargin = getSliderInt(WEATHER_CUSTOM_MARGINS_TOP, 20)
            mBottomMargin = getSliderInt(WEATHER_CUSTOM_MARGINS_BOTTOM, 20)
            mWeatherBackground = Integer.parseInt(getString(WEATHER_STYLE, "0")!!)
            mCenterWeather = getBoolean(WEATHER_CENTER_VIEW, false)
        }

        if (key.isNotEmpty() &&
            (key[0] == WEATHER_SHOW_LOCATION ||
                    key[0] == WEATHER_SHOW_CONDITION ||
                    key[0] == WEATHER_SHOW_HUMIDITY ||
                    key[0] == WEATHER_SHOW_WIND ||
                    key[0] == WEATHER_TEXT_COLOR_SWITCH ||
                    key[0] == WEATHER_TEXT_COLOR ||
                    key[0] == WEATHER_TEXT_SIZE ||
                    key[0] == WEATHER_ICON_SIZE ||
                    key[0] == WEATHER_STYLE ||
                    key[0] == WEATHER_CUSTOM_MARGINS_BOTTOM ||
                    key[0] == WEATHER_CUSTOM_MARGINS_SIDE ||
                    key[0] == WEATHER_CUSTOM_MARGINS_TOP ||
                    key[0] == WEATHER_CENTER_VIEW)
        ) {
            updateWeatherView()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "DiscouragedApi")
    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (!isComposeLockscreen) return

        // Receiver to handle weather inflated
        if (!mBroadcastRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ACTION_LS_CLOCK_INFLATED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mContext.registerReceiver(
                    mReceiver,
                    intentFilter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                mContext.registerReceiver(
                    mReceiver,
                    intentFilter
                )
            }

            mBroadcastRegistered = true
        }

        mWeatherContainer = LinearLayout(mContext).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val aodBurnInLayerClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer")

        aodBurnInLayerClass
            .hookConstructor()
            .runAfter { param ->
                if (!weatherEnabled) return@runAfter

                val entryV = param.thisObject as View

                entryV.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!weatherEnabled) return@postDelayed

                            mLockscreenRootView = entryV.parent as ViewGroup

                            placeWeatherView()
                        }, 1000)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }

        val defaultNotificationStackScrollLayoutSectionClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.DefaultNotificationStackScrollLayoutSection")

        val notificationContainerId = mContext.resources.getIdentifier(
            "nssl_placeholder",
            "id",
            mContext.packageName
        )

        defaultNotificationStackScrollLayoutSectionClass
            .hookMethod("applyConstraints")
            .runAfter { param ->
                if (!weatherEnabled) return@runAfter

                val constraintSet = param.args[0]

                constraintSet.clear(
                    notificationContainerId,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    notificationContainerId,
                    ConstraintSet.TOP,
                    mWeatherContainer.id,
                    ConstraintSet.BOTTOM,
                    mContext.toPx(mBottomMargin)
                )
            }

        val smartspaceSectionClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.SmartspaceSection")

        smartspaceSectionClass
            .hookMethod("applyConstraints")
            .runAfter { param ->
                if (!weatherEnabled) return@runAfter

                val constraintSet = param.args[0]
                val clockView =
                    mLockscreenRootView?.findViewWithTag<View?>(ICONIFY_LOCKSCREEN_CLOCK_TAG)

                val dateSmartSpaceViewId = mContext.resources.getIdentifier(
                    "date_smartspace_view",
                    "id",
                    mContext.packageName
                )

                // Connect weather view to bottom of date smartspace
                constraintSet.clear(
                    mWeatherContainer.id,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    mWeatherContainer.id,
                    ConstraintSet.TOP,
                    clockView?.id ?: dateSmartSpaceViewId,
                    ConstraintSet.BOTTOM,
                    mContext.toPx(mTopMargin)
                )
            }
    }

    @SuppressLint("DiscouragedApi")
    private fun placeWeatherView() {
        if (!isComposeLockscreen) return
        if (!weatherEnabled || mLockscreenRootView == null) return
        if (customLockscreenClock && !mLockscreenClockInflated) return

        assignIdsToViews(mLockscreenRootView!!)

        val notificationContainerId = mContext.resources.getIdentifier(
            "nssl_placeholder",
            "id",
            mContext.packageName
        )
        val aodNotificationIconContainerId = mContext.resources.getIdentifier(
            "aod_notification_icon_container",
            "id",
            mContext.packageName
        )
        val dateSmartspaceViewId = mContext.resources.getIdentifier(
            "date_smartspace_view",
            "id",
            mContext.packageName
        )

        try {
            val currentWeatherView: CurrentWeatherView = CurrentWeatherView.getInstance(
                mContext,
                LOCKSCREEN_WEATHER
            )

            (currentWeatherView.parent as ViewGroup?)?.removeView(currentWeatherView)
            (mWeatherContainer.parent as ViewGroup?)?.removeView(mWeatherContainer)

            mWeatherContainer.addView(currentWeatherView)
            mLockscreenRootView!!.addView(mWeatherContainer)

            refreshWeatherView(currentWeatherView)

            // Weather placed, now inflate widgets
            val broadcast = Intent(ACTION_WEATHER_INFLATED)
            broadcast.setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            Thread { mContext.sendBroadcast(broadcast) }.start()

            constraintSetInstance?.also { constraintSet ->
                constraintSet.clone(mLockscreenRootView!!)

                // Connect weather view to parent
                constraintSet.connect(
                    mWeatherContainer.id,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                constraintSet.connect(
                    mWeatherContainer.id,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )

                if (customLockscreenClock) {
                    val clockView =
                        mLockscreenRootView!!.findViewWithTag<View?>(ICONIFY_LOCKSCREEN_CLOCK_TAG)

                    constraintSet.connect(
                        mWeatherContainer.id,
                        ConstraintSet.TOP,
                        clockView.id,
                        ConstraintSet.BOTTOM,
                        mContext.toPx(mTopMargin)
                    )
                } else {
                    constraintSet.connect(
                        mWeatherContainer.id,
                        ConstraintSet.TOP,
                        dateSmartspaceViewId,
                        ConstraintSet.BOTTOM,
                        mContext.toPx(mTopMargin)
                    )
                }

                // Connect notification container below weather
                if (notificationContainerId != 0) {
                    constraintSet.clear(
                        notificationContainerId,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        notificationContainerId,
                        ConstraintSet.TOP,
                        mWeatherContainer.id,
                        ConstraintSet.BOTTOM,
                        mContext.toPx(mBottomMargin)
                    )
                }

                // Connect aod notification icon container below weather
                if (aodNotificationIconContainerId != 0) {
                    constraintSet.clear(
                        aodNotificationIconContainerId,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        aodNotificationIconContainerId,
                        ConstraintSet.TOP,
                        mWeatherContainer.id,
                        ConstraintSet.BOTTOM,
                        mContext.toPx(mBottomMargin)
                    )
                }

                constraintSet.applyTo(mLockscreenRootView!!)
            }
        } catch (ignored: Throwable) {
        }
    }

    private fun refreshWeatherView(currentWeatherView: CurrentWeatherView?) {
        if (currentWeatherView == null) return

        currentWeatherView.apply {
            updateSizes(
                weatherTextSize,
                weatherImageSize,
                LOCKSCREEN_WEATHER
            )
            updateColors(
                if (weatherCustomColor) weatherColor else Color.WHITE,
                LOCKSCREEN_WEATHER
            )
            updateWeatherSettings(
                weatherShowLocation,
                weatherShowCondition,
                weatherShowHumidity,
                weatherShowWind,
                LOCKSCREEN_WEATHER
            )
            visibility = if (weatherEnabled) View.VISIBLE else View.GONE
            updateWeatherBg(
                mWeatherBackground,
                LOCKSCREEN_WEATHER
            )
        }

        updateMargins()
    }

    private fun updateMargins() {
        val childView = mWeatherContainer.getChildAt(0) as LinearLayout

        setMargins(
            childView,
            mContext,
            mSideMargin,
            mTopMargin,
            mSideMargin,
            mBottomMargin
        )

        mWeatherContainer.gravity = if (mCenterWeather) Gravity.CENTER_HORIZONTAL else Gravity.START
        childView.gravity = if (mCenterWeather) Gravity.CENTER_HORIZONTAL else Gravity.START
        (mWeatherContainer.getChildAt(0) as LinearLayout?)?.children?.forEach {
            (it as LinearLayout).gravity = if (mCenterWeather) {
                Gravity.CENTER_HORIZONTAL
            } else {
                Gravity.START or Gravity.CENTER_VERTICAL
            }
        }
    }

    private fun updateWeatherView() {
        if (isComposeLockscreen) {
            refreshWeatherView(CurrentWeatherView.getInstance(LOCKSCREEN_WEATHER))
        }
    }

    companion object {
        const val LOCKSCREEN_WEATHER = "iconify_ls_weather"
    }
}
