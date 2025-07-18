package com.drdisagree.iconify.xposed.modules.lockscreen.depthwallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Const.ACTION_EXTRACT_FAILURE
import com.drdisagree.iconify.data.common.Const.ACTION_EXTRACT_SUBJECT
import com.drdisagree.iconify.data.common.Const.ACTION_EXTRACT_SUCCESS
import com.drdisagree.iconify.data.common.Const.ACTION_UPDATE_DEPTH_WALLPAPER_FOREGROUND_VISIBILITY
import com.drdisagree.iconify.data.common.Const.AI_PLUGIN_PACKAGE
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.CUSTOM_DEPTH_WALLPAPER_SWITCH
import com.drdisagree.iconify.data.common.Preferences.DEPTH_WALLPAPER_AI_MODE
import com.drdisagree.iconify.data.common.Preferences.DEPTH_WALLPAPER_CHANGED
import com.drdisagree.iconify.data.common.Preferences.DEPTH_WALLPAPER_FOREGROUND_ALPHA
import com.drdisagree.iconify.data.common.Preferences.DEPTH_WALLPAPER_ON_AOD
import com.drdisagree.iconify.data.common.Preferences.DEPTH_WALLPAPER_SWITCH
import com.drdisagree.iconify.data.common.Preferences.ICONIFY_DEPTH_WALLPAPER_BACKGROUND_TAG
import com.drdisagree.iconify.data.common.Preferences.ICONIFY_DEPTH_WALLPAPER_FOREGROUND_TAG
import com.drdisagree.iconify.data.common.Preferences.LOCKSCREEN_SHADE_SWITCH
import com.drdisagree.iconify.data.common.Preferences.LSCLOCK_SWITCH
import com.drdisagree.iconify.data.common.XposedConst.DEPTH_WALL_BG_FILE
import com.drdisagree.iconify.data.common.XposedConst.DEPTH_WALL_FG_FILE
import com.drdisagree.iconify.xposed.HookRes.Companion.modRes
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.callbacks.BootCallback
import com.drdisagree.iconify.xposed.modules.extras.callbacks.KeyguardShowingCallback
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.reAddView
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.lockscreen.AlbumArt.Companion.shouldShowAlbumArt
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("DiscouragedApi")
abstract class BaseDepthWallpaperA14(context: Context) : ModPack(context) {

    private var showDepthWallpaper = false
    private var showLockscreenClock = false
    private var showCustomImages = false
    private var foregroundAlpha = 1.0f
    private var mPreviousState: String? = null
    private var mScrimController: Any? = null
    private var mForegroundDimmingOverlay: Drawable? = null
    private lateinit var mWallpaperForeground: FrameLayout
    private lateinit var mWallpaperBackground: FrameLayout
    private lateinit var mWallpaperBitmapContainer: FrameLayout
    private lateinit var mWallpaperDimmingOverlay: FrameLayout
    private var mWallpaperForegroundCacheValid = false
    private var mLayersCreated = false
    private var showOnAOD = true
    private var keepLockScreenShade = true
    protected var mAiMode = 0
    private var mPluginReceiverRegistered = false
    private lateinit var mPluginReceiver: BroadcastReceiver
    private var wallpaperProcessorThread: Thread? = null

