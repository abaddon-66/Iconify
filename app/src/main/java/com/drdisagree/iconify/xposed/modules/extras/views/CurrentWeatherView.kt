package com.drdisagree.iconify.xposed.modules.extras.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.R
import com.drdisagree.iconify.ui.utils.ViewHelper.applyTextSizeRecursively
import com.drdisagree.iconify.ui.utils.ViewHelper.setTextRecursively
import com.drdisagree.iconify.utils.OmniJawsClient
import com.drdisagree.iconify.xposed.HookRes.Companion.modRes
import com.drdisagree.iconify.xposed.modules.extras.callbacks.ThemeChangeCallback
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.findViewContainsTag
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.findViewWithTagAndChangeColor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class CurrentWeatherView(context: Context, name: String) : LinearLayout(context),
    OmniJawsClient.OmniJawsObserver {

    private var mCurrentImage: ImageView? = null
    private var mHumImage: ImageView? = null
    private var mWindImage: ImageView? = null
    private var mWeatherClient: OmniJawsClient?
    private var mWeatherInfo: OmniJawsClient.WeatherInfo? = null
    private var mLeftText: TextView? = null
    private var mRightText: TextView? = null
    private var mWeatherText: TextView? = null // Weather Layout
    private var mHumText: TextView? = null
    private var mWindText: TextView? = null
    private var mHumLayout: LinearLayout? = null
    private var mWindLayout: LinearLayout? = null
    private var mHumDrawable: Drawable? = null
    private var mWindDrawable: Drawable? = null
    private var mWeatherBgSelection = 0
    private var mShowWeatherLocation = false
    private var mShowWeatherText = false
    private var mShowWeatherHumidity = false
    private var mShowWeatherWind = false
    private var mWeatherHorPadding = 0
    private var mWeatherVerPadding = 0
    private val mContext: Context
    private var appContext: Context? = null

    private val mThemeChangeCallback: ThemeChangeCallback.OnThemeChangedListener =
        object : ThemeChangeCallback.OnThemeChangedListener {
            override fun onThemeChanged() {
                reloadWeatherBg()
            }
        }

    init {
        instances.add(arrayOf(this, name))
        mContext = context
        try {
            appContext = context.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (_: PackageManager.NameNotFoundException) {
        }
        mWeatherClient = OmniJawsClient(mContext)
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )

        inflateView()

        enableUpdates()

        ThemeChangeCallback.getInstance().registerThemeChangedCallback(mThemeChangeCallback)
    }

    private fun inflateView() {
        inflate(appContext, R.layout.view_current_weather, this)
        setupViews()
    }

    private fun setupViews() {
        mLeftText = findViewContainsTag("leftText") as TextView
        mCurrentImage = findViewContainsTag("currentImage") as ImageView
        mRightText = findViewContainsTag("rightText") as TextView
        mWeatherText = findViewContainsTag("weatherText") as TextView
        mHumLayout = findViewContainsTag("humLayout") as LinearLayout
        mHumImage = findViewContainsTag("humImage") as ImageView
        mHumText = findViewContainsTag("humText") as TextView
        mWindLayout = findViewContainsTag("windLayout") as LinearLayout
        mWindImage = findViewContainsTag("windImage") as ImageView
        mWindText = findViewContainsTag("windText") as TextView

        mWindDrawable = ContextCompat.getDrawable(
            appContext!!,
            R.drawable.ic_wind_symbol
        )

        mHumDrawable = ContextCompat.getDrawable(
            appContext!!,
            R.drawable.ic_humidity_symbol
        )
    }

    fun getName(): String {
        if (instances.isEmpty()) throw IllegalStateException("No instances found")

        val instance = instances.stream()
            .filter { obj: Array<Any> ->
                obj[0] == this
            }
            .findFirst()
            .map { obj: Array<Any> ->
                obj[1] as String
            }
            .orElse("")

        return if (instance.isEmpty()) throw IllegalStateException("No instance name found") else instance
    }

    fun updateSizes(weatherTextSize: Int, weatherImageSize: Int, name: String) {
        if (instances.isEmpty()) return

        updateIconsSize(weatherImageSize, name)

        instances
            .stream()
            .filter { obj: Array<Any> ->
                obj[1] == name
            }
            .forEach { obj: Array<Any> ->
                applyTextSizeRecursively(
                    obj[0] as CurrentWeatherView,
                    weatherTextSize
                )
            }
    }

    fun updateColors(color: Int, name: String) {
        if (instances.isEmpty()) return
        instances
            .stream()
            .filter { obj: Array<Any> ->
                obj[1] == name
            }
            .forEach { obj: Array<Any> ->
                val instance = obj[0] as CurrentWeatherView
                findViewWithTagAndChangeColor(instance, "text", color)
            }
    }

    private fun enableUpdates() {
        if (mWeatherClient != null) {
            mWeatherClient!!.addObserver(this)
            queryAndUpdateWeather()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setErrorView(errorReason: Int) {
        var reQuery = false
        val errorText = when (errorReason) {
            OmniJawsClient.EXTRA_ERROR_DISABLED -> modRes.getString(R.string.omnijaws_service_disabled)
            OmniJawsClient.EXTRA_ERROR_NO_PERMISSIONS -> modRes.getString(R.string.omnijaws_service_error_permissions)
            else -> ""
        }

        if (!TextUtils.isEmpty(errorText)) {
            mLeftText!!.text = errorText
        } else {
            reQuery = true
        }

        if (reQuery) {
            queryAndUpdateWeather()
        } else {
            setTextRecursively(this, "")
            mCurrentImage!!.setImageDrawable(null)
            mHumImage!!.setImageDrawable(null)
            mWindImage!!.setImageDrawable(null)
        }
    }

    override fun weatherError(errorReason: Int) {
        // Show only Disabled and Permission errors
        log(this@CurrentWeatherView, "weatherError: $errorReason")
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null
        }
        setErrorView(errorReason)
    }

    override fun weatherUpdated() {
        queryAndUpdateWeather()
    }

    override fun updateSettings() {
        queryAndUpdateWeather()
    }

    @SuppressLint("SetTextI18n")
    private fun queryAndUpdateWeather() {
        try {
            if (mWeatherClient == null || !mWeatherClient!!.isOmniJawsEnabled) {
                setErrorView(OmniJawsClient.EXTRA_ERROR_DISABLED)
                return
            }

            mWeatherClient!!.queryWeather()
            mWeatherInfo = mWeatherClient!!.mCachedInfo

            if (mWeatherInfo != null) {
                val formattedConditionLowercase =
                    mWeatherInfo!!.condition.toString().lowercase(Locale.getDefault())

                val formattedCondition = when {
                    formattedConditionLowercase.contains("clouds") -> {
                        modRes.getString(R.string.weather_condition_clouds)
                    }

                    formattedConditionLowercase.contains("rain") -> {
                        modRes.getString(R.string.weather_condition_rain)
                    }

                    formattedConditionLowercase.contains("clear") -> {
                        modRes.getString(R.string.weather_condition_clear)
                    }

                    formattedConditionLowercase.contains("storm") -> {
                        modRes.getString(R.string.weather_condition_storm)
                    }

                    formattedConditionLowercase.contains("snow") -> {
                        modRes.getString(R.string.weather_condition_snow)
                    }

                    formattedConditionLowercase.contains("wind") -> {
                        modRes.getString(R.string.weather_condition_wind)
                    }

                    formattedConditionLowercase.contains("mist") -> {
                        modRes.getString(R.string.weather_condition_mist)
                    }

                    else -> {
                        mWeatherInfo!!.condition.toString()
                    }
                }

                val drawable =
                    mWeatherClient!!.getWeatherConditionImage(mWeatherInfo!!.conditionCode)
                mCurrentImage!!.setImageDrawable(drawable)
                mRightText!!.text = mWeatherInfo!!.temp + mWeatherInfo!!.tempUnits
                mLeftText!!.text = mWeatherInfo!!.city
                mLeftText!!.visibility = if (mShowWeatherLocation) VISIBLE else GONE
                mWeatherText!!.text = " · $formattedCondition"
                mWeatherText!!.visibility = if (mShowWeatherText) VISIBLE else GONE

                mHumImage!!.setImageDrawable(mHumDrawable)
                mHumText!!.text = mWeatherInfo!!.humidity
                mHumLayout!!.visibility = if (mShowWeatherHumidity) VISIBLE else GONE

                mWindImage!!.setImageDrawable(mWindDrawable)
                mWindText!!.text =
                    ((mWeatherInfo!!.windDirection + " " + mWeatherInfo!!.pinWheel) + " · " + mWeatherInfo!!.windSpeed) + " " + mWeatherInfo!!.windUnits
                mWindLayout!!.visibility = if (mShowWeatherWind) VISIBLE else GONE
            }
        } catch (e: Exception) {
            log(this@CurrentWeatherView, "Weather query failed" + e.message)
            Log.e(TAG, "Weather query failed", e)
        }
    }

    fun updateWeatherBg(selection: Int, name: String) {
        if (instances.isEmpty()) return
        instances
            .stream()
            .filter { obj: Array<Any> ->
                obj[1] == name
            }
            .forEach { obj: Array<Any> ->
                val instance = obj[0] as CurrentWeatherView
                instance.mWeatherBgSelection = selection
                instance.updateWeatherBg()
            }
    }

    fun reloadWeatherBg() {
        if (instances.isEmpty()) return
        instances.forEach(Consumer { obj: Array<Any> ->
            val instance = obj[0] as CurrentWeatherView
            instance.updateWeatherBg()
        })
    }

    private fun updateWeatherBg() {
        var bg: Drawable? = null
        try {
            appContext = mContext.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (_: PackageManager.NameNotFoundException) {
        }
        when (mWeatherBgSelection) {
            0 -> {
                bg = null
                mWeatherHorPadding = 0
                mWeatherVerPadding = 0
            }

            1 -> {
                bg = ResourcesCompat.getDrawable(
                    appContext!!.resources,
                    R.drawable.date_box_str_border,
                    appContext!!.theme
                )
                mWeatherHorPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_box_padding_hor).toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
                mWeatherVerPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_box_padding_ver).toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
            }

            2 -> {
                bg = ResourcesCompat.getDrawable(
                    appContext!!.resources,
                    R.drawable.date_str_border,
                    appContext!!.theme
                )
                mWeatherHorPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_box_padding_hor).toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
                mWeatherVerPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_box_padding_ver).toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
            }

            3 -> {
                bg = ResourcesCompat.getDrawable(
                    appContext!!.resources,
                    R.drawable.ambient_indication_pill_background,
                    appContext!!.theme
                )
                mWeatherHorPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_hor).toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
                mWeatherVerPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_ver).toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
            }

            4, 5 -> {
                bg = ResourcesCompat.getDrawable(
                    appContext!!.resources,
                    R.drawable.date_str_accent,
                    appContext!!.theme
                )
                mWeatherHorPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
                mWeatherVerPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
            }

            6 -> {
                bg = ResourcesCompat.getDrawable(
                    appContext!!.resources,
                    R.drawable.date_str_gradient,
                    appContext!!.theme
                )
                mWeatherHorPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
                mWeatherVerPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
            }

            7 -> {
                bg = ResourcesCompat.getDrawable(
                    appContext!!.resources,
                    R.drawable.date_str_borderacc,
                    appContext!!.theme
                )
                mWeatherHorPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
                mWeatherVerPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
            }

            8 -> {
                bg = ResourcesCompat.getDrawable(
                    appContext!!.resources,
                    R.drawable.date_str_bordergrad,
                    appContext!!.theme
                )
                mWeatherHorPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
                mWeatherVerPadding = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    modRes.getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver)
                        .toFloat(),
                    mContext.resources.displayMetrics
                ).roundToInt()
            }

            else -> {}
        }
        setViewBackground(bg, if ((bg != null && mWeatherBgSelection == 5)) 160 else 255)
        setPadding(mWeatherHorPadding, mWeatherVerPadding, mWeatherHorPadding, mWeatherVerPadding)
    }

    private fun setViewBackground(drawRes: Drawable?, bgAlpha: Int) {
        drawRes?.mutate()
        background = drawRes
        if (drawRes != null) background.alpha = bgAlpha
    }

    fun updateWeatherSettings(
        showLocation: Boolean,
        showText: Boolean,
        showHumidity: Boolean,
        showWind: Boolean,
        name: String
    ) {
        instances.stream()
            .filter { obj: Array<Any> ->
                obj[1] == name
            }
            .forEach { obj: Array<Any> ->
                (obj[0] as CurrentWeatherView).apply {
                    mShowWeatherLocation = showLocation
                    mShowWeatherText = showText
                    mShowWeatherHumidity = showHumidity
                    mShowWeatherWind = showWind
                    mLeftText!!.visibility = if (showLocation) VISIBLE else GONE
                    mWeatherText!!.visibility = if (showText) VISIBLE else GONE
                    mHumLayout!!.visibility = if (showHumidity) VISIBLE else GONE
                    mWindLayout!!.visibility = if (showWind) VISIBLE else GONE
                    updateSettings()
                }
            }
    }

    companion object {
        private val TAG: String = CurrentWeatherView::class.java.name

        val Int.dp: Int
            get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

        @Volatile
        var instances: ArrayList<Array<Any>> = ArrayList()

        fun updateIconsSize(size: Int, name: String) {
            instances
                .stream()
                .filter { obj: Array<Any> ->
                    obj[1] == name
                }
                .forEach { obj: Array<Any> ->
                    val instance = obj[0] as CurrentWeatherView
                    val params = instance.mCurrentImage!!.layoutParams as LayoutParams
                    params.width = size.dp
                    params.height = size.dp
                    params.gravity = Gravity.CENTER_VERTICAL
                    instance.mCurrentImage!!.layoutParams = params
                    instance.mHumImage!!.layoutParams = params
                    instance.mWindImage!!.layoutParams = params
                }
        }

        fun getInstance(c: Context, name: String): CurrentWeatherView {
            return getInstance(name) ?: CurrentWeatherView(c, name)
        }

        fun getInstance(name: String): CurrentWeatherView? {
            for (obj in instances) {
                if (obj[1] == name) {
                    return obj[0] as CurrentWeatherView
                }
            }
            return null
        }
    }
}