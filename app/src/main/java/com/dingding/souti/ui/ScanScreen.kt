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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dingding.souti.model.SearchResult
import com.dingding.souti.repository.QuestionBank
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 扫描搜题页：
 * - 上 50%：CameraX PreviewView 实时摄像头预览
 * - 下 50%：ML Kit 中文 OCR 持续识别 + QuestionBank.search 实时匹配结果
 */
@Composable
fun ScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
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

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    // 有权限且页面在组合中时，绑定/解绑 CameraX。页面返回时自动解绑，不影响悬浮窗/读屏。
    DisposableEffect(lifecycleOwner, hasCameraPermission, previewView) {
        if (hasCameraPermission) {
            val controller = CameraScanController(
                context = context.applicationContext,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                onText = { text ->
                    recognizedText = text
                    searchJob?.cancel()
                    searchJob = scope.launch {
                        val found = withContext(Dispatchers.IO) {
                            bank.search(text, limit = 10)
                        }
                        results = found
                    }
                }
            )
            controller.start()
            onDispose { controller.stop() }
        } else {
            onDispose { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回")
            }
            Text(
                text = "扫描搜题",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Green
            )
            Spacer(Modifier.weight(1f))
            if (recognizedText.isNotBlank()) {
                Text(
                    text = "识别中",
                    fontSize = 12.sp,
                    color = Color.Gray
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
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
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
                        color = Color.Gray
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, "关闭")
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
                                recognizedText.isBlank() -> "将题目对准摄像头，识别到文字后自动搜索"
                                else -> "未找到匹配题目\n识别文字：${recognizedText.take(80)}"
                            },
                            fontSize = 13.sp,
                            color = Color.Gray,
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
private fun SearchResultCard(result: SearchResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = result.question.stem,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF222222),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (result.question.options.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                result.question.options.forEach { option ->
                    Text(
                        text = option,
                        fontSize = 12.sp,
                        color = Color(0xFF333333),
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
                    color = Green,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "来源：" + result.bankName + if (result.question.source.isNotBlank()) " / " + result.question.source else "",
                fontSize = 11.sp,
                color = Color.Gray
            )
            Text(
                text = "匹配分：${result.score}",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

private fun checkCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * CameraX 绑定控制器：负责 Preview + ImageAnalysis，节流约 1 秒执行中文 OCR。
 */
private class CameraScanController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onText: (String) -> Unit
) {
    companion object {
        private const val TAG = "ScanCamera"
        private const val OCR_THROTTLE_MS = 1000L
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var started = false
    private var lastAnalyzedAt = 0L
    private var analyzing = false

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
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyze(imageProxy)
                        }
                    }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
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
        cameraProvider = null
        cameraExecutor.shutdown()
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzedAt < OCR_THROTTLE_MS || analyzing) {
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
                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    mainHandler.post { onText(text) }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR 识别失败: ${e.message}")
            }
            .addOnCompleteListener {
                analyzing = false
                imageProxy.close()
            }
    }
}