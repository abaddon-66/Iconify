package com.drdisagree.iconify.xposed.modules.extras.callbacks

import android.annotation.SuppressLint
import android.content.Context
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.isMethodAvailable
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ThemeChangeCallback(context: Context) : ModPack(context) {

    private val mThemeChangedListeners = ArrayList<OnThemeChangedListener>()

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {

        instance = this

        // Get monet change so we can apply theme
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")

        scrimControllerClass
            .hookMethod("updateThemeColors")
            .runAfter { onThemeChanged() }

        if (scrimControllerClass.isMethodAvailable("updateThemeColors")) return

        val notificationPanelViewControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.NotificationPanelViewController",
            "$SYSTEMUI_PACKAGE.statusbar.phone.NotificationPanelViewController"
        )

        notificationPanelViewControllerClass
            .hookMethod("onThemeChanged")
            .runAfter { onThemeChanged() }

        if (notificationPanelViewControllerClass.isMethodAvailable("onThemeChanged")) return

        val configurationListenerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.NotificationPanelViewController\$ConfigurationListener",
            "$SYSTEMUI_PACKAGE.statusbar.phone.NotificationPanelViewController\$ConfigurationListener",
            suppressError = true
        )

        configurationListenerClass
            .hookMethod("onThemeChanged")
            .suppressError()
            .runAfter { onThemeChanged() }
    }

    interface OnThemeChangedListener {
        fun onThemeChanged()
    }

    private fun onThemeChanged() {
        mThemeChangedListeners.forEach {
            try {
                it.onThemeChanged()
            } catch (throwable: Throwable) {
                log(this@ThemeChangeCallback, "onThemeChanged: $throwable")
            }
        }
    }

    fun registerThemeChangedCallback(callback: OnThemeChangedListener) {
        mThemeChangedListeners.add(callback)
    }

    fun unRegisterThemeChangedCallback(callback: OnThemeChangedListener?) {
        mThemeChangedListeners.remove(callback)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ThemeChangeCallback? = null

        fun getInstance(): ThemeChangeCallback {
            return checkNotNull(instance) { "ThemeChangeCallback is not initialized yet!" }
        }
    }
}