# 浮窗结果框 4 项 BUG 汇报（WorkBuddy 实测，2026-08-16 晚）

> 由叮叮提供 5 张截图、WorkBuddy 读代码定位根因、给 Codex 的修复汇报。
> **优先级**：P0 / P1 必修；P2 建议一并修。
> **截图原件**：`docs/bug_reports/clipboard-2026-08-16T11-59-24-*.png`（5 张，按时间顺序 16:27 → 22:51）

---

## TL;DR（一句话）
`FloatWindowService.kt` 里浮窗高度公式是写死的 `topBar + greenH + 固定 180dp 输出框`，输出框内容用 ScrollView 但**高度被 `coerceIn(60dp, 180dp)` 强制截断**，加上 resize 时**只改 height 不改 y**，导致 4 个连锁 BUG。

---

## BUG 链条（一图看完）

```
场景：浮窗搜题，方向=向下显示，OCR 循环中

启动 → 浮窗高度 = 28 + 150 + 180 = 358dp       ← 一切正常
↓
OCR 扫不到题 → ScrollView 内容高度 ≈ 30dp
   → updateFloatHeightAfterRender() clamp 到 60dp
   → "未匹配到题库题目" 单行 ok
↓
OCR 扫到题 → renderScanResults(...)
   → 异步 post 测量 contentH
   → 此时容器可能还是"未匹配"高度，或正在重新布局
   → 测量结果被 clamp 在 60~180dp 之间
   → 结果卡 C/D 选项 + 答案 + 相关度 全部被 ScrollView 截断        ← BUG 1
↓
用户拖大识别框（绿框）
   → bindResizeAndDrag 重算 targetH = 28 + newH + 180
   → 输出框被重置为 180dp
   → 看起来"识别框拖大了，输出框也跟着变大了"                     ← BUG 2
   （实际是输出框被覆盖回 180dp，不是联动）

切换到 "向上显示"
   → 布局顺序：topBar → 结果区 → 识别框
   → 同样的 clampIn(60, 180)，结果区初始 60dp
   → 识别框被挤到下面，部分内容被结果区压住                         ← BUG 3
↓
向上显示状态下再拖大识别框
   → bindResizeAndDrag：p.height = targetH（变大），p.y 不变
   → 浮窗顶部锚定在原 y，浮窗底部往下扩展
   → 浮窗内部 layout：topBar(不变) → 结果区 ScrollView(变大) → 识别框(位置被推到下面)
   → OCR 读取的实际屏幕坐标 = recognizeArea.top + p.y，跟着识别框下移
   → 下次扫描 OCR 落到屏幕下方别的题 → 输出又是另一题                 ← BUG 4（最严重）
```

---

## 截图证据（按时间顺序）

| # | 时间 | 模式 | 现象 | 对应 BUG |
|---|---|---|---|---|
| 1 | 16:27 | 向下显示 | 扫描不到题，输出框"未匹配到题库题目"占 1 行（高度约 60dp 起步） | BUG 1 前置 |
| 2 | 17:26 | 向下显示 | 扫到题，结果卡 C/D 选项+答案+相关度**全被截断**（只看到 A/B 选项和"来源：sbs_bank"） | **BUG 1** |
| 3 | 19:17 | 向下显示 | 用户把识别框**拖大**了，结果框也**跟着变大**（4 个选项 + 答案 + 相关度 10 + 第二题开头都能显示） | **BUG 2** |
| 4 | 21:36 | 向上显示 | 初始未匹配，结果框高度过小，识别框被挤到下方只剩半行 | **BUG 3** |
| 5 | 22:51 | 向上显示 | 拖大识别框后，识别框**漂移到屏幕中下部**，**且识别的题变成"塔顶压力"**（结果区是"塔釜温度"），OCR 实际读到屏幕别处 | **BUG 4（最严重）** |

---

## 代码根因（已定位到具体行）

### 公共背景：`FloatWindowService.kt`
默认高度公式在 `showFloatWindow()` 第 188-198 行注释里写明：
```
topBar(28) + topSpace(0) + 绿框(150) + 输出框(180) = 358dp
```

### BUG 1+3 根因：`updateFloatHeightAfterRender` (第 1516-1543 行)
```kotlin
// 第 1525 行：
val desired = contentH.coerceIn(dp(60), dp(180))   // ← 这里是关键

// 第 1530 行：
val scrollH = minOf(desired, containerRemaining).coerceAtLeast(dp(60))

// 第 1537 行：
val actualH = dp(28) + dp(0) + greenH + ocrStatusH + scrollH
```
- 输出框 ScrollView 高度被**硬 clamp 在 [60dp, 180dp]**
- 内容（题干+4 选项+答案+来源）需要约 200~260dp，**永远溢出**显示不下
- "未匹配"状态 ScrollView 60dp；"匹配到"状态 ScrollView 仍被 clamp 在 ≤180dp，结果卡下半截被吞
- **官方注释也提到这一段**，但 clamp 范围偏小，没给题干+4 选项留够空间

### BUG 2 根因：`bindResizeAndDrag` (第 1603-1667 行)
```kotlin
// 第 1654 行：
val targetH = dp(28) + dp(0) + newH + ocrStatusH + dp(180)   // ← 输出框写死 180dp

// 第 1658-1660 行：
if (p.height != targetH || p.width != targetW) {
    p.height = targetH
    p.width = targetW
    windowManager.updateViewLayout(root, p)
}
```
- 拖动识别框 resize handle 时，浮窗总高 = topBar + 新绿框高 + **固定 180dp** 输出框
- 用户的错觉："识别框拖大了，输出框也变大"
- **真实机制**：拖大识别框触发了 `windowManager.updateViewLayout` 把 ScrollView 高度从 clamp 的 60dp 重置回 180dp，看起来像"联动"，其实是"重置"
- 而且这个 180dp 还是 clamp 的上限，仍然显示不全内容

