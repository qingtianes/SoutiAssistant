package com.dingding.souti.repository

/** 设置值到运行时参数的纯函数映射，便于单元测试。 */
object SettingsLogic {
    fun ocrThrottleMs(speed: String): Long = when (speed) {
        "fast" -> 500L
        "slow" -> 1500L
        else -> 1000L
    }

    fun fontScaleFactor(scale: String): Float = when (scale) {
        "small" -> 0.85f
        "large" -> 1.2f
        else -> 1f
    }

    fun frameSizeDp(size: String): Pair<Int, Int> = when (size) {
        "small" -> 280 to 120
        "large" -> 420 to 180
        else -> 352 to 150
    }

    fun viewfinderFraction(height: String): Float = when (height) {
        "single" -> 0.20f
        else -> 0.40f
    }
}