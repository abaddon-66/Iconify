package com.drdisagree.iconify.xposed.modules.lockscreen.widgets

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
import com.drdisagree.iconify.common.Const.ACTION_LS_CLOCK_INFLATED
import com.drdisagree.iconify.common.Const.ACTION_WEATHER_INFLATED
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_CLOCK_TAG
import com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_WEATHER_TAG
import com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_WIDGET_TAG
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BOTTOM_MARGIN
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_CUSTOM_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_ENABLED
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_EXTRAS
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SCALE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_TOP_MARGIN
import com.drdisagree.iconify.common.Preferences.LSCLOCK_SWITCH
import com.drdisagree.iconify.common.Preferences.WEATHER_SWITCH
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
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.views.LockscreenWidgetsView
import com.drdisagree.iconify.xposed.modules.lockscreen.Lockscreen.Companion.isComposeLockscreen
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LockscreenWidgetsA15(context: Context) : ModPack(context) {

    // Parent
    private var mLockscreenRootView: ViewGroup? = null

    // Widgets Container
    private lateinit var mWidgetsContainer: LinearLayout

    // Activity Starter
    private var mActivityStarter: Any? = null

    // Ls custom clock
    private var customLockscreenClock = false
    private var mLockscreenClockInflated = false

    // Ls weather
    private var lsWeatherEnabled = false
    private var lsWeatherInflated = false

    // Widgets Prefs
    // Lockscreen Widgets
    private var mWidgetsEnabled: Boolean = false
    private var mDeviceWidgetEnabled = false
    private var mDeviceCustomColor = false
    private var mDeviceLinearColor = Color.WHITE
    private var mDeviceCircularColor = Color.WHITE
    private var mDeviceTextColor = Color.WHITE
    private var mWidgetsCustomColor = false
    private var mBigInactiveColor = Color.BLACK
    private var mBigActiveColor = Color.WHITE
    private var mSmallInactiveColor = Color.BLACK
    private var mSmallActiveColor = Color.WHITE
    private var mBigIconActiveColor = Color.WHITE
    private var mBigIconInactiveColor = Color.BLACK
    private var mSmallIconActiveColor = Color.WHITE
    private var mSmallIconInactiveColor = Color.BLACK
    private var mDeviceName = ""
    private var mMainWidgets: String = ""
    private var mExtraWidgets: String = ""
    private var mTopMargin = 0
    private var mBottomMargin = 0
    private var mWidgetsScale = 1.0f

    private var mBroadcastRegistered = false
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action != null && mWidgetsEnabled) {
                if (intent.action == ACTION_WEATHER_INFLATED) {
                    lsWeatherInflated = true
                    placeWidgets()
                } else if (intent.action == ACTION_LS_CLOCK_INFLATED && !lsWeatherEnabled) {
                    mLockscreenClockInflated = true
                    placeWidgets()
                }
            }
        }
    }

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized || !isComposeLockscreen) return

        Xprefs.apply {
            // Ls custom clock
            customLockscreenClock = getBoolean(LSCLOCK_SWITCH, false)

            // Ls weather
            lsWeatherEnabled = getBoolean(WEATHER_SWITCH, false)

            // Widgets
            mWidgetsEnabled = getBoolean(LOCKSCREEN_WIDGETS_ENABLED, false)
            mDeviceWidgetEnabled = getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET, false)
            mMainWidgets = getString(LOCKSCREEN_WIDGETS, "")!!
            mExtraWidgets = getString(LOCKSCREEN_WIDGETS_EXTRAS, "")!!
            mDeviceCustomColor =
                getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH, false)
            mDeviceLinearColor = getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR, Color.WHITE)
            mDeviceCircularColor =
                getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR, Color.WHITE)
            mDeviceTextColor = getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR, Color.WHITE)
            mDeviceName = getString(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE, "")!!
            mWidgetsCustomColor = getBoolean(LOCKSCREEN_WIDGETS_CUSTOM_COLOR, false)
            mBigInactiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_INACTIVE, Color.BLACK)
            mBigActiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_ACTIVE, Color.WHITE)
            mSmallInactiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_INACTIVE, Color.BLACK)
            mSmallActiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_ACTIVE, Color.WHITE)
            mBigIconActiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE, Color.BLACK)
            mBigIconInactiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE, Color.WHITE)
            mSmallIconActiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE, Color.BLACK)
            mSmallIconInactiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE, Color.WHITE)
            mTopMargin = getSliderInt(LOCKSCREEN_WIDGETS_TOP_MARGIN, 0)
            mBottomMargin = getSliderInt(LOCKSCREEN_WIDGETS_BOTTOM_MARGIN, 0)
            mWidgetsScale = getSliderFloat(LOCKSCREEN_WIDGETS_SCALE, 1.0f)
        }

        when (key.firstOrNull()) {
            in setOf(
                LOCKSCREEN_WIDGETS_ENABLED,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET,
                LOCKSCREEN_WIDGETS,
                LOCKSCREEN_WIDGETS_EXTRAS
            ) -> updateLockscreenWidgets()

            in setOf(
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE
            ) -> updateLsDeviceWidget()

            in setOf(
                LOCKSCREEN_WIDGETS_CUSTOM_COLOR,
                LOCKSCREEN_WIDGETS_BIG_ACTIVE,
                LOCKSCREEN_WIDGETS_BIG_INACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_ACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_INACTIVE,
                LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE,
                LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE
            ) -> updateLockscreenWidgetsColors()

            in setOf(
                LOCKSCREEN_WIDGETS_TOP_MARGIN,
                LOCKSCREEN_WIDGETS_BOTTOM_MARGIN
            ) -> updateMargins()

            LOCKSCREEN_WIDGETS_SCALE -> updateLockscreenWidgetsScale()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (!isComposeLockscreen) return

        // Receiver to handle weather inflated
        if (!mBroadcastRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ACTION_WEATHER_INFLATED)
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

        mWidgetsContainer = LinearLayout(mContext).apply {
            id = View.generateViewId()
            tag = ICONIFY_LOCKSCREEN_WIDGET_TAG
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val keyguardQuickAffordanceInteractor =
            findClass("$SYSTEMUI_PACKAGE.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor")

        keyguardQuickAffordanceInteractor
            .hookConstructor()
            .runAfter { param ->
                try {
                    mActivityStarter = param.thisObject.getField("activityStarter")
                } catch (t: Throwable) {
                    log(this@LockscreenWidgetsA15, "Failed to get ActivityStarter")
                }
                setActivityStarter()
            }

        val aodBurnInLayerClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer")

        aodBurnInLayerClass
            .hookConstructor()
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                val entryV = param.thisObject as View

                entryV.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!mWidgetsEnabled) return@postDelayed

                            mLockscreenRootView = entryV.parent as ViewGroup

                            placeWidgets()
                        }, 1000)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }

        val keyguardClockSwitch = findClass("com.android.keyguard.KeyguardClockSwitch")

        keyguardClockSwitch
            .hookMethod("updateClockViews")
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                updateLockscreenWidgetsOnClock(param.args[0] as Boolean)
            }

        val dozeScrimControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.DozeScrimController")

        dozeScrimControllerClass
            .hookMethod("onDozingChanged")
            .runAfter { param -> updateDozingState(param.args[0] as Boolean) }
    }

    private fun placeWidgets() {
        if (!isComposeLockscreen) return
        if (!mWidgetsEnabled || mLockscreenRootView == null) return
        if (customLockscreenClock && !mLockscreenClockInflated) return
        if (lsWeatherEnabled && !lsWeatherInflated) return

        try {
            val lsWidgets = LockscreenWidgetsView.getInstance(mContext, mActivityStarter)
            (lsWidgets.parent as ViewGroup?)?.removeView(lsWidgets)
            (mWidgetsContainer.parent as ViewGroup?)?.removeView(mWidgetsContainer)

            mWidgetsContainer.addView(lsWidgets)
            mLockscreenRootView?.addView(mWidgetsContainer)

            updateLockscreenWidgets()
            updateLsDeviceWidget()
            updateLockscreenWidgetsColors()
            updateMargins()
            updateLockscreenWidgetsScale()
            applyLayoutConstraints()
        } catch (ignored: Throwable) {
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun applyLayoutConstraints(widgetView: ViewGroup = mWidgetsContainer) {
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

        constraintSetInstance?.also { constraintSet ->
            constraintSet.clone(mLockscreenRootView!!)

            // Connect widget view to parent
            constraintSet.connect(
                widgetView.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            constraintSet.connect(
                widgetView.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )

            if (lsWeatherEnabled) {
                val weatherView = mLockscreenRootView!!.findViewWithTag<View?>(
                    ICONIFY_LOCKSCREEN_WEATHER_TAG
                )

                constraintSet.connect(
                    widgetView.id,
                    ConstraintSet.TOP,
                    weatherView.id,
                    ConstraintSet.BOTTOM,
                    mContext.toPx(mTopMargin)
                )
            } else {
                if (customLockscreenClock) {
                    val clockView = mLockscreenRootView!!.findViewWithTag<View?>(
                        ICONIFY_LOCKSCREEN_CLOCK_TAG
                    )

                    constraintSet.connect(
                        widgetView.id,
                        ConstraintSet.TOP,
                        clockView.id,
                        ConstraintSet.BOTTOM,
                        mContext.toPx(mTopMargin)
                    )
                } else {
                    constraintSet.connect(
                        widgetView.id,
                        ConstraintSet.TOP,
                        dateSmartspaceViewId,
                        ConstraintSet.BOTTOM,
                        mContext.toPx(mTopMargin)
                    )
                }
            }

            // Connect notification container below widget
            if (notificationContainerId != 0) {
                constraintSet.clear(
                    notificationContainerId,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    notificationContainerId,
                    ConstraintSet.TOP,
                    widgetView.id,
                    ConstraintSet.BOTTOM,
                    mContext.toPx(mBottomMargin)
                )
            }

            // Connect aod notification icon container below widget
            if (aodNotificationIconContainerId != 0) {
                constraintSet.clear(
                    aodNotificationIconContainerId,
                    ConstraintSet.BOTTOM
                )
                constraintSet.connect(
                    aodNotificationIconContainerId,
                    ConstraintSet.BOTTOM,
                    widgetView.id,
                    ConstraintSet.TOP,
                    mContext.toPx(mBottomMargin)
                )
            }

            constraintSet.applyTo(mLockscreenRootView!!)
        }
    }

    private fun updateLockscreenWidgets() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setOptions(mWidgetsEnabled, mDeviceWidgetEnabled, mMainWidgets, mExtraWidgets)
    }

    private fun updateLockscreenWidgetsOnClock(isLargeClock: Boolean) {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setIsLargeClock(if (customLockscreenClock) false else isLargeClock)
    }

    private fun updateLsDeviceWidget() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setDeviceWidgetOptions(
            mDeviceCustomColor,
            mDeviceLinearColor,
            mDeviceCircularColor,
            mDeviceTextColor,
            mDeviceName
        )
    }

    private fun updateLockscreenWidgetsColors() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setCustomColors(
            mWidgetsCustomColor,
            mBigInactiveColor,
            mBigActiveColor,
            mSmallInactiveColor,
            mSmallActiveColor,
            mBigIconInactiveColor,
            mBigIconActiveColor,
            mSmallIconInactiveColor,
            mSmallIconActiveColor
        )
    }

    private fun updateMargins() {
        val childView = mWidgetsContainer.getChildAt(0) as LinearLayout

        mWidgetsContainer.gravity = Gravity.CENTER_HORIZONTAL
        childView.gravity = Gravity.CENTER_HORIZONTAL

        setMargins(
            childView,
            mContext,
            0,
            mTopMargin,
            0,
            mBottomMargin
        )
    }

    private fun updateLockscreenWidgetsScale() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setScale(mWidgetsScale)
    }

    private fun updateDozingState(isDozing: Boolean) {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setDozingState(isDozing)
    }

    private fun setActivityStarter() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        if (mActivityStarter != null) lsWidgets.setActivityStarter(mActivityStarter)
    }
}