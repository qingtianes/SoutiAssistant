package com.dingding.souti

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * OCR 截屏管理器：首次授权后把 MediaProjection 句柄交给 Service
 * 后续按按钮 Service 直接 OCR（不再弹授权）
 */
class OcrHelper(private val activity: ComponentActivity) {

    companion object {
        private const val TAG = "OcrHelper"
        const val PREFS_NAME = "ocr_prefs"
        const val KEY_RESULT = "ocr_result"
        const val KEY_TIMESTAMP = "ocr_timestamp"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val pendingRect: Rect
        get() = OcrBridge.pendingRect

    fun startOcr(rect: Rect, launcher: ActivityResultLauncher<Intent>) {
        OcrBridge.pendingRect = rect
        OcrBridge.continuous = activity.intent.getBooleanExtra("ocr_continuous", false)
        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mpm.createScreenCaptureIntent())
    }

    /**
     * 拿到授权结果：截屏 → OCR → 写 SharedPreferences → 启动 Service 接管句柄 → finish
     */
    fun onProjectionResult(resultCode: Int, data: Intent) {
        if (resultCode != android.app.Activity.RESULT_OK) {
            activity.finish()
            return
        }
        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { Log.d(TAG, "MediaProjection onStop") }
        }, mainHandler)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = projection.createVirtualDisplay(
            "ocr_capture", w, h, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, mainHandler
        )

        mainHandler.postDelayed({
            val image = try { imageReader.acquireLatestImage() } catch (_: Exception) { null }
            if (image == null) {
                writeResult("截屏未就绪")
                virtualDisplay.release(); imageReader.close(); projection.stop()
                activity.finish()
                return@postDelayed
            }
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride, image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val rect = pendingRect
                val cropL = rect.left.coerceIn(0, w - 1)
                val cropT = rect.top.coerceIn(0, h - 1)
                val cropR = rect.right.coerceIn(1, w)
                val cropB = rect.bottom.coerceIn(1, h)
                val cropW = (cropR - cropL).coerceAtLeast(1)
                val cropH = (cropB - cropT).coerceAtLeast(1)

                Log.d(TAG, "OCR 裁剪: $cropL,$cropT ${cropW}x$cropH")
                if (cropW < 20 || cropH < 20) {
                    writeResult("区域太小")
                } else {
                    val cropped = Bitmap.createBitmap(bitmap, cropL, cropT, cropW, cropH)
                    runOcr(cropped)
                }
            } catch (e: Exception) {
                Log.e(TAG, "截屏失败: ${e.message}")
                writeResult("截屏失败")
            } finally {
                try { virtualDisplay.release() } catch (_: Exception) {}
                try { imageReader.close() } catch (_: Exception) {}
                // ★ 关键：MediaProjection 句柄保存到 Service（OcrBridge）
                //   后续按按钮 Service 复用此句柄（不再弹授权）
                OcrBridge.mediaProjection = projection
                OcrBridge.isRunning = true
                Log.d(TAG, "MediaProjection 句柄已交给 OcrBridge（可复用）")
                activity.finish()
            }
        }, 800)
    }

    private fun runOcr(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.replace("\n", " ").trim()
                Log.d(TAG, "OCR 完成: ${text.length} 字符")
                writeResult(text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR 失败: ${e.message}")
                writeResult("OCR 失败")
            }
    }

    private fun writeResult(text: String) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_RESULT, text)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun destroy() { }
}
