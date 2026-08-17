# 鸿蒙版推进进度（H0 基线恢复）

## 重要纠正

此前文档把 HarmonyOS 错误描述成“图片 OCR + TXT 题库 MVP”，并把 Android 的完整功能范围遗漏了。现在以 Android v1.0.2 为唯一产品基准，完整对照以 `docs/PARITY_MATRIX.md` 为准。

## 当前事实

- Android 基线：`E:\SoutiAssistant`，v1.0.2。
- Harmony 工作区：`E:\SoutiAssistant\HarmonyOS`。
- TXT 题库导入/启停/删除/持久化/手动匹配已有模拟器证据，但只是完整题库系统的一部分。
- 智能导入、题库总览、题库详情、完整设置、浮窗搜题、读屏搜题和 Android UI 仍未复刻完成。
- 摄像头扫描已按用户决定冻结到最后阶段；不得自动申请权限或自动采集。
- 普通构建不注册静态悬浮窗页面，也不申请 `SYSTEM_FLOAT_WINDOW`。

## H0 已完成

1. 修正 Harmony 长期项目记忆和决策；
2. 建立 Android→Harmony `PARITY_MATRIX.md`；
3. 建立 `SESSION_HANDOFF.md`；
4. 创建并验证 `souti-parity-development` Skill；
5. 记录摄像头隐私测试踩坑；
6. 冻结摄像头入口和页面注册；
7. 更新 README、使用说明和 changelog 方向。

## H0 待完成

1. 完成矩阵与 Android 源码的逐项复核；
2. 修正 README/使用说明中的所有状态措辞；
3. 构建与静态审查通过；
4. 确认正确 Git 远程地址；
5. 在 main 提交并 push H0 检查点。

## 后续顺序

1. H1：首页、导航和 UI 骨架；
2. H2：智能导入、题库总览和题库详情；
3. H3：完整设置与使用说明；
4. H4：图片 OCR；
5. H5：浮窗搜题能力探针和实现；
6. H6：读屏搜题能力探针和实现；
7. H7：摄像头扫描，用户明确授权后最后开发；
8. H8：用户真机最终验收。


## H1 (2026-08-18)

- 首页/导入/总览/详情/设置骨架已建立并对齐 Android 信息架构，构建通过。
- 摄像头仍冻结；H2 开始完整智能导入与题库总览。
