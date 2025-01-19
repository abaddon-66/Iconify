package com.drdisagree.iconify.xposed

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

abstract class ModPack(private var context: Context) {
    protected val mContext: Context get() = context

    abstract fun updatePrefs(vararg key: String)

    abstract fun handleLoadPackage(loadPackageParam: LoadPackageParam)
}