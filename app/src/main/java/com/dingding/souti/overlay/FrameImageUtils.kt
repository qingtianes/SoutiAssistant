package com.dingding.souti.overlay

import android.graphics.Bitmap

/**
 * 帧图像工具：负责 OCR 前的图像差异、亮度和反色处理。
 * 不依赖 Service 状态，便于独立复用。
 */
object FrameImageUtils {
    fun computeFrameDiff(a: Bitmap, b: Bitmap?): Float {
        if (b == null || a.width != b.width || a.height != b.height) return 1f
        var diffPixels = 0
        val w = a.width
        val h = a.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                val dr = Math.abs((pa shr 16 and 0xFF) - (pb shr 16 and 0xFF))
                val dg = Math.abs((pa shr 8 and 0xFF) - (pb shr 8 and 0xFF))
                val db = Math.abs((pa and 0xFF) - (pb and 0xFF))
                if (dr + dg + db > 90) diffPixels++
            }
        }
        return diffPixels.toFloat() / (w * h)
    }

    fun computeAverageBrightness(bmp: Bitmap): Int {
        var sum = 0L
        var count = 0
        val step = 8
        for (y in 0 until bmp.height step step) {
            for (x in 0 until bmp.width step step) {
                val p = bmp.getPixel(x, y)
                sum += (p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)
                count += 3
            }
        }
        return if (count == 0) 255 else (sum / count).toInt()
    }

    fun invertBitmap(bmp: Bitmap) {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in pixels.indices) {
            val a = pixels[i] shr 24 and 0xFF
            val r = 255 - (pixels[i] shr 16 and 0xFF)
            val g = 255 - (pixels[i] shr 8 and 0xFF)
            val b = 255 - (pixels[i] and 0xFF)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }
}
