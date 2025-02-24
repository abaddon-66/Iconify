package com.drdisagree.iconify.xposed.modules.extras.utils

import com.drdisagree.iconify.data.common.References.FABRICATED_SB_COLOR_SOURCE
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs

val isQsTileOverlayEnabled: Boolean
    get() {
        for (i in 0..25) {
            if (Xprefs.getBoolean("IconifyComponentQSSN$i.overlay") ||
                Xprefs.getBoolean("IconifyComponentQSSP$i.overlay")
            ) {
                return true
            }
        }
        return false
    }

val isPixelVariant: Boolean
    get() {
        for (i in 0..25) {
            if (Xprefs.getBoolean("IconifyComponentQSSP$i.overlay")) {
                return true
            }
        }
        return false
    }

val coloredStatusbarOverlayEnabled: Boolean
    get() = Xprefs.getBoolean("IconifyComponentSBTint.overlay") ||
            Xprefs.getString(FABRICATED_SB_COLOR_SOURCE, "System") == "System"