### BUG 4 根因：`bindResizeAndDrag` 缺 y 调整 (第 1658-1660 行)
```kotlin
if (p.height != targetH || p.width != targetW) {
    p.height = targetH      // ← height 变了
    p.width = targetW
    windowManager.updateViewLayout(root, p)
    // ❌ 没有 p.y = ... ← 这是核心 BUG
}
```
- 浮窗在 WindowManager 里的位置 `p.x, p.y` 是**窗口左上角在屏幕上的坐标**
- 改 `p.height` 但不改 `p.y` → 窗口**顶部固定在原 y，底部向下扩展**
- 浮窗内部是 LinearLayout(VERTICAL)，子 View 自上而下排列
- 向上显示布局顺序：`topBar` → `结果区 ScrollView` → `识别框 (dragArea)` → `拖拽手柄`
- 结果区 ScrollView 高度变大 → 内部布局重排 → `识别框` 在浮窗内的相对 y 变大 → `识别框` 在屏幕上的实际 y = `p.y + 识别框在浮窗内的 top` 也跟着变大 → **OCR 实际扫描位置下移**
- 截图 5 完美印证：浮窗整体 y 不变，但 OCR 读到屏幕下方"塔顶压力"那道题（结果区是"塔釜温度"）

### 顺带发现：方向切换后的窗口 y 不重置
- `toggleOutputDirection()`（第 247-256 行）调用 `showFloatWindow()` 但没记录方向切换前的 `p.y`
- 实际效果：方向切换会重建浮窗，但**复用旧 params**，所以 y 保留——这个其实是 OK 的，但**新浮窗总高变了**，y 锚点应该相应调整才能让用户感觉"位置没动"。

---

## 修复建议（按优先级）

### P0 — BUG 4（向上显示扫描框漂移）
**最小修复**：`bindResizeAndDrag` 第 1658-1660 行补一段 y 调整：
```kotlin
// 浮窗高度变大时，把窗口向上扩展一半，保留视觉锚点（不要让底部乱跑）
val heightDelta = targetH - p.height
if (heightDelta > 0 && !outputDown) {  // 向上显示模式
    p.y = (p.y - heightDelta).coerceAtLeast(0)  // 顶部上移，让底部仍接近原位置
} else if (heightDelta > 0 && outputDown) {
    // 向下显示模式：底部下扩 OK，顶部不变（识别框位置变化小）
}
```
或者更彻底的方案：**让识别框在浮窗内部的位置独立于结果区高度**——把识别框的 layoutParams 改为固定位置（比如浮窗底部），结果区 ScrollView 用 weight=1 占据中间。这样结果区膨胀不会推识别框。

### P1 — BUG 1+3（结果框高度过小）
**修改**：`updateFloatHeightAfterRender` 第 1525 行把 clamp 上限放大，并允许内部滚动：
```kotlin
// 把上限从 180dp 提到 280dp（容纳题干+4 选项+答案+来源）
val desired = contentH.coerceIn(dp(80), dp(280))
```
同时第 1654 行（resize 时）输出框高度也对应提到 `dp(280)`。

### P2 — BUG 2（识别框与结果框大小联动错觉）
**修复**：让 `bindResizeAndDrag` 第 1654 行**不重置输出框高度**，只调浮窗 height = topBar + 新绿框 + ScrollView 当前高度：
```kotlin
val currentScrollH = ocrResultScroll?.height ?: dp(180)
val targetH = dp(28) + dp(0) + newH + ocrStatusH + currentScrollH
```
这样识别框拖大不会"重置"结果区高度，避免错觉。

### P2（顺带）— 未匹配→匹配时的布局抖动
**修复**：在 `renderScanResults` 第 1465-1510 行的填充逻辑里，**先强制 setVisibility 再 measure**，避免 contentH 拿到旧值；或者在 ScrollView 里塞一个 `minHeight = 200dp` 占位，保证切换时 ScrollView 高度不缩。

---

## 给 Codex 的处理顺序建议
1. 先改 **BUG 4**（最严重，会让用户读到错的题）
2. 接着改 **BUG 1+3**（体验问题，每次匹配都显示不全）
3. 最后改 **BUG 2 + 占位抖动**（视觉一致性）

按项目 "只切蛋糕、不改味道" 原则，建议这 4 个改动都限制在 `FloatWindowService.kt` 一个文件内，不动 `OverlayResultRenderer.kt` / `StandbyUiBuilder.kt` / `SearchUiBuilder.kt`。

---

## 我没验证的项
- 拖大识别框在 **向下显示** 模式下是否会复现 BUG 4 的漂移问题（按代码逻辑应该不会，因为向下显示时识别框在结果区**下方**，结果区膨胀把识别框向下推 → 但向下显示模式下，识别框在浮窗底部，y 推到底部之外就是浮窗底部之外，可能反而看不见识别框了——值得测一次）
- 拖小识别框（dy 为负）时的对称行为（理论上同样有 y 漂移问题）

建议 Codex 改完后用同样的 5 张图场景回归一次（启动→未匹配→匹配→拖大→切换向上→拖大）。

---

_汇报人：WorkBuddy；时间：2026-08-16 22:51（实测截图时间）_