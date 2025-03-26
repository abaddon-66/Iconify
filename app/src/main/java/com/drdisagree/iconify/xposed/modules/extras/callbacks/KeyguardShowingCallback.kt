package com.drdisagree.iconify.xposed.modules.extras.callbacks

import android.annotation.SuppressLint
import android.content.Context
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.CopyOnWriteArrayList

class KeyguardShowingCallback(context: Context) : ModPack(context) {

    private var isKeyguardState: Boolean = false
    private val mKeyguardShowingListeners = CopyOnWriteArrayList<KeyguardShowingListener>()

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        instance = this

        val qsImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSImpl",
            "$SYSTEMUI_PACKAGE.qs.QSFragment"
        )

        qsImplClass
            .hookMethod("setQsExpansion")
            .runAfter { param ->
                val isKeyguardState = param.thisObject.callMethod("isKeyguardState") as Boolean

                if (this.isKeyguardState != isKeyguardState) {
                    if (isKeyguardState) {
                        notifyKeyguardShown()
                    } else {
                        notifyKeyguardDismissed()
                    }
                    this.isKeyguardState = isKeyguardState
                }
            }
    }

    interface KeyguardShowingListener {
        fun onKeyguardShown()
        fun onKeyguardDismissed()
    }

    private fun notifyKeyguardShown() {
        mKeyguardShowingListeners.forEach {
            try {
                it.onKeyguardShown()
            } catch (throwable: Throwable) {
                log(this@KeyguardShowingCallback, "notifyKeyguardShown: $throwable")
            }
        }
    }

    private fun notifyKeyguardDismissed() {
        mKeyguardShowingListeners.forEach {
            try {
                it.onKeyguardDismissed()
            } catch (throwable: Throwable) {
                log(this@KeyguardShowingCallback, "notifyKeyguardDismissed: $throwable")
            }
        }
    }

    fun registerKeyguardShowingListener(callback: KeyguardShowingListener) {
        if (!mKeyguardShowingListeners.contains(callback)) {
            mKeyguardShowingListeners.add(callback)
        }
    }

    fun unregisterKeyguardShowingListener(callback: KeyguardShowingListener) {
        mKeyguardShowingListeners.remove(callback)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: KeyguardShowingCallback? = null

        fun getInstance(): KeyguardShowingCallback {
            return checkNotNull(instance) { "KeyguardShowingCallback is not initialized yet!" }
        }
    }
}