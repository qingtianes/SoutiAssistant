# 搜题助手 · 鸿蒙版（Souti Assistant for HarmonyOS）

> 基于 OCR 的题库搜题工具 — 鸿蒙原生版（ArkTS）。安卓版见 `E:\SoutiAssistant`（Kotlin，v0.5）。

**当前状态**：🟡 早期 MVP（2026-08-07 创建工程，DevEco Studio）

---

## 📌 当前进度

| 模块 | 状态 | 说明 |
|---|---|---|
| 工程骨架 | ✅ | DevEco 标准结构，`deviceTypes: phone`，API 版本见 `build-profile.json5` |
| 系统悬浮窗 | ✅ | `FloatWindowManager`：TYPE_FLOAT 创建/关闭/移动/取位置，全局单例 |
| 主页控制台 | ✅ | `Index.ets`：启停悬浮窗 + 运行状态圆点 |
| 题库管理 | ✅ | `QuestionBank`：模型 + 导入 + 激活题库切换 + LCS 搜索 + txt 解析 + Preferences 存储（**当前最成熟模块**） |
| 浮窗内容页 | 🟡 | `FloatPage.ets`：可拖动 + 识别区虚线框 + 状态栏；识别状态写死「待机中」，标注"后续接 OCR" |
| 相机拍照 | 🟡 | `CameraHelper`：预览 + 定时拍照 + 写沙箱 + `onPhotoTaken` 回调，**回调无人消费，未闭环** |
| OCR 识别 | ❌ | `oh-package` 依赖为空，未引入任何视觉/OCR 引擎 |
| 题库导入 UI | ❌ | 模型齐了，主页无导入入口 |
| 老板键 | ❌ | 对齐安卓 v0.5（缩成 28dp 红 +） |
| 读屏搜题 / AI 搜题 | ❌ | 对齐安卓路线，待开发 |

## 🧱 架构与文件职责

```
E:\SoutiAssistant_Harmony\
├── entry/src/main/ets/
│   ├── pages/
│   │   ├── Index.ets            # 主页控制台：启停悬浮窗、状态指示
│   │   └── FloatPage.ets        # 悬浮窗内容页：识别区 + 拖动 + 状态栏（MVP 占位）
│   ├── common/
│   │   ├── FloatWindowManager.ets  # 悬浮窗生命周期（创建/关闭/移动/取位置）
│   │   └── CameraHelper.ets        # 相机拍照（识别方案候选，待定）
│   ├── model/
│   │   └── QuestionBank.ets        # 题库模型 + LCS 匹配 + txt 解析 + Preferences 存储
│   ├── entryability/EntryAbility.ets  # 应用入口，加载 pages/Index
│   └── entrybackupability/          # 系统备份扩展（模板）
├── entry/src/main/module.json5      # 权限：SYSTEM_FLOAT_WINDOW + CAMERA
├── AppScope/  hvigor/  oh_modules/  # 工程/构建/依赖
└── build-profile.json5  oh-package.json5  # SDK 版本与依赖声明
```

## 🔗 与安卓版对齐（防止逻辑漂开）

安卓版 v0.5 已沉淀的核心决策，鸿蒙版要继承或换型的部分：

| 能力 | 安卓版做法 | 鸿蒙版 | 状态 |
|---|---|---|---|
| 题库匹配 | LCS 最长公共子串 + 完全包含 +100 | 已移植 `QuestionBank.ets` | ✅ |
| txt 解析 | 序号/空行/选项对齐三格式兼容 | 已移植 `parseTxt`（空行分隔版） | ✅ |
| 截屏 | MediaProjection + VirtualDisplay（长期复用） | **需换型**：`window.snapshot` / `componentSnapshot` | ⏳ |
| OCR | ML Kit `text-recognition-chinese`（Hani_ctc） | **需换型**：Core Vision `textRecognition`（`@kit.CoreVisionKit`，API 以 SDK 版本核对） | ⏳ |
| 老板键 | 缩成 28dp 红 +，不销毁 Service | 待做：缩窗 + 保持悬浮窗/识别会话 | ⏳ |
| 按钮三态 | 🔓授权并扫描 → ⏸暂停 → ▶继续 | 待做 | ⏳ |
| 异步防乱序 | OCR 回调带序列号，丢弃过期结果 | 待做（接 OCR 时同步引入） | ⏳ |
| 关闭语义 | ✕ 仅最小化；「关闭服务」才销毁 | 当前 FloatPage ✕ 直接销毁，后续对齐 | ⏳ |

## 🧭 待办清单（按优先级）

### P0 · 打通识别闭环（缺它就不算"搜题助手"）
1. **定识别方案**：鸿蒙上「对准屏幕自动识别」应走 **截屏 + Core Vision 文本识别**；`CameraHelper` 摄像头方案只适合"拍实物题目"，两者择一（建议截屏，与安卓体验一致）。
2. 引入 OCR：按选定的 kit 补依赖/导入（原生 kit 一般无需三方包），封装 `OcrHelper`。
3. `FloatPage` 状态机接入：`待机中 → 识别中 → 识别到题干 → 匹配结果`，替换写死的 statusText。
4. 接闭环：截屏（或 `onPhotoTaken`）→ OCR → `QuestionBank.search` → 结果卡片渲染。

### P1 · 补全 MVP
5. 题库导入 UI（主页入口 + 文件选择 + `parseTxt`）。
6. 结果卡片区（题干 + 选项 + 答案 + 来源，可滚动，参考安卓 4 模块布局）。
7. 老板键 + 按钮三态（对齐安卓 v0.5）。
8. 异步 OCR 回调带序列号，防乱序覆盖（移植安卓踩坑经验）。

### P2 · 进阶
9. 读屏搜题（录屏实时检测全屏题目）。
10. AI 搜题（在线大模型）。
11. `开发笔记.md` / `踩坑记录.md`（对齐安卓版文档习惯，本文件是起点）。
12. CI：hvigor 自动构建 HAP（对齐安卓 GitHub Actions）。

## ⚠️ 已知卡点

- **`SYSTEM_FLOAT_WINDOW` 权限需 AGC 申请**（企业/特殊资质）。当前依赖 `trial debug profile + DevEco 自动签名` 临时运行，正式发布要走 AGC 申请流程。
- `CAMERA` 权限：若最终方案定为截屏，可从 `module.json5` 移除。

## 🔧 构建说明

1. DevEco Studio 打开 `E:\SoutiAssistant_Harmony`
2. 同步工程（首次需下载 SDK/依赖）
3. 配置自动签名（trial debug profile）
4. Run → entry → 真机（需鸿蒙手机，API 版本满足工程要求）

## 📜 版本历史

- **v0.1-mvp**（2026-08-07）— 工程骨架 + 悬浮窗框架 + 题库模型；浮窗内容占位，OCR 未接

## 🔄 接管后更新（分支 codex/takeover-20260815）

- 模块边界已对齐安卓：
  - `model`：题库模型
  - `repository`：题库存取
  - `service`：搜索编排与匹配算法
  - `import`：TXT 文件选择读取
  - `ocr`：官方 textRecognition
  - `picker`：PhotoViewPicker
  - `overlay`：悬浮窗管理
  - `camera`：相机能力（未接入）
  - `ui`：主页面
- 已完成：图片选择 → 官方 OCR → 本地题库搜索 → TXT 题库导入/启用/删除。
- 题库存储改为应用私有 JSON，关闭系统备份。
- 构建验证：`hvigor assembleHap` 成功（未签名）。
- 待办：真机验证中文 OCR、相册 URI 读取、DevEco 自动签名。
