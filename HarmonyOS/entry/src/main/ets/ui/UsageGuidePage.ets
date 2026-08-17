/**
 * 使用说明（对齐 Android UsageGuideScreen 的完整流程顺序）。
 */
import { router } from '@kit.ArkUI';

@Entry
@Component
struct UsageGuidePage {
  @Builder
  private stepCard(title: string, steps: string[]) {
    Column({ space: 6 }) {
      Text(title)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor('#222222')
      ForEach(steps, (step: string, index: number) => {
        Text(`${index + 1}. ${step}`)
          .width('100%')
          .fontSize(13)
          .fontColor('#444444')
          .lineHeight(20)
      }, (step: string, index: number) => `${index}`)
    }
    .width('100%')
    .padding(14)
    .borderRadius(12)
    .backgroundColor(Color.White)
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column({ space: 10 }) {
      Row() {
        Text('使用说明')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
        Blank()
        Button('返回')
          .height(32)
          .fontSize(13)
          .backgroundColor('#1D9E75')
          .onClick(() => router.back())
      }
      .width('100%')
      .height(56)
      .padding({ left: 16, right: 16 })
      .backgroundColor('#1D9E75')

      Scroll() {
        Column({ space: 10 }) {
          this.stepCard('1. 题库导入', [
            '首页“题库”→“智能导入”，选择 txt / docx / pdf / xls 文件。',
            '导入完成后进入“题库总览”，勾选要参与搜题的题库。',
            '当前鸿蒙版 TXT 已可用；DOCX/PDF/XLS 正在完整复刻中。'
          ])
          this.stepCard('2. 浮窗搜题', [
            '首页点“浮窗搜题”，按提示授权悬浮窗与录屏。',
            '拖动/缩放绿框圈住题目，自动识别。',
            '答案显示在独立输出窗：标题栏可拖动，内容区可滚动。',
            '当前鸿蒙普通应用无法获得系统悬浮窗权限，此项待平台能力验证。'
          ])
          this.stepCard('3. 读屏搜题', [
            '首页点“读屏搜题”，授权悬浮窗与录屏。',
            '全屏自动识别多题，答案按顺序显示在右上角小窗。',
            '当前鸿蒙读屏能力待真机验证。'
          ])
          this.stepCard('4. 扫描搜题', [
            '首页点“扫描搜题”，授权摄像头。',
            '把题目对准绿色取景框，只识别框内内容。',
            '双指捏合或右下角 +/− 缩放画面，支持暂停/继续。',
            '摄像头功能属于后置开发，当前版本不启动摄像头。'
          ])
          this.stepCard('5. 设置', [
            '首页“设置”可管理权限、识别与匹配、浮窗显示、扫描搜题等。'
          ])
        }
        .width('92%')
        .padding({ top: 16, bottom: 30 })
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Auto)
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F7F7F7')
  }
}
