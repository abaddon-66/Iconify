package com.drdisagree.iconify.utils.helper

import com.drdisagree.iconify.common.Resources.BACKUP_DIR
import com.drdisagree.iconify.common.Resources.MODULE_DIR
import com.drdisagree.iconify.common.Resources.OVERLAY_DIR
import com.drdisagree.iconify.common.Resources.TEMP_MODULE_DIR
import com.drdisagree.iconify.common.Resources.TEMP_MODULE_OVERLAY_DIR
import com.drdisagree.iconify.utils.RootUtils
import com.topjohnwu.superuser.Shell

object BackupRestore {

    fun backupFiles() {
        // Create backup directory
        Shell.cmd("rm -rf $BACKUP_DIR", "mkdir -p $BACKUP_DIR").exec()

        backupFile("$MODULE_DIR/system.prop")
        backupFile("$MODULE_DIR/post-exec.sh")
        backupFile("$OVERLAY_DIR/IconifyComponentCR1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentCR2.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIS.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIP1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIP2.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIP3.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentPGB.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSWITCH1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSWITCH2.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentDynamic1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentDynamic2.apk")
    }

    fun restoreFiles() {
        restoreFile("system.prop", TEMP_MODULE_DIR)
        restoreFile("post-exec.sh", TEMP_MODULE_DIR)
        restoreFile("IconifyComponentCR1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentCR2.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIS.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIP1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIP2.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIP3.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentPGB.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSWITCH1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSWITCH2.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentDynamic1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentDynamic2.apk", TEMP_MODULE_OVERLAY_DIR)

        restoreBlurSettings()

        // Remove backup directory
        Shell.cmd("rm -rf $BACKUP_DIR").exec()
    }

    private fun backupExists(fileName: String): Boolean {
        return RootUtils.fileExists("$BACKUP_DIR/$fileName")
    }

    private fun backupFile(source: String) {
        if (RootUtils.fileExists(source)) Shell.cmd("cp -rf $source $BACKUP_DIR/")
            .exec()
    }

    private fun restoreFile(fileName: String, dest: String) {
        if (backupExists(fileName)) {
            Shell.cmd("rm -rf $dest/$fileName").exec()
            Shell.cmd("cp -rf $BACKUP_DIR/$fileName $dest/").exec()
        }
    }

    private fun restoreBlurSettings() {
        if (isBlurEnabled) {
            enableBlur()
        }
    }

    private val isBlurEnabled: Boolean
        get() {
            val outs =
                Shell.cmd("if grep -q \"ro.surface_flinger.supports_background_blur=1\" $TEMP_MODULE_DIR/system.prop; then echo yes; else echo no; fi")
                    .exec().out
            return outs[0] == "yes"
        }

    private fun disableBlur() {
        Shell.cmd("mv $TEMP_MODULE_DIR/system.prop $TEMP_MODULE_DIR/system.txt; grep -v \"ro.surface_flinger.supports_background_blur\" $TEMP_MODULE_DIR/system.txt > $TEMP_MODULE_DIR/system.txt.tmp; rm -rf $TEMP_MODULE_DIR/system.prop; mv $TEMP_MODULE_DIR/system.txt.tmp $TEMP_MODULE_DIR/system.prop; rm -rf $TEMP_MODULE_DIR/system.txt; rm -rf $TEMP_MODULE_DIR/system.txt.tmp")
            .exec()
        Shell.cmd("grep -v \"ro.surface_flinger.supports_background_blur\" $TEMP_MODULE_DIR/service.sh > $TEMP_MODULE_DIR/service.sh.tmp && mv $TEMP_MODULE_DIR/service.sh.tmp $TEMP_MODULE_DIR/service.sh")
            .exec()
    }

    private fun enableBlur() {
        disableBlur()

        val blurCmd1 = "ro.surface_flinger.supports_background_blur=1"
        val blurCmd2 =
            "resetprop ro.surface_flinger.supports_background_blur 1 && killall surfaceflinger"

        Shell.cmd("echo \"$blurCmd1\" >> $TEMP_MODULE_DIR/system.prop")
            .exec()
        Shell.cmd("sed '/*}/a $blurCmd2' $TEMP_MODULE_DIR/service.sh > $TEMP_MODULE_DIR/service.sh.tmp && mv $TEMP_MODULE_DIR/service.sh.tmp $TEMP_MODULE_DIR/service.sh")
            .exec()
    }
}
