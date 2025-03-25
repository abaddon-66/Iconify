package com.drdisagree.iconify.ui.fragments.xposed

import android.os.Bundle
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Preferences.STATUSBAR_LOGO_STYLE
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat
import com.drdisagree.iconify.ui.preferences.BottomSheetListPreference
import com.drdisagree.iconify.ui.utils.ViewHelper.getStatusbarLogoDrawables

class StatusbarLogo : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.status_bar_logo_title)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_statusbar_logo

    override val hasMenu: Boolean
        get() = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        findPreference<BottomSheetListPreference>(STATUSBAR_LOGO_STYLE)?.apply {
            createDefaultAdapter(getStatusbarLogoDrawables(requireContext()))
        }
    }
}
