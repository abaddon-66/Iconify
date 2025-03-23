package com.drdisagree.iconify.xposed.modules.launcher

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import com.drdisagree.iconify.data.common.Const.LAUNCHER3_PACKAGE
import com.drdisagree.iconify.data.common.Const.PIXEL_LAUNCHER_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.LAUNCHER_ICON_SIZE
import com.drdisagree.iconify.data.common.Preferences.LAUNCHER_TEXT_SIZE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class IconTextSize(context: Context) : ModPack(context) {

    private var invariantDeviceProfileInstance: Any? = null
    private var iconSizeModifier = 1f
    private var textSizeModifier = 1f

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            iconSizeModifier = getSliderInt(LAUNCHER_ICON_SIZE, 100) / 100f
            textSizeModifier = getSliderInt(LAUNCHER_TEXT_SIZE, 100) / 100f
        }

        when (key.firstOrNull()) {
            in setOf(
                LAUNCHER_ICON_SIZE,
                LAUNCHER_TEXT_SIZE
            ) -> invariantDeviceProfileInstance.callMethod("onConfigChanged", mContext)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val invariantDeviceProfileClass = findClass("com.android.launcher3.InvariantDeviceProfile")

        invariantDeviceProfileClass
            .hookConstructor()
            .runAfter { param ->
                invariantDeviceProfileInstance = param.thisObject
            }

        hookPixelLauncher(loadPackageParam)
        hookLauncher3(loadPackageParam)
    }

    private fun hookPixelLauncher(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != PIXEL_LAUNCHER_PACKAGE) return

        val bubbleTextViewClass = findClass("com.android.launcher3.BubbleTextView")

        bubbleTextViewClass
            .hookConstructor()
            .parameters(
                Context::class.java,
                AttributeSet::class.java,
                Int::class.javaPrimitiveType
            )
            .runAfter { param ->
                val bubbleTextView = param.thisObject as TextView

                var mIconSize = bubbleTextView.getField("mIconSize") as Int
                mIconSize = (mIconSize * iconSizeModifier).toInt()
                bubbleTextView.setField("mIconSize", mIconSize)

                val mTextSize = bubbleTextView.textSize
                bubbleTextView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    mTextSize * textSizeModifier
                )
            }
    }

    private fun hookLauncher3(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != LAUNCHER3_PACKAGE) return

        val displayOptionClass =
            findClass("com.android.launcher3.InvariantDeviceProfile\$DisplayOption")

        displayOptionClass
            .hookConstructor()
            .parameters(
                "com.android.launcher3.InvariantDeviceProfile\$GridOption",
                Context::class.java,
                AttributeSet::class.java
            )
            .runAfter { param ->
                val iconSizes = param.thisObject.getField("iconSizes") as FloatArray
                val allAppsIconSizes = param.thisObject.getField("allAppsIconSizes") as FloatArray
                val textSizes = param.thisObject.getField("textSizes") as FloatArray
                val allAppsIconTextSizes =
                    param.thisObject.getField("allAppsIconTextSizes") as FloatArray

                iconSizes[INDEX_DEFAULT] *= iconSizeModifier
                iconSizes[INDEX_LANDSCAPE] *= iconSizeModifier
                iconSizes[INDEX_TWO_PANEL_PORTRAIT] *= iconSizeModifier
                iconSizes[INDEX_TWO_PANEL_LANDSCAPE] *= iconSizeModifier

                allAppsIconSizes[INDEX_DEFAULT] *= iconSizeModifier
                allAppsIconSizes[INDEX_LANDSCAPE] *= iconSizeModifier
                allAppsIconSizes[INDEX_TWO_PANEL_PORTRAIT] *= iconSizeModifier
                allAppsIconSizes[INDEX_TWO_PANEL_LANDSCAPE] *= iconSizeModifier

                textSizes[INDEX_DEFAULT] *= textSizeModifier
                textSizes[INDEX_LANDSCAPE] *= textSizeModifier
                textSizes[INDEX_TWO_PANEL_PORTRAIT] *= textSizeModifier
                textSizes[INDEX_TWO_PANEL_LANDSCAPE] *= textSizeModifier

                allAppsIconTextSizes[INDEX_DEFAULT] *= textSizeModifier
                allAppsIconTextSizes[INDEX_LANDSCAPE] *= textSizeModifier
                allAppsIconTextSizes[INDEX_TWO_PANEL_PORTRAIT] *= textSizeModifier
                allAppsIconTextSizes[INDEX_TWO_PANEL_LANDSCAPE] *= textSizeModifier
            }
    }

    companion object {
        private const val INDEX_DEFAULT = 0
        private const val INDEX_LANDSCAPE = 1
        private const val INDEX_TWO_PANEL_PORTRAIT = 2
        private const val INDEX_TWO_PANEL_LANDSCAPE = 3
    }
}