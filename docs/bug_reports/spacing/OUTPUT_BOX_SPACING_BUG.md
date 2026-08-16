# 输出框 padding/margin 缺失汇报（WorkBuddy 实测，2026-08-16 晚）

> 背景：上一份汇报 `docs/BUG_REPORT_FLOAT_WINDOW.md` 修完后，Codex 把输出框高度改为"🎯 命中题目的实际高度"，但**忽略了卡片自身的 padding/margin** 导致底部切了一截。
> 本次只关心**padding/margin 参数位置**与缺失量，不重复上次的浮窗布局 BUG。

---

## TL;DR

输出框高度公式需要补上结果卡片的 **padding + 内部 topMargin 总和**，目前缺失约 **22dp**（用户感觉"略小了一丁点"）。

所有 spacing 参数都在 `OverlayResultRenderer.kt` 的 `buildScanCard()` 函数里（浮窗扫描模式专用）。

---

## 截图证据（按时间顺序）

> 原件：`docs/bug_reports/spacing/clipboard-2026-08-16T13-20-19-*.png`

| # | 文件名 | 时间 | 现象 |
|---|---|---|---|
| 1 | `clipboard-2026-08-16T13-20-19-544Z-5bc3c4b2.png` | 04:44 | 整图，结果卡「来源：sbs_bank · 相关度 13」紧贴背景「粘稠度」行 |
| 2 | `clipboard-2026-08-16T13-20-19-548Z-5549aa83.png` | （裁剪） | 看得最清楚：「答案：C」与背景「C. 粘度」压在一起 |
| 3 | `clipboard-2026-08-16T13-20-19-550Z-d57055b3.png` | （裁剪） | 题干被识别框压住上半部分（识别框与结果卡有重叠 = 浮窗高度仍差一点） |

---

## 用户问的两个参数，定位如下

### 1. 每个答案之间的间隔（卡片内部元素间距）
**位置**：`OverlayResultRenderer.kt` 第 121-180 行 `buildScanCard()`

| 元素 | topMargin | 行号 |
|---|---|---|
| 题干（首元素，无 margin） | — | 第 132-143 行 |
| 4 × 选项（每个） | **`1dp`** | 第 154 行 `topMargin = dp(context, 1)` |
| ✔ 答案行 | **`4dp`** | 第 167 行 `topMargin = dp(context, 4)` |
| 来源：行 | **`2dp`** | 第 177 行 `topMargin = dp(context, 2)` |

合计内部 margin：`1×4 + 4 + 2 = 10dp`（如果只一题不滚动；如果多题卡片，还要算卡片之间）

### 2. 最顶部 / 最底部间隔（卡片内 padding）
**位置**：同一函数第 126 行

```kotlin
setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
//                 left        top           right       bottom
//                              ↑                ↑
//                          顶部 6dp        底部 6dp
```

合计 padding：**顶部 6dp + 底部 6dp = 12dp**

### 3. 卡片与卡片之间（容器 bottomMargin）
**位置**：第 130 行
```kotlin
apply { bottomMargin = dp(context, 4) }
```
- **不影响本卡片自身高度**（影响下一卡片位置）
- 但**会贡献到 ScrollView 总内容高度**，进而影响 `updateFloatHeightAfterRender` 算的 `contentH`

---

## Codex 缺失的总量（我估算）

按"输出框高度 = 命中题目的实际高度"计算时，漏算：
- **顶部 padding** 6dp
- **底部 padding** 6dp
- **4 个选项的 topMargin** 1dp × 4 = 4dp
- **答案 topMargin** 4dp
- **来源 topMargin** 2dp
- **合计 ≈ 22dp**

→ 用户感觉"略小了一丁点"正好吻合（22dp 在 2856px 物理像素屏幕上约 66px，约屏幕高度的 2.3%，视觉上就是"一丁点"）

---

## 修复建议（最小改动）

### 方案 A：在 `updateFloatHeightAfterRender` 算实际高度时把 padding/margin 加上
**位置**：`FloatWindowService.kt` 第 1516-1543 行

```kotlin
private fun updateFloatHeightAfterRender() {
    val ocrScroll = ocrResultScroll ?: return
    val contentRoot = root ?: return
    val p = params ?: return
    ocrScroll.post {
        val container = ocrResultContainer ?: return@post
        val contentH = if (container.height > 0) container.height else container.measuredHeight
        
        // ★ 关键修复：补上结果卡片 padding/margin
        // OverlayResultRenderer.buildScanCard:
        //   顶部 padding 6dp + 底部 padding 6dp = 12dp
        //   内部 topMargin 总和：1*4 + 4 + 2 = 10dp（题干→选项×4→答案→来源）
        //   容器 bottomMargin 4dp（最后一个卡片不需要）
        val cardSpacing = dp(12) + dp(10)  // = 22dp padding+margin
        val lastCardBottomMargin = dp(4)
        val adjustedContentH = contentH + cardSpacing  // 最后一张卡不需要 bottomMargin
        
        val desired = adjustedContentH.coerceIn(dp(60), dp(280))  // 上限从 180 提到 280
        // ...
    }
}
```

### 方案 B（更彻底）：让浮窗高度跟随"实际测量高度 + 安全余量"
```kotlin
val desired = (contentH + dp(24)).coerceIn(dp(80), dp(320))
```

### 方案 C（最干净）：让 OverlayResultRenderer 把"卡片期望高度"暴露给 Service
```kotlin
// OverlayResultRenderer.kt 加一个常量
object OverlayResultRenderer {
    const val SCAN_CARD_VERTICAL_SPACING = 22  // dp, padding+margin 总和
}

// FloatWindowService 里读这个常量
val adjustedContentH = contentH + dp(OverlayResultRenderer.SCAN_CARD_VERTICAL_SPACING)
```
这样以后改 padding/margin 时不用两边同步。

---

## 给 Codex 的最小修改建议

**优先用方案 C**——加一个 `SCAN_CARD_VERTICAL_SPACING` 常量，`updateFloatHeightAfterRender` 读取它补到 contentH 里。这样：
- 参数在 OverlayResultRenderer 改一次，浮窗高度自动同步
- 不动 buildScanCard 内部结构
- 不动 `bindResizeAndDrag` 的逻辑

**clamp 范围**：从 `(60, 180)` 改到 `(80, 280)`，给 4 选项题留够呼吸空间。

---

## 与之前 BUG 报告的关系

| 之前的 BUG 报告 | 本次新增 |
|---|---|
| `BUG_REPORT_FLOAT_WINDOW.md` (P0-P2 共 4 项) | padding/margin 缺失（22dp） |

本次是上一份"输出框高度计算"修复的**遗漏补完**，不算新 BUG 类别，是数值校准。

---

## 我没验证的项

- **多卡片场景**（OCR 一次识别出 2-3 题）的 padding 累加 —— 上面的 `+10dp topMargin 总和` 是按 4 选项估算，实际可能因选项数变化。Codex 改时建议用 `forEach` 累加，或干脆给 ScrollView 加 `paddingBottom = dp(12)` 让它自己占空。
- **识别框与结果卡重叠**（图 3 现象）—— 看起来浮窗总高度还差点，可能还要再上调 5-10dp。
- **向上显示模式**的同样问题——本次截图都是向下显示，没拍向上显示的 padding 缺失情况。建议回归测试时一起看。

---

_汇报人：WorkBuddy；时间：2026-08-16 21:21_
_文件夹：`docs/bug_reports/spacing/`（按叮叮要求防止多次汇报混在一起）_