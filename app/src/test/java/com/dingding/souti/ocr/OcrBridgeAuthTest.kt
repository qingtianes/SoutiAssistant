package com.dingding.souti.ocr

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBridgeAuthTest {

    @After
    fun resetAuthState() {
        OcrBridge.cancelAuthRequest()
    }

    @Test
    fun `auth request can only be consumed once`() {
        val requestId = OcrBridge.beginAuthRequest()

        assertTrue(OcrBridge.isAuthRequestActive(requestId))
        assertTrue(OcrBridge.consumeAuthRequest(requestId))
        assertFalse(OcrBridge.isAuthRequestActive(requestId))
        assertFalse(OcrBridge.consumeAuthRequest(requestId))
    }

    @Test
    fun `new auth request invalidates the previous request`() {
        val oldRequestId = OcrBridge.beginAuthRequest()
        val newRequestId = OcrBridge.beginAuthRequest()

        assertNotEquals(oldRequestId, newRequestId)
        assertFalse(OcrBridge.isAuthRequestActive(oldRequestId))
        assertFalse(OcrBridge.consumeAuthRequest(oldRequestId))
        assertTrue(OcrBridge.consumeAuthRequest(newRequestId))
    }

    @Test
    fun `cancelling an old request does not cancel the current request`() {
        val oldRequestId = OcrBridge.beginAuthRequest()
        val currentRequestId = OcrBridge.beginAuthRequest()

        OcrBridge.cancelAuthRequest(oldRequestId)

        assertTrue(OcrBridge.isAuthRequestActive(currentRequestId))
    }

    @Test
    fun `global cancellation rejects a late result`() {
        val requestId = OcrBridge.beginAuthRequest()

        OcrBridge.cancelAuthRequest()

        assertFalse(OcrBridge.isAuthRequestActive(requestId))
        assertFalse(OcrBridge.consumeAuthRequest(requestId))
    }
}
