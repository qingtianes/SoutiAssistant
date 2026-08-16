package com.dingding.souti.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dingding.souti.model.SearchResult
import com.dingding.souti.repository.QuestionBank
import com.dingding.souti.repository.SettingsStore
import com.dingding.souti.ui.theme.LocalGlass
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VF_WIDTH_FRACTION = 0.88f
private const val VF_HEIGHT_FRACTION = 0.40f
private const val VF_CORNER_DP = 12f
private const val ZOOM_STEP = 1.4f

/**
 * 扫描搜题页：
 * - 上 50%：CameraX 实时预览，中间是横向取景框（只识别框内题目）
 * - 下 50%：ML Kit 中文 OCR 持续识别 + QuestionBank.search 实时匹配结果
 */
@Composable
fun ScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bank = remember { QuestionBank(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(checkCameraPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var recognizedText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var paused by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<CameraScanController?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // 有权限且页面在组合中时，绑定/解绑 CameraX。页面返回时自动解绑，不影响悬浮窗/读屏。
    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) {
            val camera = CameraScanController(
                context = context.applicationContext,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                viewfinderFraction = SettingsStore.viewfinderFraction(context),
                ocrThrottleMs = SettingsStore.ocrThrottleMs(context),
                onZoomChanged = { zoomRatio = it },
                onText = { text ->
                    if (!paused) {
                        recognizedText = text
                        searchJob?.cancel()
                        if (text.isBlank()) {
                            results = emptyList()
                        } else {
                            searchJob = scope.launch {
                                val found = withContext(Dispatchers.IO) {
                                    val limit = SettingsStore.resultLimit(context)
                                    val minScore = SettingsStore.minScore(context)
                                    bank.search(text, limit = limit).filter { it.score >= minScore }
                                }
                                results = found
                            }
                        }
                    }
                }
            )
            controller = camera
            camera.start()
            camera.setZoomRatio(SettingsStore.scanZoom(context))
            onDispose {
                camera.stop()
                if (controller === camera) controller = null
            }
        } else {
            onDispose { }
        }
    }

    val currentZoom by rememberUpdatedState(zoomRatio)
    val currentController by rememberUpdatedState(controller)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(glass.bgMid)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = glass.textPrimary)
            }
            Text(
                text = "扫描搜题",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary
            )
            Spacer(Modifier.weight(1f))
            if (recognizedText.isNotBlank()) {
                Text(
                    text = "识别中",
                    fontSize = 12.sp,
                    color = glass.textSecondary
                )
            }
        }

        // 上下 50/50
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(hasCameraPermission) {
                        if (hasCameraPermission) {
                            detectTransformGestures { _, _, zoom, _ ->
                                currentController?.setZoomRatio(currentZoom * zoom)
                            }
                        }
                    }
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    ViewfinderOverlay(fraction = SettingsStore.viewfinderFraction(context), modifier = Modifier.fillMaxSize())
                    ZoomControls(
                        zoomRatio = zoomRatio,
                        onZoomIn = { controller?.setZoomRatio(zoomRatio * ZOOM_STEP) },
                        onZoomOut = { controller?.setZoomRatio(zoomRatio / ZOOM_STEP) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "未获得摄像头权限",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "扫描搜题需要实时摄像头预览，请授权后使用。",
                            fontSize = 13.sp,
                            color = Color(0xFFCCCCCC)
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("授权摄像头")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "匹配答案",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = glass.textSecondary
                    )
                    TextButton(onClick = { paused = !paused }) {
                        Text(if (paused) "继续" else "暂停", fontSize = 14.sp, color = glass.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, "关闭", tint = glass.textPrimary)
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                recognizedText.isBlank() -> "将题目对准取景框，识别到文字后自动搜索"
                                else -> "未找到匹配题目\n识别文字：${recognizedText.take(80)}"
                            },
                            fontSize = 13.sp,
                            color = glass.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(results, key = { it.question.id }) { result ->
                            SearchResultCard(result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewfinderOverlay(fraction: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val vfW = w * VF_WIDTH_FRACTION
        val vfH = h * fraction
        val left = (w - vfW) / 2f
        val top = (h - vfH) / 2f
        val right = left + vfW
        val bottom = top + vfH
        val dim = Color(0x66000000)

        drawRect(dim, topLeft = Offset(0f, 0f), size = Size(w, top))
        drawRect(dim, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
        drawRect(dim, topLeft = Offset(0f, top), size = Size(left, vfH))
        drawRect(dim, topLeft = Offset(right, top), size = Size(w - right, vfH))

        drawRoundRect(
            color = Color(0xFF00E676),
            topLeft = Offset(left, top),
            size = Size(vfW, vfH),
            cornerRadius = CornerRadius(VF_CORNER_DP.dp.toPx(), VF_CORNER_DP.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // ★ A. 角标：放绿框外（左上角上方），呼应 App 图标，不遮挡框内识别内容
        val label = textMeasurer.measure(
            AnnotatedString("A."),
            style = TextStyle(color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        )
        drawText(label, topLeft = Offset(left, (top - 30.dp.toPx()).coerceAtLeast(0f)))
    }
}

@Composable
private fun ZoomControls(
    zoomRatio: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onZoomOut,
            modifier = Modifier.background(Color(0x88000000), CircleShape)
        ) {
            Text(text = "−", color = Color.White, fontSize = 18.sp)
        }
        Text(
            text = "%.1fx".format(zoomRatio),
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .background(Color(0x88000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        IconButton(
            onClick = onZoomIn,
            modifier = Modifier.background(Color(0x88000000), CircleShape)
        ) {
            Text(text = "+", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult) {
    val glass = LocalGlass.current
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = result.question.stem,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = glass.textPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (result.question.options.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            result.question.options.forEach { option ->
                Text(
                    text = option,
                    fontSize = 12.sp,
                    color = glass.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, top = 1.dp)
                )
            }
        }
        if (result.question.answer.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "答案：${result.question.answer}",
                fontSize = 13.sp,
                color = glass.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "来源：" + result.bankName + if (result.question.source.isNotBlank()) " / " + result.question.source else "",
            fontSize = 11.sp,
            color = glass.textSecondary
        )
        Text(
            text = "匹配分：${result.score}",
            fontSize = 11.sp,
            color = glass.textSecondary
        )
    }
}

private fun checkCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * CameraX 绑定控制器：负责 Preview + ImageAnalysis，节流约 1 秒执行中文 OCR。
 * 只识别取景框内的文字，支持双指缩放和按钮缩放。
 */
private class CameraScanController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val viewfinderFraction: Float,
    private val ocrThrottleMs: Long,
    private val onZoomChanged: (Float) -> Unit,
    private val onText: (String) -> Unit
) {
    companion object {
        private const val TAG = "ScanCamera"
        private const val OCR_THROTTLE_MS = 1000L
        private const val DEFAULT_MAX_ZOOM = 4f
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var maxZoomRatio = DEFAULT_MAX_ZOOM
    private var currentZoom = 1f
    private var pendingZoom: Float? = null
    private var started = false
    private var lastAnalyzedAt = 0L
    private var analyzing = false

    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(1f, maxZoomRatio)
        currentZoom = clamped
        val boundCamera = camera
        if (boundCamera == null) {
            pendingZoom = clamped
        } else {
            boundCamera.cameraControl.setZoomRatio(clamped)
        }
        mainHandler.post { onZoomChanged(clamped) }
    }

    fun start() {
        if (started) return
        started = true

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (!started) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build())
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build())
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyze(imageProxy)
                        }
                    }

                provider.unbindAll()
                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                camera = boundCamera

                val zoomState = boundCamera.cameraInfo.zoomState.value
                if (zoomState != null) {
                    maxZoomRatio = zoomState.maxZoomRatio.coerceAtLeast(1f)
                }
                val target = pendingZoom ?: 1f
                pendingZoom = null
                setZoomRatio(target)
            } catch (e: Exception) {
                Log.w(TAG, "绑定摄像头失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        started = false
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        camera = null
        cameraProvider = null
        cameraExecutor.shutdown()
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzedAt < ocrThrottleMs || analyzing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        analyzing = true
        lastAnalyzedAt = now

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val text = filterToViewfinder(result, imageProxy)
                mainHandler.post { onText(text) }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR 识别失败: ${e.message}")
            }
            .addOnCompleteListener {
                analyzing = false
                imageProxy.close()
            }
    }

    private fun filterToViewfinder(result: Text, imageProxy: ImageProxy): String {
        val viewW = previewView.width
        val viewH = previewView.height
        if (viewW <= 0 || viewH <= 0) return result.text.trim()

        val rotation = imageProxy.imageInfo.rotationDegrees
        val crop = imageProxy.cropRect
        if (crop.width() <= 0 || crop.height() <= 0) return result.text.trim()

        val rotW = if (rotation % 180 == 0) crop.width().toFloat() else crop.height().toFloat()
        val rotH = if (rotation % 180 == 0) crop.height().toFloat() else crop.width().toFloat()

        val scale = maxOf(viewW.toFloat() / rotW, viewH.toFloat() / rotH)
        val offX = (viewW - rotW * scale) / 2f
        val offY = (viewH - rotH * scale) / 2f

        val vfLeft = viewW * (1f - VF_WIDTH_FRACTION) / 2f
        val vfRight = viewW * (1f + VF_WIDTH_FRACTION) / 2f
        val vfTop = viewH * (1f - viewfinderFraction) / 2f
        val vfBottom = viewH * (1f + viewfinderFraction) / 2f

        val imgVfLeft = (vfLeft - offX) / scale
        val imgVfRight = (vfRight - offX) / scale
        val imgVfTop = (vfTop - offY) / scale
        val imgVfBottom = (vfBottom - offY) / scale

        return result.textBlocks
            .asSequence()
            .filter { it.boundingBox != null }
            .filter { block ->
                val box = block.boundingBox!!
                val cx = box.centerX().toFloat()
                val cy = box.centerY().toFloat()
                cx >= imgVfLeft && cx <= imgVfRight && cy >= imgVfTop && cy <= imgVfBottom
            }
            .sortedBy { it.boundingBox!!.top }
            .joinToString("\n") { it.text }
            .trim()
    }
}
