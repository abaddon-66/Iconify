package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat

class Launcher : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.activity_title_xposed_launcher)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_launcher

    override val hasMenu: Boolean
        get() = true
}
