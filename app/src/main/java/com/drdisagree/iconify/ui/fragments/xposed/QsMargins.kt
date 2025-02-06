package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Preferences.CUSTOM_QS_MARGIN
import com.drdisagree.iconify.common.Preferences.QQS_TOPMARGIN_LANDSCAPE
import com.drdisagree.iconify.common.Preferences.QQS_TOPMARGIN_PORTRAIT
import com.drdisagree.iconify.common.Preferences.QS_TOPMARGIN_LANDSCAPE
import com.drdisagree.iconify.common.Preferences.QS_TOPMARGIN_PORTRAIT
import com.drdisagree.iconify.ui.activities.MainActivity
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat

class QsMargins : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.custom_qs_margin_title)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_qs_margins

    override val hasMenu: Boolean
        get() = true

    override fun updateScreen(key: String?) {
        super.updateScreen(key)

        when (key) {
            CUSTOM_QS_MARGIN,
            QQS_TOPMARGIN_PORTRAIT,
            QS_TOPMARGIN_PORTRAIT,
            QQS_TOPMARGIN_LANDSCAPE,
            QS_TOPMARGIN_LANDSCAPE -> {
                MainActivity.showOrHidePendingActionButton(
                    activityBinding = (requireActivity() as MainActivity).binding,
                    requiresSystemUiRestart = true
                )
            }
        }
    }
}