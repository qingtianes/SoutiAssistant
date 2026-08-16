package com.dingding.souti

import com.dingding.souti.repository.SettingsLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLogicTest {
    @Test
    fun ocrThrottleMapping() {
        assertEquals(500L, SettingsLogic.ocrThrottleMs("fast"))
        assertEquals(1000L, SettingsLogic.ocrThrottleMs("normal"))
        assertEquals(1500L, SettingsLogic.ocrThrottleMs("slow"))
    }

    @Test
    fun fontScaleMapping() {
        assertEquals(0.85f, SettingsLogic.fontScaleFactor("small"))
        assertEquals(1f, SettingsLogic.fontScaleFactor("medium"))
        assertEquals(1.2f, SettingsLogic.fontScaleFactor("large"))
    }

    @Test
    fun frameSizeMapping() {
        assertEquals(280 to 120, SettingsLogic.frameSizeDp("small"))
        assertEquals(352 to 150, SettingsLogic.frameSizeDp("medium"))
        assertEquals(420 to 180, SettingsLogic.frameSizeDp("large"))
    }

    @Test
    fun viewfinderMapping() {
        assertEquals(0.20f, SettingsLogic.viewfinderFraction("single"))
        assertEquals(0.40f, SettingsLogic.viewfinderFraction("double"))
    }
}