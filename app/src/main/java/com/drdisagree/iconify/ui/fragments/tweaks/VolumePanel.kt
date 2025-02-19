package com.drdisagree.iconify.ui.fragments.tweaks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.VOLUME_PANEL_BACKGROUND_WIDTH
import com.drdisagree.iconify.config.RPrefs
import com.drdisagree.iconify.databinding.FragmentVolumePanelBinding
import com.drdisagree.iconify.ui.base.BaseFragment
import com.drdisagree.iconify.ui.utils.ViewHelper.setHeader
import com.drdisagree.iconify.utils.SystemUtils.hasStoragePermission
import com.drdisagree.iconify.utils.SystemUtils.requestStoragePermission
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceEntry
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceManager.buildOverlayWithResource
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceManager.removeResourceFromOverlay
import com.google.android.material.button.MaterialButton

class VolumePanel : BaseFragment() {

    private lateinit var binding: FragmentVolumePanelBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVolumePanelBinding.inflate(inflater, container, false)
        val view: View = binding.getRoot()

        // Header
        setHeader(
            requireContext(),
            getParentFragmentManager(),
            binding.header.toolbar,
            R.string.activity_title_volume_panel
        )

        binding.thinBg.isChecked = RPrefs.getInt(VOLUME_PANEL_BACKGROUND_WIDTH, 0) == 1
        binding.thinBg.addOnCheckedChangeListener { button: MaterialButton, isChecked: Boolean ->
            if (button.isPressed) {
                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.toggleButtonGroup.uncheck(binding.thinBg.id)
                } else {
                    if (isChecked) {
                        binding.toggleButtonGroup.uncheck(binding.thickBg.id)
                        binding.toggleButtonGroup.uncheck(binding.noBg.id)

                        RPrefs.putInt(VOLUME_PANEL_BACKGROUND_WIDTH, 1)

                        buildOverlayWithResource(
                            requireContext(),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "volume_dialog_slider_width",
                                "42dp"
                            ),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "volume_dialog_track_width",
                                "4dp"
                            ),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "rounded_slider_track_inset",
                                "22dp"
                            )
                        )
                    } else {
                        RPrefs.putInt(VOLUME_PANEL_BACKGROUND_WIDTH, 0)

                        removeResourceFromOverlay(
                            requireContext(),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "volume_dialog_slider_width"),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "volume_dialog_track_width"),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "rounded_slider_track_inset")
                        )
                    }
                }
            }
        }

        binding.thickBg.isChecked = RPrefs.getInt(VOLUME_PANEL_BACKGROUND_WIDTH, 0) == 2
        binding.thickBg.addOnCheckedChangeListener { button: MaterialButton, isChecked: Boolean ->
            if (button.isPressed) {
                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.toggleButtonGroup.uncheck(binding.thickBg.id)
                } else {
                    if (isChecked) {
                        binding.toggleButtonGroup.uncheck(binding.thinBg.id)
                        binding.toggleButtonGroup.uncheck(binding.noBg.id)

                        RPrefs.putInt(VOLUME_PANEL_BACKGROUND_WIDTH, 2)

                        buildOverlayWithResource(
                            requireContext(),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "volume_dialog_slider_width",
                                "42dp"
                            ),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "volume_dialog_track_width",
                                "42dp"
                            ),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "rounded_slider_track_inset",
                                "0dp"
                            )
                        )
                    } else {
                        RPrefs.putInt(VOLUME_PANEL_BACKGROUND_WIDTH, 0)

                        removeResourceFromOverlay(
                            requireContext(),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "volume_dialog_slider_width"),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "volume_dialog_track_width"),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "rounded_slider_track_inset")
                        )
                    }
                }
            }
        }

        binding.noBg.isChecked = RPrefs.getInt(VOLUME_PANEL_BACKGROUND_WIDTH, 0) == 3
        binding.noBg.addOnCheckedChangeListener { button: MaterialButton, isChecked: Boolean ->
            if (button.isPressed) {
                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.toggleButtonGroup.uncheck(binding.noBg.id)
                } else {
                    if (isChecked) {
                        binding.toggleButtonGroup.uncheck(binding.thinBg.id)
                        binding.toggleButtonGroup.uncheck(binding.thickBg.id)

                        RPrefs.putInt(VOLUME_PANEL_BACKGROUND_WIDTH, 3)

                        buildOverlayWithResource(
                            requireContext(),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "volume_dialog_slider_width",
                                "42dp"
                            ),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "volume_dialog_track_width",
                                "0dp"
                            ),
                            ResourceEntry(
                                SYSTEMUI_PACKAGE,
                                "dimen",
                                "rounded_slider_track_inset",
                                "24dp"
                            )
                        )
                    } else {
                        RPrefs.putInt(VOLUME_PANEL_BACKGROUND_WIDTH, 0)

                        removeResourceFromOverlay(
                            requireContext(),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "volume_dialog_slider_width"),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "volume_dialog_track_width"),
                            ResourceEntry(SYSTEMUI_PACKAGE, "dimen", "rounded_slider_track_inset")
                        )
                    }
                }
            }
        }

        return view
    }
}