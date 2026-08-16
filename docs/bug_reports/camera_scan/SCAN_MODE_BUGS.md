# 扫描搜题模式 BUG 汇报（WorkBuddy 看图实测，2026-08-17 凌晨）

> 截图来自鸿蒙真机上跑的 0.11.0 封装 APK；WorkBuddy 读 `ScanScreen.kt` 全部 592 行代码 + 看图分析根因。
> **优先级**：P0 必须修（用户体验直接崩坏），P1 体验优化，P2 视觉小瑕疵。

---

## TL;DR

扫描搜题**核心 3 个问题**：
1. **P0**：摄像头预览区域被压成屏幕中间的窄条（约 1/3 宽），用户实际能看到的取景范围极小
2. **P0**：取景框高度太大（屏幕高度 40%），一次圈了 4-6 道题，导致 OCR 同时识别多题、匹配结果列表闪烁多题
3. **P2**：取景框左上角有个黑色小缺口，视觉不规整

---

## 截图证据

> 原件：`docs/bug_reports/camera_scan/screenshot_20260817_*.jpg`

| # | 文件名 | 时间 | 变焦 | 现象摘要 |
|---|---|---|---|---|
| 1 | `screenshot_20260817_000804_com.dingding.souti.jpg` | 00:08:04 | **1.0x** | 窄条预览，绿框圈了题 15、16、17 等多题，匹配结果给 15、17 |
| 2 | `screenshot_20260817_000813_com.dingding.souti.jpg` | 00:08:13 | **2.8x** | 画面放大后绿框圈了题 12-16 多题，匹配结果给 12、16 |

---

## P0-问题 1：预览区域被压成窄条

### 现象
截图里摄像头预览**只占屏幕中间的窄条**（约屏幕宽度的 1/3），两侧大片纯黑。绿色识别框虽然按比例画，但实际能"圈住"的题目极少（窄条里能塞 4-6 行判断题）。

### 根因（已定位到代码）
`ScanScreen.kt` 第 469-472 + 474-477 + 113-116 行：

```kotlin
// 第 113-116 行：PreviewView 用 FIT_CENTER（居中等比缩放）
val previewView = remember {
    PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FIT_CENTER  // ← 居中等比缩放
    }
}

// 第 470 行：CameraX Preview 用 16:9 横宽比
val preview = Preview.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_16_9)   // ← 16:9
    .build()

// 第 476 行：ImageAnalysis 也用 16:9
val imageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setTargetAspectRatio(AspectRatio.RATIO_16_9)   // ← 16:9
    .build()
```

**三处都是 16:9** + FIT_CENTER + 竖屏手机（aspect ~9:16）→ 摄像头画面被强制 16:9 渲染，竖屏里只能居中等比缩放，**左右两侧大黑边**就出来了。

### 修复建议
两种方案任选：
- **方案 A（推荐）**：把两处 `RATIO_16_9` 改成 `RATIO_4_3`（更接近竖屏比例，损失少）
- **方案 B**：`PreviewView.ScaleType.FIT_CENTER` → `FILL_CENTER`（铺满屏幕，会裁剪画面但没黑边）
- **方案 C**（最干净）：根据屏幕方向动态选择 — 竖屏用 `RATIO_4_3`，横屏用 `RATIO_16_9`

---

## P0-问题 2：取景框太高，一次圈多题

### 现象
绿色识别框圈住了 4-6 道判断题，OCR 把这些题全部识别 + 全部参与题库匹配 → 结果列表里**同时出现 2-3 道题的匹配卡**（截图 1 显示题 15 和 17，截图 2 显示题 12 和 16）。

这跟用户期望"圈一道题出答案"不符，且由于代码里没做防抖/稳定判定，手机稍微一动，**结果列表会频繁跳动**。

### 根因（已定位到代码）
`ScanScreen.kt` 第 83-84 行：

```kotlin
private const val VF_WIDTH_FRACTION = 0.88f   // 宽 88%
private const val VF_HEIGHT_FRACTION = 0.40f  // 高 40%   ← 太高了！
```

**取景框高度 = 屏幕高度 × 40%**。竖屏手机屏幕高度 2856px，40% ≈ 1140px ≈ 能塞 8-12 行 14sp 文字 → 一次圈 4-6 道判断题很正常。

加上 `filterToViewfinder`（第 553-591 行）按**文字块中心点**过滤：
- 只要中心点在框内的文字块都保留
- 多道题的标题中心点都在 40% 高度的框内 → 都进 OCR 结果
- 然后每道都进 `bank.search()` → 结果列表跳多题

### 修复建议

#### 方案 A：缩小取景框高度
```kotlin
private const val VF_WIDTH_FRACTION = 0.92f
private const val VF_HEIGHT_FRACTION = 0.15f  // 从 0.40 降到 0.15，约 1-2 行字
```

#### 方案 B：按"中心区域优先"过滤文字块
修改 `filterToViewfinder`：
- 计算每个文字块中心点到框中心的距离
- 只保留距离最近的那一道题（距离最小者）
- 其他文字块丢掉

