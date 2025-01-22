package com.drdisagree.iconify.xposed.modules.extras.callbacks

import android.annotation.SuppressLint
import android.content.Context
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ThemeChange(context: Context) : ModPack(context) {

    private val mThemeChangedListeners = ArrayList<OnThemeChangedListener>()

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {

        instance = this

        // Get monet change so we can apply theme
        val scrimControllerClass = findClass("com.android.systemui.statusbar.phone.ScrimController")

        scrimControllerClass
            .hookMethod("updateThemeColors")
            .runAfter { onThemeChanged() }

        val notificationPanelViewControllerClass =
            findClass("com.android.systemui.shade.NotificationPanelViewController")

        notificationPanelViewControllerClass
            .hookMethod("onThemeChanged")
            .runAfter { onThemeChanged() }
    }

    interface OnThemeChangedListener {
        fun onThemeChanged()
    }

    private fun onThemeChanged() {
        for (callback in mThemeChangedListeners) {
            try {
                callback.onThemeChanged()
            } catch (ignored: Throwable) {
            }
        }
    }

    fun registerThemeChangedCallback(callback: OnThemeChangedListener) {
        instance!!.mThemeChangedListeners.add(callback)
    }

    /** @noinspection unused */
    fun unRegisterThemeChangedCallback(callback: OnThemeChangedListener?) {
        instance!!.mThemeChangedListeners.remove(callback)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ThemeChange? = null

        fun getInstance(): ThemeChange {
            return instance!!
        }

    }

}