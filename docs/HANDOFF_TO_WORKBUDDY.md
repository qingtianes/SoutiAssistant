# 交接给 WorkBuddy：UI 重做说明（v1.0.2）

> 用途：WorkBuddy 接手重做 UI 前，先读本文件，避免停留在旧版本认知。
> 生成时间：2026-08-17

## 1. 当前状态
- 项目路径：`E:\SoutiAssistant`
- 当前版本：v1.0.2（已发布 GitHub Release）
- 分支：main
- 构建通过：单元测试 ✅、Lint 0 error ✅、Release 签名 ✅

## 2. 构建与验证
```powershell
cd E:\SoutiAssistant
$env:JAVA_HOME='E:\Huawei\DevEco Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
```
发布包：
```powershell
.\gradlew.bat assembleRelease --no-daemon
```
- 输出：`app\build\outputs\apk\release\`，命名规范 `SoutiAssistant-vX.Y.Z-release.apk`

## 3. UI 重做边界（重要）
- **只改 `com.dingding.souti.ui` 包内的排版、样式、文案与导航结构。**
- **不改**：`overlay`（悬浮窗/读屏/输出窗）、`repository`、`import`、`ocr`、`ScanScreen` 里的摄像头/OCR/暂停状态逻辑。
- 设置项已通过 `SettingsStore` 全局生效，UI 只能读取，不能改 key 语义。

## 4. 当前 UI 文件清单
| 文件 | 职责 |
|---|---|
| `ui/MainActivity.kt` | 入口，定义 Green/Red 颜色 |
| `ui/HomeScreen.kt` | `App()` 导航 + 主页（B 极简卡片流） |
| `ui/SettingsScreen.kt` | 设置中心 5 大类 |
| `ui/UsageGuideScreen.kt` | 使用说明 |
| `ui/ScanScreen.kt` | 摄像头扫描（含取景框、缩放、暂停/继续） |
| `ui/ImportScreen.kt` | 题库导入 |
| `ui/BankScreens.kt` | 题库总览/详情，含 SectionTitle/MenuCard/BankIcon |

## 5. 导航结构（App() 内的 screen 字符串）
- `home` → HomeScreen
- `import` → ImportScreen
- `overview` → OverviewScreen
- `bank` → BankDetailScreen
- `scan` → ScanScreen
- `settings` → SettingsScreen
- `help` → UsageGuideScreen（返回 home）

## 6. 当前主页约定（v1.0.2）
- 顺序：标题栏（右上角 ⓘ 使用说明）→ 题库 → 快捷搜题 → 设置
- 风格：白卡片 18dp 圆角、浅灰底 #F7F7F7、品牌绿 #1D9E75 只用于状态点/主按钮
- 副标题：`浮窗 · 读屏 · 摄像头 · 本地题库`

## 7. 使用说明顺序（APP 与 README 一致）
1. 题库导入 → 2. 浮窗搜题 → 3. 读屏搜题 → 4. 扫描搜题 → 5. 设置

## 8. 不要动的关键实现
- 浮窗/读屏互斥逻辑在 `FloatWindowService`。
- OCR 截屏隐私：本机处理、allowBackup=false。
- 设置存储：`repository/SettingsStore.kt` 与 `SettingsLogic.kt`。

## 9. 交接给 WorkBuddy 的建议流程
1. 先读 `docs/PROJECT_CONTEXT.md`、`docs/DECISIONS.md`、本文件。
2. 只提交 `ui` 包改动；提交前跑上面的回归命令。
3. 改完把改动的文件清单与截图发回，由 Codex 做最终集成、构建与发布。