# 搜题助手（Souti Assistant）

> 基于 OCR 的题库搜题工具：浮窗实时识别框内题目、读屏全屏多题识别，匹配本地题库后输出答案。

## 当前版本

- **当前版本**：`v1.1.1`
- **重构版本**：结构拆分，不改变功能行为
- **当前分支**：`main`（接管工作已合并）

## 功能

| 功能 | 说明 | 状态 |
|---|---|---|
| 浮窗搜题 | 悬浮窗绿框实时 OCR 框内题目，输出单题答案 | 完成 |
| 读屏搜题 | 全屏实时 OCR，多题按顺序输出到独立小窗 | 完成 |
| 扫描搜题 | 实时摄像头扫描 + 横向取景框 + 缩放，只识别框内题目 | 完成 |
| 题库导入 | `.txt / .docx / .pdf / .xls`，统一解析题干、选项、答案 | 完成 |
| 智能匹配 | LCS 最长公共子串 + 完全包含打分 | 完成 |
| 设置中心 | 权限 / 识别匹配 / 浮窗显示 / 扫描 / 关于 | 完成 |
| 悬浮窗 | 可拖拽、缩放、最小化老板键 | 完成 |

## 使用说明

> App 内入口：首页右上角 ⓘ 图标；GitHub 说明如下。

### 1. 题库导入
1. 首页“题库”→“智能导入”，选择 txt / docx / pdf / xls 文件。
2. 在“题库总览”勾选要参与搜题的题库。

### 2. 浮窗搜题
1. 首页点“浮窗搜题”，授权悬浮窗与录屏。
2. 拖动/缩放绿框圈住题目，自动识别。
3. 答案显示在独立输出窗：标题栏可拖动，内容区可滚动。
4. 标题栏“—”最小化浮窗。

### 3. 读屏搜题
1. 首页点“读屏搜题”，授权悬浮窗与录屏。
2. 全屏自动识别多题，答案按顺序显示在右上角小窗。
3. 小窗可拖动/缩放，右上角 ✕ 关闭。

### 4. 扫描搜题
1. 首页点“扫描搜题”，授权摄像头。
2. 把题目对准绿色取景框，只识别框内内容。
3. 双指捏合或右下角 +/− 缩放画面。
4. 下方显示答案，点“暂停”锁定结果，点“继续”恢复扫描。

## 设备兼容

- Android 8.0 及以上（minSdk 26，targetSdk 33）
- CPU：arm64-v8a / armeabi-v7a / x86_64
- 鸿蒙 HarmonyOS：可通过卓易通运行安卓版（录屏授权偶发不启动，见 docs/KNOWN_ISSUES.md）
- 权限：悬浮窗、录屏、摄像头、通知、前台服务

## 技术栈

- Kotlin + Jetpack Compose（主页）+ 传统 View（悬浮窗）
- Google ML Kit 中文 OCR
- MediaProjection + VirtualDisplay + ImageReader
- SharedPreferences（JSON 题库）
- Android 13+（targetSdk 33）

## 项目结构

```text
app/src/main/java/com/dingding/souti/
├── model/       Bank、Question、SearchResult
├── repository/  QuestionBank、QuestionRepository、QuestionMatcher、SettingsStore、SettingsLogic
├── import/      Importer、FileFormatDetector、BankChunker、Txt/Docx/Pdf/Xls 解析器
├── ocr/         OcrBridge、OcrHelper、OcrQuestionProcessor
├── overlay/     FloatWindowService 及拆出的组件
└── ui/          MainActivity、HomeScreen、ImportScreen、BankScreens、ScanScreen、SettingsScreen、UsageGuideScreen
```

## FloatWindowService 已拆组件

- `FrameImageUtils`：帧差异、亮度、反色
- `OverlayResultRenderer`：结果卡片渲染
- `ServiceNotificationHelper`：前台通知
- `OverlayDragResizer`：读屏小窗拖拽/缩放
- `ProjectionVirtualDisplayFactory`：VirtualDisplay + ImageReader 创建
- `SearchUiBuilder`：搜题界面
- `ScreenReadWindowBuilder`：读屏小窗
- `StandbyUiBuilder`：待机主界面
- `OutputWindowBuilder`：独立输出窗构建

## 构建与验证

- 构建前设置 `JAVA_HOME` 到 JDK 21（本机：`E:\Huawei\DevEco Studio\jbr`）
- 回归命令：`gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon`
- 单元测试通过，Lint 0 error
- 发布包命名规范：`SoutiAssistant-vX.Y.Z-release.apk`

## 版本历史

- `v1.1.1`：主页使用说明按钮样式对齐主题切换按钮
- `v1.1.0`：WorkBuddy 玻璃拟态 UI 重做（浅色/深色主题切换、扫描页 A. 角标）
- `v1.0.2`：主页题库区置顶、首页右上角 ⓘ 使用说明入口、使用说明顺序调整
- `v1.0.1`：新增 App 内使用说明、README 使用说明
- `v1.0.0`：完善设置中心、扫描搜题暂停/继续、设置项全局生效
- `v0.11.2-transparent-cards`：扫描结果卡片透明化，只留淡轮廓，答案清晰显示
- `v0.11.1-scan-fill`：摄像头预览填满窗口（FILL_CENTER 居中裁剪），解决竖屏缩成窄条
- `v0.11.0-scan-viewfinder`：扫描搜题新增横向取景框、双指/按钮缩放、仅识别框内
- `v0.10.1-scan-fix`：扫描页摄像头画面固定比例并裁剪、新增关闭按钮
- `v0.10.0-output-window`：输出结果独立悬浮窗、常驻标题栏、可自由拖动定位
- `v0.9.0-camera-scan`：实时摄像头扫描搜题
- `v0.8.0-refactor`：结构重构、包结构拆分、补测试与文档
- `v0.7-screen-read-stable`：读屏搜题多题模式稳定
- `v0.6`：版本号与文档同步
- `v0.5`：浮窗 4 模块结构重构、老板键
- 接管重构：包结构拆分，功能行为不变，补充测试与文档

## HarmonyOS 子项目

HarmonyOS 版本已经纳入当前 Android 主仓库：

```text
HarmonyOS/
```

目标不是另一个简化应用，而是以 Android v1.0.2 为基准完整复刻：

- 首页与导航；
- 智能导入与题库总览；
- 图片 OCR；
- 浮窗搜题；
- 读屏搜题；
- 摄像头扫描搜题；
- 设置与使用说明；
- UI、交互状态和本地数据行为。

HarmonyOS 的当前状态、缺口、决策和交接入口：

```text
HarmonyOS/docs/PROJECT_CONTEXT.md
HarmonyOS/docs/PARITY_MATRIX.md
HarmonyOS/docs/DECISIONS.md
HarmonyOS/docs/TASKS.md
HarmonyOS/docs/SESSION_HANDOFF.md
```

构建鸿蒙模块：

```powershell
$env:DEVECO_SDK_HOME='E:\Huawei\DevEco Studio\sdk'
$env:JAVA_TOOL_OPTIONS='-Xms128m -Xmx1536m'
$env:JAVA_HOME='E:\Huawei\DevEco Studio\jbr'
$env:PATH='E:\Huawei\DevEco Studio\jbr\bin;E:\Huawei\DevEco Studio\tools\node;' + $env:PATH
& 'E:\Huawei\DevEco Studio\tools\hvigor\bin\hvigorw.bat' --no-daemon assembleHap --mode module -p product=default
```
