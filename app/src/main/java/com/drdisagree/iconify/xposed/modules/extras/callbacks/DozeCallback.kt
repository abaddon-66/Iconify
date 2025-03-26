package com.drdisagree.iconify.xposed.modules.extras.callbacks

import android.annotation.SuppressLint
import android.content.Context
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.CopyOnWriteArrayList

class DozeCallback(context: Context) : ModPack(context) {

    @Volatile
    private var mIsDozing: Boolean = false

    @Volatile
    private var mIsPulsing: Boolean = false

    private val mDozeListeners = CopyOnWriteArrayList<DozeListener>()

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        instance = this

        val statusBarStateControllerImplClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.StatusBarStateControllerImpl")
        val dozeScrimControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.DozeScrimController")

        fun updateState(isDozing: Boolean, isPulsing: Boolean) {
            synchronized(this@DozeCallback) {
                if (mIsDozing != isDozing || mIsPulsing != isPulsing) {
                    notifyStateChanged(isDozing || isPulsing)
                    mIsDozing = isDozing
                    mIsPulsing = isPulsing
                }
            }
        }

        statusBarStateControllerImplClass
            .hookMethod("setIsDozing")
            .runAfter { param ->
                val isDozing = param.args[0] as Boolean
                updateState(isDozing, mIsPulsing)
            }

        statusBarStateControllerImplClass
            .hookMethod("setPulsing")
            .runAfter { param ->
                val isPulsing = param.args[0] as Boolean
                updateState(mIsDozing, isPulsing)
            }

        dozeScrimControllerClass
            .hookMethod("onDozingChanged")
            .runAfter { param ->
                val isDozing = param.args[0] as Boolean
                updateState(isDozing, mIsPulsing)
            }
    }

    interface DozeListener {
        fun onDozingStarted()
        fun onDozingStopped()
    }

    private fun notifyStateChanged(isDozing: Boolean) {
        mDozeListeners.forEach {
            try {
                if (isDozing) it.onDozingStarted()
                else it.onDozingStopped()
            } catch (throwable: Throwable) {
                log(this@DozeCallback, "notifyDozingChanged: $throwable")
            }
        }
    }

    fun registerDozeChangeListener(callback: DozeListener) {
        if (!mDozeListeners.contains(callback)) {
            mDozeListeners.add(callback)
        }
    }

    fun unregisterDozeChangeListener(callback: DozeListener) {
        mDozeListeners.remove(callback)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DozeCallback? = null

        fun getInstance(): DozeCallback {
            return checkNotNull(instance) { "DozeCallback is not initialized yet!" }
        }
    }
}