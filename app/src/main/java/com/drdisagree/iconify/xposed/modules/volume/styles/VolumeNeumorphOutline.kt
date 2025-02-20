package com.drdisagree.iconify.xposed.modules.volume.styles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.TypedValue
import android.view.Gravity
import androidx.core.content.ContextCompat
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.toPx

@SuppressLint("DiscouragedApi", "RtlHardcoded")
class VolumeNeumorphOutline(
    private val mContext: Context,
    private val roundedCornerProgressDrawable: Class<*>,
    private val alphaTintDrawableWrapper: Class<*>
) : VolumeStyleBase() {

    private val holoBlueLight: Int
        get() = mContext.resources.getColor(android.R.color.holo_blue_light, mContext.theme)

    private val holoBlueDark: Int
        get() = mContext.resources.getColor(android.R.color.holo_blue_dark, mContext.theme)

    private val holoRedLight: Int
        get() = mContext.resources.getColor(android.R.color.holo_red_light, mContext.theme)

    private val holoRedDark: Int
        get() = mContext.resources.getColor(android.R.color.holo_red_dark, mContext.theme)

    private fun getSysUiDimen(name: String): Int {
        val resId = mContext.resources.getIdentifier(name, "dimen", SYSTEMUI_PACKAGE)
        return if (resId > 0) mContext.resources.getDimensionPixelSize(resId) else 0
    }

    override fun createVolumeDrawerSelectionBgDrawable(): Drawable {
        val itemSize = getSysUiDimen("volume_ringer_drawer_item_size")
        val itemSizeHalf = getSysUiDimen("volume_ringer_drawer_item_size_half").toFloat()
        val itemSizeHalfInner1 = itemSizeHalf - mContext.toPx(2)
        val itemSizeHalfInner2 = itemSizeHalf - mContext.toPx(4)

        val innerLayerDrawable = LayerDrawable(
            arrayOf(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(holoBlueLight, holoBlueDark)
                ).apply {
                    cornerRadius = itemSizeHalf
                },
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(holoRedLight, holoRedDark)
                ).apply {
                    cornerRadius = itemSizeHalfInner1
                },
                GradientDrawable(
                    GradientDrawable.Orientation.RIGHT_LEFT,
                    intArrayOf(holoRedLight, holoRedDark)
                ).apply {
                    cornerRadius = itemSizeHalfInner2
                }
            )
        ).apply {
            val itemInset1 = mContext.toPx(2)
            val itemInset2 = mContext.toPx(4)
            setLayerInset(1, itemInset1, itemInset1, itemInset1, itemInset1)
            setLayerInset(2, itemInset2, itemInset2, itemInset2, itemInset2)
            setLayerGravity(1, Gravity.FILL_HORIZONTAL or Gravity.CENTER)
            setLayerGravity(2, Gravity.FILL_HORIZONTAL or Gravity.CENTER)
        }

        return LayerDrawable(arrayOf(innerLayerDrawable)).apply {
            paddingMode = LayerDrawable.PADDING_MODE_STACK
            setLayerSize(0, itemSize, itemSize)
        }
    }

    override fun createVolumeRowSeekbarDrawable(): Drawable {
        val trackHeight = getSysUiDimen("volume_dialog_track_width")
        val cornerRadius = getSysUiDimen("volume_dialog_slider_corner_radius").toFloat()

        val backgroundColor = TypedValue().apply {
            mContext.theme.resolveAttribute(android.R.attr.colorBackground, this, true)
        }.data

        val insetBackground = InsetDrawable(
            ShapeDrawable().apply {
                paint.color = backgroundColor
                shape = RoundRectShape(FloatArray(8) { cornerRadius }, null, null)
                intrinsicHeight = trackHeight
            },
            0
        )

        val insetProgressDrawable = roundedCornerProgressDrawable
            .getConstructor(Drawable::class.java)
            .newInstance(createVolumeRowSeekbarProgressDrawable()) as InsetDrawable

        return LayerDrawable(
            arrayOf(
                insetBackground,
                insetProgressDrawable
            )
        ).apply {
            paddingMode = LayerDrawable.PADDING_MODE_STACK
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            setLayerGravity(0, Gravity.FILL_HORIZONTAL or Gravity.CENTER)
            setLayerGravity(1, Gravity.FILL_HORIZONTAL or Gravity.CENTER)
        }
    }

    override fun createVolumeRowSeekbarProgressDrawable(): Drawable {
        val sliderWidth = getSysUiDimen("volume_dialog_slider_width")
        val sliderCornerRadius = getSysUiDimen("volume_dialog_slider_corner_radius").toFloat()
        val iconSize = getSysUiDimen("rounded_slider_icon_size")
        val iconInset = getSysUiDimen("volume_slider_icon_inset")
        val sliderCornerRadiusInner1 = sliderCornerRadius - mContext.toPx(2)
        val sliderCornerRadiusInner2 = sliderCornerRadius - mContext.toPx(4)

        val transparentShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setSize(0, sliderWidth)
            cornerRadius = sliderCornerRadius
            setColor(Color.TRANSPARENT)
        }

        val middleLayerDrawable = LayerDrawable(
            arrayOf(
                GradientDrawable(
                    GradientDrawable.Orientation.RIGHT_LEFT,
                    intArrayOf(holoBlueLight, holoBlueDark)
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = sliderCornerRadius
                    setSize(0, sliderWidth)
                },
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(holoRedLight, holoRedDark)
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = sliderCornerRadiusInner1
                    setSize(0, sliderWidth - mContext.toPx(4))
                },
                GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(holoRedLight, holoRedDark)
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = sliderCornerRadiusInner2
                    setSize(0, sliderWidth - mContext.toPx(8))
                }
            )
        ).apply {
            val itemInset1 = mContext.toPx(2)
            val itemInset2 = mContext.toPx(4)
            setLayerInset(1, itemInset1, itemInset1, itemInset1, itemInset1)
            setLayerInset(2, itemInset2, itemInset2, itemInset2, itemInset2)
            setLayerGravity(1, Gravity.FILL_HORIZONTAL or Gravity.CENTER)
            setLayerGravity(2, Gravity.FILL_HORIZONTAL or Gravity.CENTER)
        }

        val iconDrawable = RotateDrawable().apply {
            fromDegrees = -270f
            toDegrees = -270f
            drawable = alphaTintDrawableWrapper
                .getConstructor(Drawable::class.java, IntArray::class.java)
                .newInstance(
                    ContextCompat.getDrawable(
                        mContext,
                        mContext.resources.getIdentifier(
                            "ic_volume_media",
                            "drawable",
                            SYSTEMUI_PACKAGE
                        )
                    ),
                    intArrayOf(android.R.attr.textColorPrimaryInverse)
                ) as InsetDrawable
        }

        return LayerDrawable(
            arrayOf(
                transparentShape,
                middleLayerDrawable,
                iconDrawable
            )
        ).apply {
            isAutoMirrored = true
            setId(
                0,
                mContext.resources.getIdentifier(
                    "volume_seekbar_progress_solid",
                    "id",
                    SYSTEMUI_PACKAGE
                )
            )
            setId(
                2,
                mContext.resources.getIdentifier(
                    "volume_seekbar_progress_icon",
                    "id",
                    SYSTEMUI_PACKAGE
                )
            )
            setLayerInset(2, 0, 0, iconInset, 0)
            setLayerSize(2, iconSize, iconSize)
            setLayerGravity(2, Gravity.CENTER or Gravity.RIGHT)
        }
    }
}