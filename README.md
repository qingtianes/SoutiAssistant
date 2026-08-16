# 搜题助手（Souti Assistant）

> 基于 OCR 的题库搜题工具：浮窗实时识别框内题目、读屏全屏多题识别，匹配本地题库后输出答案。

## 当前版本

- **功能版本**：`v0.7-screen-read-stable`
- **重构版本**：结构拆分，不改变功能行为
- **当前分支**：`main`（接管工作已合并）

## 功能

| 功能 | 说明 | 状态 |
|---|---|---|
| 浮窗搜题 | 悬浮窗绿框实时 OCR 框内题目，输出单题答案 | 完成 |
| 读屏搜题 | 全屏实时 OCR，多题按顺序输出到独立小窗 | 完成 |
| 扫描搜题 | 摄像头扫描题目出答案 | 待开发 |
| 题库导入 | `.txt / .docx / .pdf / .xls`，统一解析题干、选项、答案 | 完成 |
| 智能匹配 | LCS 最长公共子串 + 完全包含打分 | 完成 |
| 悬浮窗 | 可拖拽、缩放、最小化老板键 | 完成 |

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
├── repository/  QuestionBank、QuestionRepository、QuestionMatcher
├── import/      Importer、FileFormatDetector、BankChunker、Txt/Docx/Pdf/Xls 解析器
├── ocr/         OcrBridge、OcrHelper、OcrQuestionProcessor
├── overlay/     FloatWindowService 及拆出的 8 个组件
└── ui/          MainActivity、HomeScreen、ImportScreen、BankScreens
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

## 构建与验证

- 构建前设置 `JAVA_HOME` 到 JDK 21（本机：`E:\Huawei\DevEco Studio\jbr`）
- 回归命令：`gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon`
- 当前：40 项单元测试通过，Lint 0 error，可打包 debug APK

## 版本历史

- `v0.7-screen-read-stable`：读屏搜题多题模式稳定
- `v0.6`：版本号与文档同步
- `v0.5`：浮窗 4 模块结构重构、老板键
- 接管重构：包结构拆分，功能行为不变，补充测试与文档
