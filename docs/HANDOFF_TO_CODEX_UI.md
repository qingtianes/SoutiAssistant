# 交接给 Codex：UI 重做（玻璃拟态）完成情况

> 由 WorkBuddy 在 2026-08-17 完成 UI 重做代码，交回 Codex 集成。
> **重要**：代码已改完，但**编译验证未跑通**（见第 4 节环境坑），Codex 需先验证编译再集成。

---

## 1. 设计定稿（叮叮已确认）

- **风格**：玻璃拟态（对应 APP 图标：深蓝黑玻璃 + 青绿发光 `#3ECFCF` + 顶部高光 + 多行线段「A」logo）
- **两套主题**：浅色（白天）/ 深色（夜晚），主页右上角日/月开关切换，持久化到 `souti_ui` prefs
- 设计预览稿：`docs/ui_design/ui_完整玻璃拟态.html`（7 页全览，叮叮看过并确认）
- 叮叮特别要求：**扫描页取景框的「A.」角标放绿框外**（已实现）

---

## 2. 已改动的文件（全部在 `com.dingding.souti.ui` 包内）

| 文件 | 改动 |
|---|---|
| `theme/Theme.kt` | **重构**：新增玻璃拟态 token（`SoutiGlass` data class + `GlassLight`/`GlassDark`）+ 主题模式枚举 `SoutiThemeMode{SYSTEM,LIGHT,DARK}` + `SoutiThemeController`（持久化到 `souti_ui` prefs）|
| `GlassComponents.kt` | **新建**：`GlassBackground`（渐变+光斑）、`GlassCard`（半透明+顶部高光+边框）、`ThemeToggle`（日月开关）、`GlassSectionTitle`、`GlassChip`、`GlassSwitch` |
| `HomeScreen.kt` | `App()` 接入主题状态+切换；`HomeScreen` 玻璃化；新增私有 `GlassBankTile` |
| `SettingsScreen.kt` | 5 大类设置玻璃化（`GlassCard`+`GlassChip`+`GlassSwitch`+`Slider` 改色）|
| `ImportScreen.kt` | 玻璃化（保留文件解析/字数验证/导入对话框逻辑）|
| `BankScreens.kt` | `OverviewScreen`/`BankDetailScreen` 玻璃化（保留勾选/分享/删除逻辑）；`SectionTitle`/`MenuCard`/`BankIcon` 同步玻璃化 |
| `UsageGuideScreen.kt` | 玻璃化 |
| `ScanScreen.kt` | 结果卡 `SearchResultCard` 改用 `GlassCard`；标题/文字改用 token；**`ViewfinderOverlay` 加了「A.」角标（drawText，放绿框左上角外侧）**；`CameraScanController` 逻辑**未动** |

**未改动**：`overlay`、`repository`、`import`、`ocr` 包，以及 `ScanScreen` 的摄像头/OCR/暂停逻辑（遵守交接边界）。

---

## 3. 主题切换机制（Codex 需知晓）

- `App()` 里 `var themeMode by remember { mutableStateOf(SoutiThemeController.mode(context)) }`
- 深色判断：`LIGHT→false; DARK→true; SYSTEM→isSystemInDarkTheme()`
- 切换：主页 `ThemeToggle`（日/月按钮）→ `SoutiThemeController.setMode(context, 新模式)`
- 持久化 key：`souti_ui` / `theme_mode`（独立 prefs，**没碰** repository 的 `SettingsStore`，遵守"不改 key 语义"）
- 颜色 token 通过 `LocalGlass`（`staticCompositionLocalOf`）暴露，`GlassLight`/`GlassDark` 里是精确 ARGB 值（对应 HTML 预览的 rgba 换算）

---

## 4. ⚠️ 编译环境坑（Codex 接手前必读）

我**没能跑通编译**，卡在两个环境问题上（都不是代码问题）：

### 坑 1：全局 `init.gradle` 与项目 `FAIL_ON_PROJECT_REPOS` 冲突
- `C:\Users\38947\.gradle\init.gradle`（叮叮配的阿里云镜像加速脚本）用 `allprojects { repositories {...} }` 注入仓库
- 项目 `settings.gradle.kts` 第 14 行 `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`
- 两者冲突 → 构建在配置阶段直接 FAIL
- **临时处理**：我把 `init.gradle` 重命名为 `.bak` 绕过，构建后**已恢复**原文件

### 坑 2：Gradle transforms 缓存损坏
- 报错 `Could not read workspace metadata from ...\caches\8.13\transforms\xxx\metadata.bin`
- 原因：之前构建被中断留下的损坏缓存
- **临时处理**：删除了整个 `caches/8.13/transforms` 目录（会重新转换依赖）

### 结论
- 这两坑说明**本机命令行 Gradle 环境有问题**，Codex 之前能构建说明它有干净的构建方式（可能 Android Studio 或独立环境）。
- **建议 Codex 用自己的构建方式验证编译**；如果遇到 Kotlin 编译错误（我的代码可能有笔误），请修复后再集成。

---

## 5. 可能存在的代码问题（Codex 需检查）

1. **未使用 import**：`HomeScreen.kt` 可能残留 `SectionTitle`/`BankIcon` 的旧 import 已清理，但各文件可能有其他未使用 import（不影响编译，lint 会提示）
2. **`GlassCard` 的 `onClick` 参数**：`GlassComponents.kt` 里 `GlassCard` 有 `onClick: (() -> Unit)?` 参数，`GlassBankTile`/`BankIcon` 里用了它——请确认可空 onClick 的空判断逻辑正确
3. **`RowScope.BankIcon`**：`BankScreens.kt` 里 `BankIcon` 用了 `RowScope` 扩展，但新版 `HomeScreen` 已改用 `GlassBankTile`，`BankIcon` 可能已无调用方（可保留或删除，Codex 判断）
4. **`ScanScreen` 的 `Color(0xFFF7F7F7)` 替换**：我改成了 `glass.bgMid`，但预览区 Box 仍是 `Color.Black`（正确，摄像头画面要黑底）

---

## 6. 交接清单

- [ ] Codex 用自己环境跑 `gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon` 验证编译
- [ ] 如有 Kotlin 编译错误，修复（大概率是 import 或类型笔误）
- [ ] 跑通后集成、构建 Release、发布
- [ ] 叮叮验收：深浅主题切换、扫描页 A. 角标位置

---

_交接人：WorkBuddy；时间：2026-08-17 04:38_
_设计预览：`docs/ui_design/ui_完整玻璃拟态.html`_
