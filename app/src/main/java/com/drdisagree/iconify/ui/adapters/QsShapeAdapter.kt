package com.drdisagree.iconify.ui.adapters

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.config.RPrefs.getBoolean
import com.drdisagree.iconify.ui.dialogs.LoadingDialog
import com.drdisagree.iconify.data.models.QsShapeModel
import com.drdisagree.iconify.ui.utils.ViewBindingHelpers.setDrawable
import com.drdisagree.iconify.utils.SystemUtils
import com.drdisagree.iconify.utils.overlay.manager.QsShapeManager
import com.drdisagree.iconify.utils.overlay.manager.QsShapePixelManager
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.toPx

class QsShapeAdapter(
    var context: Context,
    private var itemList: ArrayList<QsShapeModel>,
    var loadingDialog: LoadingDialog,
    var variant: String
) : RecyclerView.Adapter<QsShapeAdapter.ViewHolder>() {

    private var qsShapeKeys = ArrayList<String>()
    private var linearLayoutManager: LinearLayoutManager? = null
    private var selectedItem = -1

    init {
        // Preference key
        for (i in 1..itemList.size) qsShapeKeys.add("IconifyComponent$variant$i.overlay")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(
            if (variant == "QSSN") R.layout.view_list_option_qsshape else R.layout.view_list_option_qsshape_pixel,
            parent,
            false
        )
        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.styleName.text = itemList[position].name

        holder.qsTile1.setDrawable(
            ResourcesCompat.getDrawable(
                context.resources,
                itemList[position].enabledDrawable,
                null
            )
        )
        holder.qsTile2.setDrawable(
            ResourcesCompat.getDrawable(
                context.resources,
                itemList[position].disabledDrawable,
                null
            )
        )
        holder.qsTile3.setDrawable(
            ResourcesCompat.getDrawable(
                context.resources,
                itemList[position].disabledDrawable,
                null
            )
        )
        holder.qsTile4.setDrawable(
            ResourcesCompat.getDrawable(
                context.resources,
                itemList[position].enabledDrawable,
                null
            )
        )

        val textColor: Int = if (variant == "QSSN") {
            if (itemList[position].inverseColor) {
                R.color.textColorPrimary
            } else {
                R.color.textColorPrimaryInverse
            }
        } else {
            if (itemList[position].inverseColor && SystemUtils.isDarkMode) {
                R.color.textColorPrimary
            } else {
                R.color.textColorPrimaryInverse
            }
        }

        holder.qsText1.setTextColor(ContextCompat.getColor(context, textColor))
        holder.qsIcon1.setColorFilter(
            ContextCompat.getColor(context, textColor),
            PorterDuff.Mode.SRC_IN
        )

        holder.qsText4.setTextColor(ContextCompat.getColor(context, textColor))
        holder.qsIcon4.setColorFilter(
            ContextCompat.getColor(context, textColor),
            PorterDuff.Mode.SRC_IN
        )

        if (itemList[position].iconMarginStart != null && itemList[position].iconMarginEnd != null) {
            setMargin(
                holder,
                itemList[position].iconMarginStart!!,
                itemList[position].iconMarginEnd!!
            )
        } else {
            setMargin(holder, 0, 10)
        }

        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            holder.orientation.orientation = LinearLayout.VERTICAL
        } else {
            holder.orientation.orientation = LinearLayout.HORIZONTAL
        }

        refreshButton(holder)
        enableOnClickListener(holder)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)

        itemSelected(holder.container, getBoolean(qsShapeKeys[holder.getBindingAdapterPosition()]))
        refreshButton(holder)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        linearLayoutManager = recyclerView.layoutManager as LinearLayoutManager?
    }

    // Function for onClick events
    private fun enableOnClickListener(holder: ViewHolder) {
        // Set onClick operation for each item
        holder.container.setOnClickListener {
            selectedItem =
                if (selectedItem == holder.getBindingAdapterPosition()) {
                    -1
                } else {
                    holder.getBindingAdapterPosition()
                }

            refreshLayout(holder)

            if (!getBoolean(qsShapeKeys[holder.getBindingAdapterPosition()])) {
                holder.btnDisable.visibility = View.GONE

                if (holder.btnEnable.visibility == View.VISIBLE) {
                    holder.btnEnable.visibility =
                        View.GONE
                } else {
                    holder.btnEnable.visibility = View.VISIBLE
                }
            } else {
                holder.btnEnable.visibility = View.GONE

                if (holder.btnDisable.visibility == View.VISIBLE) {
                    holder.btnDisable.visibility =
                        View.GONE
                } else {
                    holder.btnDisable.visibility = View.VISIBLE
                }
            }
        }

        // Set onClick operation for Enable button
        holder.btnEnable.setOnClickListener {
            // Show loading dialog
            loadingDialog.show(context.resources.getString(R.string.loading_dialog_wait))

            Thread {
                if (variant == "QSSN") {
                    QsShapeManager.enableOverlay(holder.getBindingAdapterPosition() + 1)
                } else if (variant == "QSSP") {
                    QsShapePixelManager.enableOverlay(
                        holder.getBindingAdapterPosition() + 1
                    )
                }

                (context as Activity).runOnUiThread {
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Hide loading dialog
                        loadingDialog.hide()

                        // Change button visibility
                        holder.btnEnable.visibility = View.GONE
                        holder.btnDisable.visibility = View.VISIBLE

                        refreshBackground(holder)

                        Toast.makeText(
                            appContext,
                            context.resources.getString(R.string.toast_applied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }, 1000)
                }
            }.start()
        }

        // Set onClick operation for Disable button
        holder.btnDisable.setOnClickListener {
            // Show loading dialog
            loadingDialog.show(context.resources.getString(R.string.loading_dialog_wait))

            Thread {
                if (variant == "QSSN") {
                    QsShapeManager.disableOverlay(holder.getBindingAdapterPosition() + 1)
                } else if (variant == "QSSP") {
                    QsShapePixelManager.disableOverlay(
                        holder.getBindingAdapterPosition() + 1
                    )
                }

                (context as Activity).runOnUiThread {
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Hide loading dialog
                        loadingDialog.hide()

                        // Change button visibility
                        holder.btnDisable.visibility = View.GONE
                        holder.btnEnable.visibility = View.VISIBLE

                        refreshBackground(holder)

                        Toast.makeText(
                            appContext,
                            context.resources.getString(R.string.toast_disabled),
                            Toast.LENGTH_SHORT
                        ).show()
                    }, 1000)
                }
            }.start()
        }
    }

    // Function to check for layout changes
    private fun refreshLayout(holder: ViewHolder) {
        val firstVisible = linearLayoutManager!!.findFirstVisibleItemPosition()
        val lastVisible = linearLayoutManager!!.findLastVisibleItemPosition()

        for (i in firstVisible..lastVisible) {
            val view = linearLayoutManager!!.findViewByPosition(i)

            if (view != null) {
                val child = view.findViewById<LinearLayout>(R.id.qsshape_child)

                if (view !== holder.container && child != null) {
                    child.findViewById<View>(R.id.enable_qsshape).visibility = View.GONE
                    child.findViewById<View>(R.id.disable_qsshape).visibility = View.GONE
                }
            }
        }
    }

    // Function to check for applied options
    private fun refreshBackground(holder: ViewHolder) {
        val firstVisible = linearLayoutManager!!.findFirstVisibleItemPosition()
        val lastVisible = linearLayoutManager!!.findLastVisibleItemPosition()

        for (i in firstVisible..lastVisible) {
            val view = linearLayoutManager!!.findViewByPosition(i)

            if (view != null) {
                val child = view.findViewById<LinearLayout>(R.id.qsshape_child)

                if (child != null) {
                    itemSelected(
                        child, i == holder.getAbsoluteAdapterPosition() && getBoolean(
                            qsShapeKeys[i - (holder.getAbsoluteAdapterPosition() - holder.getBindingAdapterPosition())]
                        )
                    )
                }
            }
        }
    }

    private fun refreshButton(holder: ViewHolder) {
        if (holder.getBindingAdapterPosition() != selectedItem) {
            holder.btnEnable.visibility = View.GONE
            holder.btnDisable.visibility = View.GONE
        } else {
            if (getBoolean(qsShapeKeys[selectedItem])) {
                holder.btnEnable.visibility = View.GONE
                holder.btnDisable.visibility = View.VISIBLE
            } else {
                holder.btnEnable.visibility = View.VISIBLE
                holder.btnDisable.visibility = View.GONE
            }
        }
    }

    private fun setMargin(holder: ViewHolder, iconMarginLeft: Int, iconMarginRight: Int) {
        var marginParams = MarginLayoutParams(holder.qsIcon1.layoutParams)
        marginParams.setMarginStart(context.toPx(iconMarginLeft))
        marginParams.setMarginEnd(context.toPx(iconMarginRight))
        var layoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(marginParams)
        holder.qsIcon1.setLayoutParams(layoutParams)

        marginParams = MarginLayoutParams(holder.qsIcon2.layoutParams)
        marginParams.setMarginStart(context.toPx(iconMarginLeft))
        marginParams.setMarginEnd(context.toPx(iconMarginRight))
        layoutParams = LinearLayout.LayoutParams(marginParams)
        holder.qsIcon2.setLayoutParams(layoutParams)

        marginParams = MarginLayoutParams(holder.qsIcon3.layoutParams)
        marginParams.setMarginStart(context.toPx(iconMarginLeft))
        marginParams.setMarginEnd(context.toPx(iconMarginRight))
        layoutParams = LinearLayout.LayoutParams(marginParams)
        holder.qsIcon3.setLayoutParams(layoutParams)

        marginParams = MarginLayoutParams(holder.qsIcon4.layoutParams)
        marginParams.setMarginStart(context.toPx(iconMarginLeft))
        marginParams.setMarginEnd(context.toPx(iconMarginRight))
        layoutParams = LinearLayout.LayoutParams(marginParams)
        holder.qsIcon4.setLayoutParams(layoutParams)
    }

    private fun itemSelected(parent: View, state: Boolean) {
        if (state) {
            parent.background = ContextCompat.getDrawable(context, R.drawable.container_selected)
            (parent.findViewById<View>(R.id.list_title_qsshape) as TextView).setTextColor(
                ContextCompat.getColor(
                    context, R.color.colorAccent
                )
            )
            parent.findViewById<View>(R.id.icon_selected).visibility = View.VISIBLE
        } else {
            parent.background =
                ContextCompat.getDrawable(context, R.drawable.item_background_material)
            (parent.findViewById<View>(R.id.list_title_qsshape) as TextView).setTextColor(
                ContextCompat.getColor(
                    context, R.color.text_color_primary
                )
            )
            parent.findViewById<View>(R.id.icon_selected).visibility = View.INVISIBLE
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var container: LinearLayout = itemView.findViewById(R.id.qsshape_child)
        var orientation: LinearLayout = itemView.findViewById(R.id.qs_tile_orientation)
        var styleName: TextView = itemView.findViewById(R.id.list_title_qsshape)
        var btnEnable: Button = itemView.findViewById(R.id.enable_qsshape)
        var btnDisable: Button = itemView.findViewById(R.id.disable_qsshape)
        var qsTile1: LinearLayout = itemView.findViewById(R.id.qs_tile1)
        var qsTile2: LinearLayout = itemView.findViewById(R.id.qs_tile2)
        var qsTile3: LinearLayout = itemView.findViewById(R.id.qs_tile3)
        var qsTile4: LinearLayout = itemView.findViewById(R.id.qs_tile4)
        var qsIcon1: ImageView = itemView.findViewById(R.id.qs_icon1)
        var qsIcon2: ImageView = itemView.findViewById(R.id.qs_icon2)
        var qsIcon3: ImageView = itemView.findViewById(R.id.qs_icon3)
        var qsIcon4: ImageView = itemView.findViewById(R.id.qs_icon4)
        var qsText1: TextView = itemView.findViewById(R.id.qs_text1)
        var qsText2: TextView = itemView.findViewById(R.id.qs_text2)
        var qsText3: TextView = itemView.findViewById(R.id.qs_text3)
        var qsText4: TextView = itemView.findViewById(R.id.qs_text4)
    }
}