#### 方案 C：增加结果防抖（搭配方案 A 或 B）
```kotlin
// 在 onText 回调里加稳定判定：连续 2 次识别到相同内容才更新
private var stableText = ""
private var pendingText = ""
private var stableCount = 0

onText = { text ->
    if (text == pendingText) {
        stableCount++
        if (stableCount >= 2 && text != stableText) {
            stableText = text
            // 更新 recognizedText + 触发搜索
        }
    } else {
        pendingText = text
        stableCount = 1
    }
}
```

---

## P0-问题 3（顺带）：OCR 整帧识别 + 后过滤 → 误识别框外内容

### 现象
虽然没在截图里直接看到这个现象（截图里绿框里的内容是题列表），但从代码看，存在理论风险：ML Kit 对**整帧**做 OCR，再按文字块中心过滤 → 框外文字块的中心点刚好在框内的情况会导致误判。

### 根因
`ScanScreen.kt` 第 539 行：
```kotlin
recognizer.process(inputImage)  // ← 对整帧做 OCR
.addOnSuccessListener { result ->
    val text = filterToViewfinder(result, imageProxy)  // ← 后置过滤
    ...
}
```

### 修复建议
**改成"先裁剪再识别"**：
```kotlin
// 1. 根据取景框坐标换算出图像坐标系的裁剪矩形
val cropRect = Rect(
    imgVfLeft.toInt(),
    imgVfTop.toInt(),
    imgVfRight.toInt(),
    imgVfBottom.toInt()
)
// 2. 用 Bitmap.createBitmap 裁剪
val cropped = Bitmap.createBitmap(fullBitmap, 
    cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
// 3. 只对 cropped 做 OCR
val inputImage = InputImage.fromBitmap(cropped, 0)
```

这样可以：
- 节省 OCR 算力（只处理框内）
- 杜绝框外文字误入
- 减少节流压力

---

## P2-问题 4：取景框左上角不规则缺口

### 现象
截图里绿色识别框左上角附近有个**黑色三角形小缺口**，框的边线不连续。视觉上不规整。

### 根因（推测）
`ViewfinderOverlay`（第 294-320 行）：
```kotlin
Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val vfW = w * VF_WIDTH_FRACTION
    val vfH = h * VF_HEIGHT_FRACTION
    val left = (w - vfW) / 2f
    val top = (h - vfH) / 2f
    ...
    drawRoundRect(
        color = Color(0xFF00E676),
        topLeft = Offset(left, top),
        size = Size(vfW, vfH),
        cornerRadius = CornerRadius(VF_CORNER_DP.dp.toPx(), ...),
        style = Stroke(width = 3.dp.toPx())
    )
}
```

`drawRoundRect` 圆角矩形 + `Stroke` 描边在某些 Compose Canvas 渲染下，圆角拐角处的描边宽度会"内缩"导致缺口（小问题，多见于圆角半径 12dp + 描边 3dp 这种组合）。

### 修复建议
- 改用圆角半径更小的值（`VF_CORNER_DP = 8f`）
- 或者把 `Stroke(width = 3.dp.toPx())` 改成 `Stroke(width = 4.dp.toPx())` 让描边粗一点盖住缺口
- 或者用 `drawCircle` + 直线段手动画 4 段圆角描边

---

## 截图里**没看到但要注意**的现象

| 现象 | 我的推测 |
|---|---|
| 顶部「识别中」一直显示 | OCR 循环在跑，正常。但和实际"识别到的题"不同步 → 用户无法判断"识别状态" |
| 匹配结果区显示 2 道题 | 因为绿框圈了 4-6 道，OCR 全识别 → 匹配出多题 |
| 变焦后绿框位置变了 | 数字变焦会改变 cropRect，绿框又按 previewView 比例算 → 圈的范围跟着变 |

---

## 处理顺序建议

1. **先 P0-问题 1**（预览区域窄条）→ 改 aspect ratio，5 行代码
2. **接着 P0-问题 2**（取景框太高）→ 缩高度 + 加防抖，10-20 行代码
3. **然后 P0-问题 3**（整帧 OCR）→ 改成裁剪后 OCR，需要处理 bitmap 旋转，30 行代码
4. **最后 P2-问题 4**（圆角缺口）→ 改个数值就行，1 行代码

按项目"只切蛋糕、不改味道"原则，全部改动限制在 `ScanScreen.kt` 一个文件内。

---

## 我没验证的项

- **0.11.0 与本仓库代码是否一致**：用户说"上传的封装 APK"，可能不是当前 main 分支的代码。Codex 修完请确认是用当前代码 build。
- **鸿蒙卓易通对 CameraX 的兼容性**：理论上 CameraX 在 Android 8.0+ 都能跑，但卓易通是兼容层，可能有细微差异。建议回归测试时在鸿蒙真机上多试几次。
- **OCR 处理 1 帧耗时**：大图 + 中文识别在低端机上可能 >1s，导致节流逻辑失效。Codex 改时建议加 logcat 日志看耗时。

---

_汇报人：WorkBuddy；时间：2026-08-17 00:17_
_文件夹：`docs/bug_reports/camera_scan/`（按叮叮要求每次单独子文件夹）_