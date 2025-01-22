package com.drdisagree.iconify.xposed.modules.extras.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import de.robv.android.xposed.XC_MethodHook

@SuppressLint("DiscouragedApi")
object StatusBarClock {

    fun getLeftClockView(mContext: Context, param: XC_MethodHook.MethodHookParam) = try {
        param.thisObject.getField("mClockView") as View
    } catch (throwable1: Throwable) {
        try {
            param.thisObject.getField("mLeftClock") as View
        } catch (throwable2: Throwable) {
            try {
                param.thisObject
                    .getField("mClockController")
                    .callMethod("getClock") as View
            } catch (throwable3: Throwable) {
                try {
                    val mActiveClock = param.thisObject
                        .getField("mClockController")
                        .getField("mActiveClock") as View
                    val mLeftClockId = mContext.resources.getIdentifier(
                        "clock",
                        "id",
                        mContext.packageName
                    )

                    if (mActiveClock.id == mLeftClockId) {
                        mActiveClock
                    } else {
                        null
                    }
                } catch (throwable4: Throwable) {
                    log(this@StatusBarClock, throwable4)
                    null
                }
            }
        }
    }

    fun getCenterClockView(mContext: Context, param: XC_MethodHook.MethodHookParam) = try {
        param.thisObject.getField("mCenterClockView") as View
    } catch (throwable1: Throwable) {
        try {
            param.thisObject.getField("mCenterClock") as View
        } catch (throwable2: Throwable) {
            try {
                param.thisObject
                    .getField("mClockController")
                    .callMethod("mCenterClockView") as View
            } catch (throwable3: Throwable) {
                try {
                    val mActiveClock = param.thisObject
                        .getField("mClockController")
                        .getField("mActiveClock") as View
                    val mCenterClockId = mContext.resources.getIdentifier(
                        "clock_center",
                        "id",
                        mContext.packageName
                    )

                    if (mActiveClock.id == mCenterClockId) {
                        mActiveClock
                    } else {
                        null
                    }
                } catch (throwable4: Throwable) {
                    try {
                        (param.thisObject.getField(
                            "mCenterClockLayout"
                        ) as LinearLayout).getChildAt(0)
                    } catch (throwable5: Throwable) {
                        null
                    }
                }
            }
        }
    }

    fun getRightClockView(mContext: Context, param: XC_MethodHook.MethodHookParam) = try {
        param.thisObject.getField("mRightClockView") as View
    } catch (throwable1: Throwable) {
        try {
            param.thisObject.getField("mRightClock") as View
        } catch (throwable2: Throwable) {
            try {
                param.thisObject
                    .getField("mClockController")
                    .callMethod("mRightClockView") as View
            } catch (throwable3: Throwable) {
                try {
                    val mActiveClock = param.thisObject
                        .getField("mClockController")
                        .getField("mActiveClock") as View
                    val mRightClockId = mContext.resources.getIdentifier(
                        "clock_right",
                        "id",
                        mContext.packageName
                    )

                    if (mActiveClock.id == mRightClockId) {
                        mActiveClock
                    } else {
                        null
                    }
                } catch (throwable4: Throwable) {
                    null
                }
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    fun setClockGravity(clockView: View?, gravity: Int) {
        if (clockView == null) return

        val layoutParams = clockView.layoutParams
        when (layoutParams) {
            is LinearLayout.LayoutParams -> {
                layoutParams.gravity = gravity
            }

            is FrameLayout.LayoutParams -> {
                layoutParams.gravity = gravity
            }

            is RelativeLayout.LayoutParams -> {
                when (gravity) {
                    Gravity.LEFT or Gravity.CENTER -> {
                        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    }

                    Gravity.CENTER -> {
                        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
                    }

                    Gravity.RIGHT or Gravity.CENTER -> {
                        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    }

                    else -> {
                        log(
                            this@StatusBarClock,
                            "LayoutParamsCheck - Unsupported gravity type for RelativeLayout: $gravity"
                        )
                    }
                }
            }

            else -> {
                log(
                    this@StatusBarClock,
                    "LayoutParamsCheck - Unknown LayoutParams type: ${layoutParams.javaClass.name}"
                )
            }
        }
        clockView.layoutParams = layoutParams

        (clockView as TextView).includeFontPadding = false
        clockView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT
        clockView.setGravity(Gravity.CENTER)
        clockView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER)
        clockView.requestLayout()
    }
}