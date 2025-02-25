package com.drdisagree.iconify.utils.overlay.compiler

import android.util.Log
import com.drdisagree.iconify.data.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.data.common.Const.LAUNCHER3_PACKAGE
import com.drdisagree.iconify.data.common.Const.PIXEL_LAUNCHER_PACKAGE
import com.drdisagree.iconify.data.common.Const.SETTINGS_PACKAGE
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Resources.BACKUP_DIR
import com.drdisagree.iconify.data.common.Resources.DATA_DIR
import com.drdisagree.iconify.data.common.Resources.OVERLAY_DIR
import com.drdisagree.iconify.data.common.Resources.SIGNED_DIR
import com.drdisagree.iconify.data.common.Resources.SYSTEM_OVERLAY_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_CACHE_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_OVERLAY_DIR
import com.drdisagree.iconify.data.common.Resources.UNSIGNED_DIR
import com.drdisagree.iconify.data.common.Resources.UNSIGNED_UNALIGNED_DIR
import com.drdisagree.iconify.utils.FileUtils.copyAssets
import com.drdisagree.iconify.utils.RootUtils.setPermissions
import com.drdisagree.iconify.utils.SystemUtils.mountRO
import com.drdisagree.iconify.utils.SystemUtils.mountRW
import com.drdisagree.iconify.utils.helper.BinaryInstaller.symLinkBinaries
import com.drdisagree.iconify.utils.overlay.OverlayUtils.disableOverlays
import com.drdisagree.iconify.utils.overlay.OverlayUtils.enableOverlays
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceManager.ResourceType
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceManager.generateXmlStructureForAllResources
import com.topjohnwu.superuser.Shell
import java.io.IOException

object DynamicCompiler {

    private val TAG = DynamicCompiler::class.java.simpleName
    private var mForce = false
    private var mPackage: String? = null
    private var mOverlayName: String? = null
    private val mResource: MutableMap<ResourceType, ArrayList<String>> = mutableMapOf()
    private val dynamicOverlayList = listOf(
        "IconifyComponentDynamic1.overlay",
        "IconifyComponentDynamic2.overlay",
        "IconifyComponentDynamic3.overlay",
        "IconifyComponentDynamic4.overlay",
        "IconifyComponentDynamic5.overlay"
    )

