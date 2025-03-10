package com.drdisagree.iconify.xposed.modules.extras.utils

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Display

object DisplayUtils {
    fun isScreenOn(context: Context): Boolean {
        val displays = getDisplayState(context)
        for (display in displays) {
            if (display.state == Display.STATE_ON) {
                return true
            }
        }
        return false
    }

    fun isScreenOff(context: Context): Boolean {
        val displays = getDisplayState(context)
        for (display in displays) {
            if (display.state == Display.STATE_OFF) {
                return true
            }
        }
        return false
    }

    fun isScreenDozing(context: Context): Boolean {
        val displays = getDisplayState(context)
        for (display in displays) {
            if (display.state == Display.STATE_DOZE ||
                display.state == Display.STATE_DOZE_SUSPEND
            ) {
                return true
            }
        }
        return false
    }

    private fun getDisplayState(context: Context): Array<Display> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays
    }

    val Context.isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val Context.isNightMode: Boolean
        get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
