import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';
import promptAction from '@ohos.promptAction';
import { QuestionBank } from '../service/QuestionBank';

@Entry
@Component
struct SettingsPage {
  @State version: string = '1.0.0-harmony';
  @State busy: boolean = false;
  private readonly questionBank: QuestionBank = new QuestionBank();

  private goGuide(): void {
    router.pushUrl({ url: 'pages/ui/UsageGuidePage' });
  }

  private showPrivacy(): void {
    AlertDialog.show({
      title: '隐私说明',
      message: '本应用所有 OCR 识别均在本机完成；图片、识别文字与题库只保存在设备本地，不会上传。',
      confirm: { value: '知道了', action: () => {} }
    });
  }

  private confirmClear(): void {
    AlertDialog.show({
      title: '确认清空？',
      message: '将删除全部已导入题库。此操作不可恢复。',
      primaryButton: {
        value: '清空',
        fontColor: '#C83B3B',
        action: () => this.clearAll()
      },
      secondaryButton: { value: '取消', action: () => {} }
    });
  }

  private async clearAll(): Promise<void> {
    if (this.busy) {
      return;
    }
    this.busy = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      await this.questionBank.clear(context);
      promptAction.showToast({ message: '已清空本地题库' });
    } catch (err) {
      const error = err as BusinessError;
      promptAction.showToast({ message: `清空失败：${error.message ?? JSON.stringify(err)}` });
    } finally {
      this.busy = false;
    }
  }

  @Builder
  private row(title: string, subtitle: string, action: () => void, color: string = '#222222') {
    Row() {
      Column({ space: 3 }) {
        Text(title)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor(color)
        Text(subtitle)
          .fontSize(12)
          .fontColor('#888888')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      Text('›')
        .fontSize(20)
        .fontColor('#BBBBBB')
    }
    .width('100%')
    .padding(14)
    .borderRadius(12)
    .backgroundColor(Color.White)
    .onClick(action)
  }

  build() {
    Column() {
      Row() {
        Text('设置')
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
          this.row('当前版本', this.version, () => {})
          this.row('使用说明', '各功能操作步骤', () => this.goGuide())
          this.row('隐私说明', '本机处理，不上传', () => this.showPrivacy())
          this.row('清空本地题库', '删除全部已导入题库', () => this.confirmClear(), '#C83B3B')
        }
        .width('92%')
        .padding({ top: 16 })
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