    private var shouldShowForeground = true
    private var shouldShowBackground = true
    private var mBroadcastRegistered = false
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UPDATE_DEPTH_WALLPAPER_FOREGROUND_VISIBILITY) {
                updateForegroundVisibility()
            }
        }
    }

    abstract fun handleSubjectExtraction(scaledWallpaper: Bitmap? = null)

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            showDepthWallpaper = getBoolean(DEPTH_WALLPAPER_SWITCH, false)
            showLockscreenClock = getBoolean(LSCLOCK_SWITCH, false)
            showCustomImages = getBoolean(CUSTOM_DEPTH_WALLPAPER_SWITCH, false)
            foregroundAlpha = getSliderInt(DEPTH_WALLPAPER_FOREGROUND_ALPHA, 80) / 100.0f
            showOnAOD = getBoolean(DEPTH_WALLPAPER_ON_AOD, true)
            keepLockScreenShade = getBoolean(LOCKSCREEN_SHADE_SWITCH, true)
            mAiMode = getString(DEPTH_WALLPAPER_AI_MODE, "0")!!.toInt()
        }

        if (key.isNotEmpty()) {
            key[0].let {
                if (it == DEPTH_WALLPAPER_SWITCH ||
                    it == DEPTH_WALLPAPER_CHANGED ||
                    it == CUSTOM_DEPTH_WALLPAPER_SWITCH
                ) {
                    if (it == DEPTH_WALLPAPER_CHANGED) {
                        mWallpaperForegroundCacheValid = false
                    }

                    if (it == CUSTOM_DEPTH_WALLPAPER_SWITCH && !showCustomImages) {
                        invalidateCache()
                    }

                    setCustomDepthWallpaper()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "NewApi")
    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        // Receiver to handle foreground visibility
        if (!mBroadcastRegistered) {
            val intentFilter = IntentFilter().apply {
                addAction(ACTION_UPDATE_DEPTH_WALLPAPER_FOREGROUND_VISIBILITY)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mContext.registerReceiver(
                    mReceiver,
                    intentFilter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                mContext.registerReceiver(
                    mReceiver,
                    intentFilter
                )
            }

            mBroadcastRegistered = true
        }

        mPluginReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != null) {
                    when (intent.action) {
                        ACTION_EXTRACT_SUCCESS -> {
                            mWallpaperForegroundCacheValid = false
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    modRes.getString(R.string.depth_wallpaper_subject_extraction_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        ACTION_EXTRACT_FAILURE -> {
                            mWallpaperForegroundCacheValid = false
                            log(
                                this@BaseDepthWallpaperA14,
                                "Subject extraction failed\n${intent.getStringExtra("error")}"
                            )

                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    modRes.getString(R.string.depth_wallpaper_subject_extraction_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        if (!mPluginReceiverRegistered) {
            IntentFilter().apply {
                addAction(ACTION_EXTRACT_SUCCESS)
                addAction(ACTION_EXTRACT_FAILURE)
            }.also { intentFilter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mContext.registerReceiver(
                        mPluginReceiver,
                        intentFilter,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    mContext.registerReceiver(mPluginReceiver, intentFilter)
                }
            }
            mPluginReceiverRegistered = true
        }

        val qsImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSImpl",
            "$SYSTEMUI_PACKAGE.qs.QSFragment"
        )
        val canvasEngineClass =
            findClass("$SYSTEMUI_PACKAGE.wallpapers.ImageWallpaper\$CanvasEngine")
        val centralSurfacesImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl",
            suppressError = true
        )
        val statusBarKeyguardViewManagerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.StatusBarKeyguardViewManager")
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val scrimViewClass = findClass("$SYSTEMUI_PACKAGE.scrim.ScrimView")
        val keyguardBottomAreaViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.KeyguardBottomAreaView")

        scrimViewClass
            .hookMethod("setViewAlpha")
            .runBefore { param ->
                if (!mLayersCreated) return@runBefore

                if (showOnAOD && mScrimController.getField("mState").toString() != "KEYGUARD") {
                    mWallpaperForeground.post { mWallpaperForeground.alpha = foregroundAlpha }
                } else if (mScrimController.getField("mNotificationsScrim") == param.thisObject) { // instead of using the mScrimName since older ones don't have that field
                    val mScrimBehindAlphaKeyguard = mScrimController.getField(
                        "mScrimBehindAlphaKeyguard"
                    ) as Float

                    var notificationAlpha = param.args[0] as Float

                    if (notificationAlpha < mScrimBehindAlphaKeyguard) {
                        notificationAlpha = 0f
                    }

                    val foregroundAlpha = if (notificationAlpha > mScrimBehindAlphaKeyguard) {
                        (1f - notificationAlpha) / (1f - mScrimBehindAlphaKeyguard)
                    } else {
                        1f
                    }

                    mWallpaperForeground.post { mWallpaperForeground.alpha = foregroundAlpha }
                }
            }

        centralSurfacesImplClass
            .hookMethod("start")
            .runAfter {
                val scrimBehind = mScrimController.getField("mScrimBehind") as View
                val rootView = scrimBehind.parent as ViewGroup

                val targetView = rootView.findViewById<ViewGroup>(
                    mContext.resources.getIdentifier(
                        "notification_container_parent",
                        "id",
                        mContext.packageName
                    )
                )

                if (!mLayersCreated) {
                    createLayers()
                }

                rootView.reAddView(mWallpaperBackground, 0)
                targetView.reAddView(mWallpaperForeground, 1)
            }

        centralSurfacesImplClass
            .hookConstructor()
            .runAfter { param ->
                val mWakefulnessObserver = param.thisObject.getFieldSilently("mWakefulnessObserver")

                mWakefulnessObserver?.javaClass
                    .hookMethod("onStartedWakingUp")
                    .runAfter { setDepthWallpaper() }
            }

        centralSurfacesImplClass
            .hookMethod("onStartedWakingUp")
            .suppressError()
            .runAfter { setDepthWallpaper() }

        statusBarKeyguardViewManagerClass
            .hookMethod("onStartedWakingUp")
            .suppressError()
            .runAfter { setDepthWallpaper() }

        canvasEngineClass
            .hookMethod("onSurfaceDestroyed")
            .runAfter { param ->
                // lockscreen wallpaper changed
                if (showDepthWallpaper && !showCustomImages && isLockScreenWallpaper(param.thisObject)) {
                    invalidateCache()
                }
            }

        canvasEngineClass
            .hookMethod("onCreate")
            .runAfter { param ->
                if (param.thisObject
                        .getField("mWallpaperManager")
                        .callMethod(
                            "getWallpaperInfo",
                            WallpaperManager.FLAG_LOCK
                        ) != null && !showCustomImages
                ) { // it's live wallpaper. we can't use that
                    invalidateCache()
                }
            }

        canvasEngineClass
            .hookMethod("drawFrameOnCanvas")
            .runAfter { param ->
                wallpaperProcessorThread?.interrupt()

                if (showDepthWallpaper && !showCustomImages && isLockScreenWallpaper(param.thisObject)) {
                    wallpaperProcessorThread = Thread {
                        val wallpaperBitmap = Bitmap.createBitmap((param.args[0] as Bitmap))
                        val cacheIsValid: Boolean = assertCache(wallpaperBitmap)

                        val displayBounds = (param.thisObject
                            .callMethod("getDisplayContext") as Context)
                            .getSystemService(WindowManager::class.java)
                            .currentWindowMetrics
                            .bounds

                        val ratioW = 1f * displayBounds.width() / wallpaperBitmap.width
                        val ratioH = 1f * displayBounds.height() / wallpaperBitmap.height

                        val desiredHeight = (max(
                            ratioH.toDouble(),
                            ratioW.toDouble()
                        ) * wallpaperBitmap.height).roundToInt().toInt()
                        val desiredWidth = (max(
                            ratioH.toDouble(),
                            ratioW.toDouble()
                        ) * wallpaperBitmap.width).roundToInt().toInt()

                        val xPixelShift = (desiredWidth - displayBounds.width()) / 2
                        val yPixelShift = (desiredHeight - displayBounds.height()) / 2

                        val scaledWallpaperBitmap =
                            wallpaperBitmap.scale(desiredWidth, desiredHeight).let {
                                Bitmap.createBitmap(
                                    it,
                                    xPixelShift,
                                    yPixelShift,
                                    displayBounds.width(),
                                    displayBounds.height()
                                )
                            }.let {
                                Bitmap.createBitmap(it)
                            }

                        try {
                            DEPTH_WALL_BG_FILE.parentFile?.mkdirs()
                            val out = FileOutputStream(DEPTH_WALL_BG_FILE)
                            scaledWallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            out.flush()
                            out.close()
                        } catch (throwable: IOException) {
                            log(this@BaseDepthWallpaperA14, throwable)
                        }

                        if (!mLayersCreated) {
                            createLayers()
                        }

                        mWallpaperBackground.post {
                            mWallpaperBitmapContainer.background =
                                scaledWallpaperBitmap.toDrawable(mContext.resources)
                            if (mScrimController != null) {
                                mWallpaperDimmingOverlay.setBackgroundColor(Color.BLACK)
                                mWallpaperDimmingOverlay.alpha = mScrimController.getField(
                                    "mScrimBehindAlphaKeyguard"
                                ) as Float
                            }
                        }

                        if (!cacheIsValid) {
                            handleSubjectExtraction(scaledWallpaperBitmap)
                        }

                        wallpaperProcessorThread = null
                    }

                    wallpaperProcessorThread?.start()
                }
            }

        scrimControllerClass
            .hookConstructor()
            .runAfter { param -> mScrimController = param.thisObject }

        scrimControllerClass
            .hookMethod("applyAndDispatchState")
            .runAfter { setDepthWallpaper() }

        qsImplClass
            .hookMethod("setQsExpansion")
            .runAfter { param ->
                if (param.thisObject.callMethod("isKeyguardState") as Boolean) {
                    setDepthWallpaper()
                }
            }

        KeyguardShowingCallback.getInstance().registerKeyguardShowingListener(
            object : KeyguardShowingCallback.KeyguardShowingListener {
                override fun onKeyguardShown() {
                    shouldShowBackground = true
                }

                override fun onKeyguardDismissed() {
                    shouldShowForeground = false
                    shouldShowBackground = false

                    updateForegroundVisibility()
                    setDepthWallpaper()
                }
            }
        )

        /*
         * Custom depth wallpaper images
         */
        keyguardBottomAreaViewClass
            .hookMethod("onConfigurationChanged")
            .runAfter { setCustomDepthWallpaper() }

        setCustomDepthWallpaper()
    }

    private fun updateForegroundVisibility(targetAlpha: Float = 1f, duration: Long = 0L) {
        if (::mWallpaperForeground.isInitialized.not()) return

        // Hide foreground when album art is showing
        if (showDepthWallpaper && shouldShowForeground && !shouldShowAlbumArt) {
            // Smooth appearance
            if (duration == 0L) {
                mWallpaperForeground.visibility = View.VISIBLE
            } else {
                mWallpaperForeground.apply {
                    if (visibility != View.VISIBLE) {
                        visibility = View.VISIBLE
                        alpha = 0f
                    }
                    animate()
                        .alpha(targetAlpha)
                        .setDuration(duration)
                        .start()
                }
            }
        } else {
            mWallpaperForeground.visibility = View.GONE
        }
    }

    protected fun sendPluginIntent() {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    mContext,
                    modRes.getString(R.string.depth_wallpaper_subject_extraction_started),
                    Toast.LENGTH_LONG
                ).show()
            }

            mContext.sendBroadcast(
                Intent(ACTION_EXTRACT_SUBJECT).apply {
                    setComponent(
                        ComponentName(
                            AI_PLUGIN_PACKAGE,
                            "$AI_PLUGIN_PACKAGE.receivers.SubjectExtractionReceiver"
                        )
                    )
                    putExtra("sourcePath", DEPTH_WALL_BG_FILE.absolutePath)
                    putExtra("destinationPath", DEPTH_WALL_FG_FILE.absolutePath)
                    setPackage(mContext.packageName)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
            )
        } catch (_: Throwable) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun assertCache(wallpaperBitmap: Bitmap): Boolean {
        var cacheIsValid = false

        try {
            val compressedBitmap = ByteArrayOutputStream()
            wallpaperBitmap.compress(Bitmap.CompressFormat.JPEG, 100, compressedBitmap)
            if (DEPTH_WALL_BG_FILE.exists()) {
                val cacheStream = FileInputStream(DEPTH_WALL_BG_FILE)

                if (cacheStream.readAllBytes().contentEquals(compressedBitmap.toByteArray())) {
                    cacheIsValid = true
                } else {
                    val newCacheStream = FileOutputStream(DEPTH_WALL_BG_FILE)
                    compressedBitmap.writeTo(newCacheStream)
                    newCacheStream.close()
                }
                cacheStream.close()
            }
            compressedBitmap.close()
        } catch (_: Throwable) {
        }

        if (!cacheIsValid) {
            invalidateCache()
        }

        return cacheIsValid
    }

    private fun createLayers() {
        mWallpaperForeground = FrameLayout(mContext).apply {
            tag = ICONIFY_DEPTH_WALLPAPER_FOREGROUND_TAG
        }
        mWallpaperBackground = FrameLayout(mContext).apply {
            tag = ICONIFY_DEPTH_WALLPAPER_BACKGROUND_TAG
        }
        mWallpaperDimmingOverlay = FrameLayout(mContext)
        mWallpaperBitmapContainer = FrameLayout(mContext)

        val layoutParams = FrameLayout.LayoutParams(
            MATCH_PARENT,
            MATCH_PARENT
        )

        mWallpaperDimmingOverlay.setBackgroundColor(
            if (DEPTH_WALL_BG_FILE.exists()) {
                Color.BLACK
            } else {
                Color.TRANSPARENT
            }
        )
        mWallpaperDimmingOverlay.alpha = 0F

        mWallpaperDimmingOverlay.layoutParams = layoutParams
        mWallpaperBitmapContainer.layoutParams = layoutParams

        mWallpaperBackground.addView(mWallpaperBitmapContainer)
        mWallpaperBackground.addView(mWallpaperDimmingOverlay)

        mWallpaperForeground.layoutParams = layoutParams
        mWallpaperBackground.layoutParams = layoutParams

        mWallpaperForeground.id = View.generateViewId()

        mLayersCreated = true
    }

    private fun isLockScreenWallpaper(canvasEngine: Any): Boolean {
        return ((getWallpaperFlag(canvasEngine) and WallpaperManager.FLAG_LOCK) == WallpaperManager.FLAG_LOCK)
    }

    private fun setDepthWallpaper() {
        if (mScrimController == null) return

        val state = mScrimController.getField("mState").toString()
        val showForeground = (showDepthWallpaper &&
                (state == "KEYGUARD" || (showOnAOD && state in setOf("AOD", "PULSING"))))

        if (showForeground) {
            if ((!mWallpaperForegroundCacheValid || mWallpaperForeground.background == null) &&
                DEPTH_WALL_FG_FILE.exists()
            ) {
                try {
                    FileInputStream(DEPTH_WALL_FG_FILE).use { inputStream ->
                        val bitmapDrawable = BitmapDrawable.createFromStream(
                            inputStream,
                            ""
                        )
                        bitmapDrawable!!.alpha = 255

                        mForegroundDimmingOverlay = bitmapDrawable.constantState!!
                            .newDrawable().mutate()
                        mForegroundDimmingOverlay!!.setTint(Color.BLACK)

                        mWallpaperForeground.background = LayerDrawable(
                            arrayOf(
                                bitmapDrawable,
                                mForegroundDimmingOverlay
                            )
                        )
                        mWallpaperForegroundCacheValid = true
                    }
                } catch (_: Throwable) {
                }
            }

            if (mWallpaperForegroundCacheValid && mWallpaperForeground.background != null) {
                mWallpaperForeground.background.alpha = (foregroundAlpha * 255).toInt()

                val (targetAlpha, requiresAnimation) = if (state != "KEYGUARD") { // AOD
                    mForegroundDimmingOverlay!!.alpha = 192
                    foregroundAlpha to true
                } else {
                    // this is the dimmed wallpaper coverage
                    mForegroundDimmingOverlay!!.alpha =
                        if (keepLockScreenShade) (mScrimController.getField("mScrimBehindAlphaKeyguard") as Float * 240).roundToInt() // A tad bit lower than max. show it a bit lighter than other stuff
                        else 0
                    foregroundAlpha to (showOnAOD && (mPreviousState in setOf("AOD", "PULSING")))
                }

                mWallpaperDimmingOverlay.alpha =
                    mScrimController.getField("mScrimBehindAlphaKeyguard") as Float

                mWallpaperBackground.visibility =
                    if (shouldShowBackground) View.VISIBLE else View.GONE
                shouldShowForeground = shouldShowBackground
                updateForegroundVisibility(targetAlpha, if (requiresAnimation) 300L else 0L)
            }
        } else if (mLayersCreated) {
            shouldShowForeground = false
            updateForegroundVisibility()

            if (state == "UNLOCKED" || !shouldShowBackground) {
                mWallpaperBackground.visibility = View.GONE
            }
        }

        mPreviousState = state
    }

    private fun getWallpaperFlag(canvasEngine: Any): Int {
        return canvasEngine.callMethod("getWallpaperFlags") as Int
    }

    private fun invalidateCache() { // invalidate lock screen wallpaper subject cache
        mWallpaperForegroundCacheValid = false

        if (mLayersCreated) {
            mWallpaperForeground.post {
                shouldShowForeground = false
                mWallpaperForeground.background = null
                mWallpaperBackground.visibility = View.GONE
                mWallpaperBitmapContainer.background = null
                updateForegroundVisibility()
            }
        }

        try {
            if (DEPTH_WALL_FG_FILE.exists()) {
                DEPTH_WALL_FG_FILE.delete()
            }
        } catch (_: Throwable) {
        }
    }

    /*
     * Custom depth wallpaper images
     */
    private fun setCustomDepthWallpaper() {
        if (!showDepthWallpaper || !showCustomImages) return

        if (!mLayersCreated) {
            createLayers()
        }

        BootCallback.registerBootListener(
            object : BootCallback.BootListener {
                override fun onDeviceBooted() {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            if (DEPTH_WALL_BG_FILE.exists()) {
                                FileInputStream(DEPTH_WALL_BG_FILE).use { inputStream ->
                                    val bitmapDrawable = BitmapDrawable.createFromStream(
                                        inputStream,
                                        ""
                                    )!!.apply {
                                        alpha = 255
                                    }

                                    mWallpaperBackground.post {
                                        mWallpaperBitmapContainer.background = bitmapDrawable

                                        if (mScrimController != null) {
                                            mWallpaperDimmingOverlay.setBackgroundColor(Color.BLACK)
                                            mWallpaperDimmingOverlay.alpha =
                                                mScrimController.getField(
                                                    "mScrimBehindAlphaKeyguard"
                                                ) as Float
                                        }
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                        }

                        // this sets the dimmed foreground wallpaper
                        setDepthWallpaper()
                    }
                }
            }
        )
    }
}