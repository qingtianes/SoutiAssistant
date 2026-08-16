package com.dingding.souti.overlay

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.DisplayMetrics

data class CaptureSurface(
    val imageReader: ImageReader,
    val virtualDisplay: VirtualDisplay
)

object ProjectionVirtualDisplayFactory {
    fun create(
        metrics: DisplayMetrics,
        projection: MediaProjection,
        name: String,
        handler: Handler?
    ): CaptureSurface {
        val reader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2
        )
        val virtualDisplay = projection.createVirtualDisplay(
            name, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
        return CaptureSurface(reader, virtualDisplay)
    }
}