    @JvmOverloads
    @Throws(IOException::class)
    suspend fun buildDynamicOverlay(force: Boolean = true): Boolean {
        mForce = force

        try {
            Shell.cmd("mkdir -p $BACKUP_DIR").exec()

            val resourcesMap = generateXmlStructureForAllResources()

            // Create overlay for each package
            for (packageName in resourcesMap.keys) {
                mPackage = packageName

                mResource.clear()
                mResource[ResourceType.PORTRAIT] =
                    ArrayList(resourcesMap[packageName]!![ResourceType.PORTRAIT]!!)
                resourcesMap[packageName]!![ResourceType.LANDSCAPE]?.let {
                    mResource[ResourceType.LANDSCAPE] = ArrayList(it)
                }
                resourcesMap[packageName]!![ResourceType.NIGHT]?.let {
                    mResource[ResourceType.NIGHT] = ArrayList(it)
                }

                mOverlayName = when (mPackage) {
                    FRAMEWORK_PACKAGE -> "Dynamic1"
                    SYSTEMUI_PACKAGE -> "Dynamic2"
                    PIXEL_LAUNCHER_PACKAGE -> "Dynamic3"
                    LAUNCHER3_PACKAGE -> "Dynamic4"
                    SETTINGS_PACKAGE -> "Dynamic5"
                    else -> throw Exception("Unknown package: $mPackage")
                }

                preExecute()
                moveOverlaysToCache()

                // Create AndroidManifest.xml
                if (createManifestResource(
                        mOverlayName,
                        mPackage,
                        "$TEMP_CACHE_DIR/$mPackage/$mOverlayName"
                    )
                ) {
                    Log.e(TAG, "Failed to create Manifest for $mOverlayName! Exiting...")
                    postExecute(true)
                    return true
                }

                // Build APK using AAPT
                if (OverlayCompiler.runAapt(
                        "$TEMP_CACHE_DIR/$mPackage/$mOverlayName",
                        mPackage
                    )
                ) {
                    Log.e(TAG, "Failed to build $mOverlayName! Exiting...")
                    postExecute(true)
                    return true
                }

                // ZipAlign the APK
                if (OverlayCompiler.zipAlign("$UNSIGNED_UNALIGNED_DIR/$mOverlayName-unsigned-unaligned.apk")) {
                    Log.e(
                        TAG,
                        "Failed to align $mOverlayName-unsigned-unaligned.apk! Exiting..."
                    )
                    postExecute(true)
                    return true
                }

                // Sign the APK
                if (OverlayCompiler.apkSigner("$UNSIGNED_DIR/$mOverlayName-unsigned.apk")) {
                    Log.e(TAG, "Failed to sign $mOverlayName-unsigned.apk! Exiting...")
                    postExecute(true)
                    return true
                }
                postExecute(false)
            }

            if (mForce) {
                Shell.cmd("rm -rf $BACKUP_DIR").exec()

                // Disable the overlays in case they are already enabled
                disableOverlays(*dynamicOverlayList.toTypedArray())

                // Install from files dir
                dynamicOverlayList.forEach { overlay ->
                    val apkName = overlay.replace(".overlay", ".apk")

                    Shell.cmd("pm install -r $DATA_DIR/$apkName").exec()
                    Shell.cmd("rm -rf $DATA_DIR/$apkName").exec()
                }

                // Move to system overlay dir
                mountRW()
                dynamicOverlayList.forEach { overlay ->
                    val apkName = overlay.replace(".overlay", ".apk")

                    Shell.cmd("cp -rf $SIGNED_DIR/$apkName $SYSTEM_OVERLAY_DIR/$apkName").exec()
                    setPermissions(644, "/system/product/overlay/$apkName")
                }
                mountRO()

                // Enable the overlays
                enableOverlays(*dynamicOverlayList.toTypedArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build overlay! Exiting...", e)
            postExecute(true)
            return true
        }

        return false
    }

    @Throws(IOException::class)
    private fun preExecute() {
        // Create symbolic link
        symLinkBinaries()

        // Clean data directory
        Shell.cmd("rm -rf $TEMP_DIR").exec()
        Shell.cmd("rm -rf $DATA_DIR/Overlays").exec()

        // Extract overlay from assets
        copyAssets("Overlays/$mPackage/$mOverlayName")

        // Create temp directory
        Shell.cmd("rm -rf $TEMP_DIR; mkdir -p $TEMP_DIR").exec()
        Shell.cmd("mkdir -p $TEMP_OVERLAY_DIR").exec()
        Shell.cmd("mkdir -p $TEMP_CACHE_DIR").exec()
        Shell.cmd("mkdir -p $UNSIGNED_UNALIGNED_DIR").exec()
        Shell.cmd("mkdir -p $UNSIGNED_DIR").exec()
        Shell.cmd("mkdir -p $SIGNED_DIR").exec()
        Shell.cmd("mkdir -p $TEMP_CACHE_DIR/$mPackage/").exec()
        Shell.cmd("mkdir -p $BACKUP_DIR").exec()
    }

    private fun postExecute(hasErroredOut: Boolean) {
        if (!hasErroredOut) {
            // Move all generated overlays to module
            Shell.cmd(
                "cp -rf $SIGNED_DIR/IconifyComponent$mOverlayName.apk $OVERLAY_DIR/IconifyComponent$mOverlayName.apk"
            ).exec()
            setPermissions(644, "$OVERLAY_DIR/IconifyComponent$mOverlayName.apk")
            Shell.cmd(
                "cp -rf $SIGNED_DIR/IconifyComponent$mOverlayName.apk $BACKUP_DIR/IconifyComponent$mOverlayName.apk"
            ).exec()

            // Move to files dir
            if (mForce) {
                Shell.cmd(
                    "cp -rf $SIGNED_DIR/IconifyComponent$mOverlayName.apk $DATA_DIR/IconifyComponent$mOverlayName.apk"
                ).exec()
                setPermissions(644, "$DATA_DIR/IconifyComponent$mOverlayName.apk")
            }
        }

        // Clean temp directory
        Shell.cmd("rm -rf $TEMP_DIR").exec()
        Shell.cmd("rm -rf $DATA_DIR/Overlays").exec()
    }

    private fun moveOverlaysToCache() {
        Shell.cmd(
            "mv -f \"$DATA_DIR/Overlays/$mPackage/$mOverlayName\" \"$TEMP_CACHE_DIR/$mPackage/$mOverlayName\""
        ).exec().isSuccess
    }

    private fun createManifestResource(
        overlayName: String?,
        targetPackage: String?,
        source: String
    ): Boolean {
        Shell.cmd("mkdir -p $source/res").exec()

        val values = arrayOf("values", "values-land", "values-night")

        for (i in 0..2) {
            Shell.cmd("mkdir -p " + source + "/res/" + values[i]).exec()

            val resourceType = when (i) {
                0 -> ResourceType.PORTRAIT
                1 -> ResourceType.LANDSCAPE
                2 -> ResourceType.NIGHT
                else -> throw Exception("Invalid resource type")
            }

            val filePath = "$source/res/${values[i]}/iconify.xml"
            val resourceList = mResource[resourceType]?.let { ArrayList(it) }

            if (!resourceList.isNullOrEmpty()) {
                Shell.cmd("rm -f $filePath; touch $filePath").exec()
                resourceList.forEach { line -> Shell.cmd("echo '$line\n' >> $filePath").exec() }
            }
        }

        return OverlayCompiler.createManifest(overlayName, targetPackage, source)
    }
}
