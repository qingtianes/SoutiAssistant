/**
 * 智能导入页（对齐 Android ImportScreen）。
 * 当前阶段：TXT 可用；DOCX/PDF/XLS 在 H2 统一解析器中实现。
 */
import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';
import promptAction from '@ohos.promptAction';
import { Question } from '../model/QuestionBankModels';
import { QuestionBank } from '../service/QuestionBank';
import { SelectedTextBank, TextBankImportService } from '../importer/TextBankImportService';

@Entry
@Component
struct ImportPage {
  @State status: string = '选择文件后开始导入';
  @State busy: boolean = false;
  private readonly questionBank: QuestionBank = new QuestionBank();

  private async startImport(): Promise<void> {
    if (this.busy) {
      return;
    }
    this.busy = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const selected: SelectedTextBank | undefined = await TextBankImportService.pickOneTxt(context);
      if (selected === undefined) {
        this.status = '已取消选择';
        return;
      }
      const questions: Question[] = QuestionBank.parseTxt(selected.content, selected.name);
      if (questions.length === 0) {
        throw new Error('没有解析到题目，请检查 TXT 是否包含题干并用空行分隔');
      }
      await this.questionBank.importBank(context, selected.name, selected.uri,
        selected.modifiedAt, 'manual', questions);
      this.status = `已导入“${selected.name}”，共 ${questions.length} 道题，已自动启用`;
      promptAction.showToast({ message: this.status });
    } catch (err) {
      const e = err as BusinessError;
      this.status = `导入失败：${e.message ?? JSON.stringify(err)}`;
    } finally {
      this.busy = false;
    }
  }

  build() {
    Column({ space: 12 }) {
      Row() {
        Text('智能导入')
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
        Column({ space: 12 }) {
          Text('支持格式')
            .width('100%')
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor('#444444')
          Column({ space: 6 }) {
            Row() {
              Text('TXT').fontSize(14).fontWeight(FontWeight.Bold).fontColor('#222222')
              Blank()
              Text('可用').fontSize(12).fontColor('#1D9E75')
            }
            Row() {
              Text('DOCX / PDF / XLS').fontSize(14).fontWeight(FontWeight.Bold).fontColor('#222222')
              Blank()
              Text('开发中').fontSize(12).fontColor('#BBBBBB')
            }
            Row() {
              Text('XLSX').fontSize(14).fontWeight(FontWeight.Bold).fontColor('#222222')
              Blank()
              Text('暂不支持').fontSize(12).fontColor('#BBBBBB')
            }
          }
          .width('100%')
          .padding(14)
          .borderRadius(14)
          .backgroundColor(Color.White)

          Button(this.busy ? '导入中…' : '选择 TXT 文件')
            .width('100%')
            .height(44)
            .enabled(!this.busy)
            .backgroundColor('#1D9E75')
            .onClick(() => this.startImport())

          Text(this.status)
            .width('100%')
            .fontSize(13)
            .fontColor('#666666')
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
