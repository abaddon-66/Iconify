package com.drdisagree.iconify.xposed

import android.os.Build
import com.drdisagree.iconify.common.Const.PIXEL_LAUNCHER_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.xposed.modules.BackgroundChip
import com.drdisagree.iconify.xposed.modules.BatteryStyleManager
import com.drdisagree.iconify.xposed.modules.extras.callbacks.ControllersProvider
import com.drdisagree.iconify.xposed.modules.extras.callbacks.ThemeChange
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet
import com.drdisagree.iconify.xposed.modules.extras.utils.SettingsLibUtils
import com.drdisagree.iconify.xposed.modules.launcher.IconUpdater
import com.drdisagree.iconify.xposed.modules.lockscreen.Lockscreen
import com.drdisagree.iconify.xposed.modules.lockscreen.clock.LockscreenClock
import com.drdisagree.iconify.xposed.modules.lockscreen.clock.LockscreenClockA15
import com.drdisagree.iconify.xposed.modules.lockscreen.depthwallpaper.DepthWallpaper
import com.drdisagree.iconify.xposed.modules.lockscreen.depthwallpaper.DepthWallpaperA14
import com.drdisagree.iconify.xposed.modules.lockscreen.weather.LockscreenWeather
import com.drdisagree.iconify.xposed.modules.lockscreen.weather.LockscreenWeatherA15
import com.drdisagree.iconify.xposed.modules.lockscreen.widgets.LockscreenWidgets
import com.drdisagree.iconify.xposed.modules.lockscreen.widgets.LockscreenWidgetsA15
import com.drdisagree.iconify.xposed.modules.misc.Miscellaneous
import com.drdisagree.iconify.xposed.modules.quicksettings.ColorizeNotification
import com.drdisagree.iconify.xposed.modules.quicksettings.HeaderImage
import com.drdisagree.iconify.xposed.modules.quicksettings.OpQsHeader
import com.drdisagree.iconify.xposed.modules.quicksettings.QSTransparency
import com.drdisagree.iconify.xposed.modules.quicksettings.QuickSettings
import com.drdisagree.iconify.xposed.modules.quicksettings.headerclock.HeaderClock
import com.drdisagree.iconify.xposed.modules.quicksettings.headerclock.HeaderClockA14
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSBlackThemeA13
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSBlackThemeA14
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSBlackThemeA15
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSFluidThemeA13
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSFluidThemeA14
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSFluidThemeA15
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSLightThemeA12
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSLightThemeA13
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSLightThemeA14
import com.drdisagree.iconify.xposed.modules.quicksettings.themes.QSLightThemeA15
import com.drdisagree.iconify.xposed.modules.statusbar.Statusbar
import com.drdisagree.iconify.xposed.modules.volume.VolumePanel
import com.drdisagree.iconify.xposed.utils.HookCheck

object EntryList {

    private val topPriorityCommonModPacks: List<Class<out ModPack>> = listOf(
        SettingsLibUtils::class.java,
        HookCheck::class.java
    )

    private val systemUICommonModPacks: List<Class<out ModPack>> = listOf(
        MyConstraintSet::class.java,
        ControllersProvider::class.java,
        ThemeChange::class.java,
        BackgroundChip::class.java,
        HeaderImage::class.java,
        Lockscreen::class.java,
        LockscreenClock::class.java,
        LockscreenWidgets::class.java,
        LockscreenWeather::class.java,
        Miscellaneous::class.java,
        QSTransparency::class.java,
        QuickSettings::class.java,
        Statusbar::class.java,
        BatteryStyleManager::class.java,
        VolumePanel::class.java,
        ColorizeNotification::class.java
    )

    private val systemUiAndroid12ModPacks: List<Class<out ModPack>> = listOf(
        DepthWallpaper::class.java,
        QSFluidThemeA13::class.java,
        QSBlackThemeA13::class.java,
        QSLightThemeA12::class.java,
        HeaderClock::class.java
    )

    private val systemUiAndroid13ModPacks: List<Class<out ModPack>> = listOf(
        DepthWallpaper::class.java,
        QSFluidThemeA13::class.java,
        QSBlackThemeA13::class.java,
        QSLightThemeA13::class.java,
        HeaderClock::class.java
    )

    private val systemUiAndroid14ModPacks: List<Class<out ModPack>> = listOf(
        DepthWallpaperA14::class.java,
        QSFluidThemeA14::class.java,
        QSBlackThemeA14::class.java,
        QSLightThemeA14::class.java,
        HeaderClockA14::class.java,
        OpQsHeader::class.java
    )

    private val systemUiAndroid15ModPacks: List<Class<out ModPack>> = listOf(
        DepthWallpaperA14::class.java,
        QSFluidThemeA15::class.java,
        QSBlackThemeA15::class.java,
        QSLightThemeA15::class.java,
        HeaderClockA14::class.java,
        LockscreenClockA15::class.java,
        LockscreenWeatherA15::class.java,
        LockscreenWidgetsA15::class.java,
        OpQsHeader::class.java
    )

    private val pixelLauncherModPacks: List<Class<out ModPack>> = listOf(
        IconUpdater::class.java
    )

    fun getEntries(packageName: String): ArrayList<Class<out ModPack>> {
        val modPacks = ArrayList<Class<out ModPack>>()

        modPacks.addAll(topPriorityCommonModPacks)

        when (packageName) {
            SYSTEMUI_PACKAGE -> {
                if (!HookEntry.isChildProcess) {
                    modPacks.addAll(systemUICommonModPacks)

                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> { // android 15+
                            modPacks.addAll(systemUiAndroid15ModPacks)
                        }

                        Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> { // Android 14
                            modPacks.addAll(systemUiAndroid14ModPacks)
                        }

                        Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU -> { // Android 13
                            modPacks.addAll(systemUiAndroid13ModPacks)
                        }

                        else -> { // Android 12.0 and 12.1
                            modPacks.addAll(systemUiAndroid12ModPacks)
                        }
                    }
                }
            }

            PIXEL_LAUNCHER_PACKAGE -> {
                modPacks.addAll(pixelLauncherModPacks)
            }
        }

        return modPacks
    }
}
