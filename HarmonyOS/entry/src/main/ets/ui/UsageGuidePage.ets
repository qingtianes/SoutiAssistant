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
    Column() {
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
      .alignItems(VerticalAlign.Center)
      .backgroundColor('#1D9E75')

      Scroll() {
        Column({ space: 10 }) {
          this.stepCard('1. 题库导入', [
            '点“导入 TXT”选择 UTF-8 编码的题库文件。',
            '导入成功后自动启用，可在题库列表中停用或删除。'
          ])
          this.stepCard('2. 图片文字识别', [
            '点“选择图片”从相册选一张题目图片。',
            '点“开始识别”，设备本机 OCR 识别文字。',
            '识别出的文字可手动修改。'
          ])
          this.stepCard('3. 搜索答案', [
            '确认已启用题库，点“搜索本地题库”。',
            '结果按相关度从高到低显示。'
          ])
          this.stepCard('4. 扫描搜题', [
            '扫描搜题属于后置开发功能，当前版本暂不启动摄像头。',
            '待完成隐私确认、平台适配和真机验证后再开放。'
          ])
          this.stepCard('5. 设置', [
            '首页右上角“设置”可查看版本、隐私说明，或清空本地题库。'
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
