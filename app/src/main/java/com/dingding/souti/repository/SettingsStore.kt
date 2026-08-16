package com.dingding.souti.repository

import android.content.Context

/**
 * 应用设置的唯一存取入口（SharedPreferences）。
 * 设置页、扫描页、悬浮窗/读屏服务都从这里读取，避免各处散落 key。
 */
object SettingsStore {

    private const val PREFS = "souti_settings"

    // 识别与匹配
    private const val KEY_RESULT_LIMIT = "result_limit"
    private const val KEY_MIN_SCORE = "min_score"
    private const val KEY_OCR_SPEED = "ocr_speed"

    // 浮窗显示
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_SHOW_META = "show_meta"
    private const val KEY_FRAME_SIZE = "frame_size"
    private const val KEY_OUTPUT_POSITION = "output_position"

    // 扫描搜题
    private const val KEY_SCAN_ZOOM = "scan_zoom"
    private const val KEY_VIEWFINDER_HEIGHT = "viewfinder_height"

    const val RESULT_LIMIT_DEFAULT = 10
    const val MIN_SCORE_DEFAULT = 50f
    const val OCR_SPEED_DEFAULT = "normal"
    const val FONT_SCALE_DEFAULT = "medium"
    const val SHOW_META_DEFAULT = true
    const val FRAME_SIZE_DEFAULT = "medium"
    const val OUTPUT_POSITION_DEFAULT = "bottom_right"
    const val SCAN_ZOOM_DEFAULT = 1f
    const val VIEWFINDER_HEIGHT_DEFAULT = "double"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- 识别与匹配 ----
    fun resultLimit(ctx: Context): Int =
        prefs(ctx).getInt(KEY_RESULT_LIMIT, RESULT_LIMIT_DEFAULT)

    fun setResultLimit(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_RESULT_LIMIT, value.coerceIn(1, 50)).apply()
    }

    fun minScore(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_MIN_SCORE, MIN_SCORE_DEFAULT)

    fun setMinScore(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat(KEY_MIN_SCORE, value.coerceIn(0f, 100f)).apply()
    }

    fun ocrSpeed(ctx: Context): String =
        prefs(ctx).getString(KEY_OCR_SPEED, OCR_SPEED_DEFAULT) ?: OCR_SPEED_DEFAULT

    fun setOcrSpeed(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_OCR_SPEED, value).apply()
    }

    fun ocrThrottleMs(ctx: Context): Long = when (ocrSpeed(ctx)) {
        "fast" -> 500L
        "slow" -> 1500L
        else -> 1000L
    }

    // ---- 浮窗显示 ----
    fun fontScale(ctx: Context): String =
        prefs(ctx).getString(KEY_FONT_SCALE, FONT_SCALE_DEFAULT) ?: FONT_SCALE_DEFAULT

    fun setFontScale(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_FONT_SCALE, value).apply()
    }

    fun fontScaleFactor(ctx: Context): Float = when (fontScale(ctx)) {
        "small" -> 0.85f
        "large" -> 1.2f
        else -> 1f
    }

    fun showMeta(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SHOW_META, SHOW_META_DEFAULT)

    fun setShowMeta(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SHOW_META, value).apply()
    }

    fun frameSize(ctx: Context): String =
        prefs(ctx).getString(KEY_FRAME_SIZE, FRAME_SIZE_DEFAULT) ?: FRAME_SIZE_DEFAULT

    fun setFrameSize(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_FRAME_SIZE, value).apply()
    }

    fun outputPosition(ctx: Context): String =
        prefs(ctx).getString(KEY_OUTPUT_POSITION, OUTPUT_POSITION_DEFAULT) ?: OUTPUT_POSITION_DEFAULT

    fun setOutputPosition(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_OUTPUT_POSITION, value).apply()
    }

    // ---- 扫描搜题 ----
    fun scanZoom(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_SCAN_ZOOM, SCAN_ZOOM_DEFAULT)

    fun setScanZoom(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat(KEY_SCAN_ZOOM, value.coerceIn(1f, 8f)).apply()
    }

    fun viewfinderHeight(ctx: Context): String =
        prefs(ctx).getString(KEY_VIEWFINDER_HEIGHT, VIEWFINDER_HEIGHT_DEFAULT)
            ?: VIEWFINDER_HEIGHT_DEFAULT

    fun setViewfinderHeight(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_VIEWFINDER_HEIGHT, value).apply()
    }

    fun viewfinderFraction(ctx: Context): Float = when (viewfinderHeight(ctx)) {
        "single" -> 0.20f
        else -> 0.40f
    }

    /** 清空全部设置，回到默认值。 */
    fun reset(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}