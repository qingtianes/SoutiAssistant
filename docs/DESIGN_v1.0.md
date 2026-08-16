# 搜题助手 v1.0 技术设计

## 1. 设置存储
- 新增 `repository/SettingsStore.kt`（object），统一用 SharedPreferences `souti_settings` 存取。
- 提供类型安全 getter/setter，所有设置读取一律经 SettingsStore，禁止散落硬编码 key。
- 默认值与 PRD 保持一致。

## 2. 页面结构
- `HomeScreen.kt` 的 `App()` 增加 `"settings"` 路由。
- 主页“设置”区块改为单个入口，点击进入 `SettingsScreen`。
- `SettingsScreen` 5 个 Section：权限管理 / 识别与匹配 / 浮窗显示 / 扫描搜题 / 通用关于。

## 3. 设置生效点
| 设置 | 生效位置 |
|---|---|
| 最多显示结果、最低匹配分 | 扫描搜题搜索调用、浮窗/读屏搜索调用 |
| 识别速度 | 扫描 CameraScanController 节流、FloatWindowService 节流 |
| 结果字号、显示匹配分/来源 | OverlayResultRenderer 结果卡片 |
| 绿框默认大小 | StandbyUiBuilder 默认识别区尺寸 |
| 默认缩放、取景框高度 | ScanScreen / CameraScanController |

## 4. 暂停/继续状态机
- `ScanScreen` 内 `paused: Boolean` 状态。
- 暂停时：OCR 回调忽略新文本，结果列表与识别文字保持不变。
- 继续时：恢复正常更新。
- 按钮文案随状态切换。

## 5. 数据与隐私
- OCR 使用本地 ML Kit 中文离线识别。
- 截屏与摄像头帧仅在内存中处理，不写共享存储、不联网。
- 清空缓存调用 `QuestionRepository.clear()` + `SettingsStore.reset()`。

## 6. 测试策略
- 单元测试：SettingsStore 默认值与读写、分数过滤、速度映射、取景框比例映射。
- 回归：QuestionMatcher、OcrQuestionProcessor、Importer 既有测试保持通过。
- UI/真机：设置页切换、暂停/继续、权限跳转。
- Lint 0 error。
