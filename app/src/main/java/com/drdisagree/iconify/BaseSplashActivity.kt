package com.drdisagree.iconify

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.drdisagree.iconify.data.common.Preferences.XPOSED_ONLY_MODE
import com.drdisagree.iconify.data.config.RPrefs
import com.drdisagree.iconify.ui.activities.MainActivity
import com.drdisagree.iconify.ui.activities.OnboardingActivity
import com.drdisagree.iconify.utils.ModuleUtils
import com.drdisagree.iconify.utils.RootUtils
import com.drdisagree.iconify.utils.SystemUtils
import com.drdisagree.iconify.utils.overlay.OverlayUtils
import com.google.android.material.color.DynamicColors
import com.topjohnwu.superuser.Shell

@SuppressLint("CustomSplashScreen")
abstract class BaseSplashActivity : AppCompatActivity() {

    private var keepShowing = true
    private val runner = Runnable {
        Shell.getShell { _: Shell? ->
            val isRooted = RootUtils.deviceProperlyRooted()
            val isModuleInstalled = ModuleUtils.moduleExists()
            val isOverlayInstalled = OverlayUtils.overlayExists()
            var isXposedOnlyMode = RPrefs.getBoolean(XPOSED_ONLY_MODE, false)
            val isVersionCodeCorrect = BuildConfig.VERSION_CODE == SystemUtils.savedVersionCode

            if (isRooted) {
                if (isOverlayInstalled) {
                    RPrefs.putBoolean(XPOSED_ONLY_MODE, false)
                } else if (isModuleInstalled) {
                    RPrefs.putBoolean(XPOSED_ONLY_MODE, true)
                    isXposedOnlyMode = true
                }
            }

            val isModuleProperlyInstalled = isModuleInstalled &&
                    (isOverlayInstalled || isXposedOnlyMode)

            val intent: Intent =
                if (SKIP_TO_HOMEPAGE_FOR_TESTING ||
                    (isRooted &&
                            isModuleProperlyInstalled &&
                            isVersionCodeCorrect)
                ) {
                    keepShowing = false
                    initializeMLKit()
                    Intent(
                        this@BaseSplashActivity,
                        if (FORCE_OVERLAY_INSTALLATION) OnboardingActivity::class.java
                        else MainActivity::class.java
                    )
                } else {
                    keepShowing = false
                    Intent(this@BaseSplashActivity, OnboardingActivity::class.java)
                }

            startActivity(intent)
            finish()
        }
    }

    abstract fun initializeMLKit()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen: SplashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { keepShowing }
        DynamicColors.applyToActivitiesIfAvailable(application)
        Thread(runner).start()
    }

    companion object {
        // For testing purposes
        private const val SKIP_INSTALLATION = false
        const val FORCE_OVERLAY_INSTALLATION = false
        val SKIP_TO_HOMEPAGE_FOR_TESTING = SKIP_INSTALLATION &&
                !FORCE_OVERLAY_INSTALLATION &&
                BuildConfig.DEBUG

        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            @Suppress("DEPRECATION")
            if (Shell.getCachedShell() == null) {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setFlags(Shell.FLAG_MOUNT_MASTER)
                        .setFlags(Shell.FLAG_REDIRECT_STDERR)
                        .setTimeout(20)
                )
            }
        }
    }
}
