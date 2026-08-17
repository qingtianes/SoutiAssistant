/**
 * 题库详情（对齐 Android BankScreens.BankDetailScreen）。
 * 逐题显示题干/选项/答案，支持删除单题。
 */
import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';
import promptAction from '@ohos.promptAction';
import { Question } from '../model/QuestionBankModels';
import { QuestionBank } from '../service/QuestionBank';

@Entry
@Component
struct BankDetailPage {
  @State bankId: number = 0;
  @State questions: Question[] = [];
  @State status: string = '加载中…';
  @State busy: boolean = false;
  private readonly questionBank: QuestionBank = new QuestionBank();

  aboutToAppear(): void {
    const params = router.getParams() as Record<string, Object>;
    const raw = params !== null && params !== undefined ? params['bankId'] : undefined;
    if (typeof raw === 'string') {
      this.bankId = Number(raw);
    }
    this.load();
  }

  private async load(): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      this.questions = await this.questionBank.loadQuestions(context, this.bankId);
      this.status = `共 ${this.questions.length} 题`;
    } catch (err) {
      const e = err as BusinessError;
      this.status = `读取失败：${e.message ?? ''}`;
    }
  }

  private async remove(question: Question): Promise<void> {
    if (this.busy) {
      return;
    }
    this.busy = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      await this.questionBank.deleteQuestion(context, question.id);
      await this.load();
    } catch (err) {
      const e = err as BusinessError;
      this.status = `删除失败：${e.message ?? ''}`;
    } finally {
      this.busy = false;
    }
  }

  build() {
    Column() {
      Row() {
        Text('题库详情')
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

      Text(this.status)
        .width('92%')
        .margin({ top: 10 })
        .fontSize(13)
        .fontColor('#666666')

      List({ space: 10 }) {
        ForEach(this.questions, (question: Question, index: number) => {
          ListItem() {
            Column({ space: 6 }) {
              Row() {
                Text(`${index + 1}. ${question.stem}`)
                  .layoutWeight(1)
                  .fontSize(15)
                  .fontWeight(FontWeight.Medium)
                  .fontColor('#222222')
                Blank()
                Button('删除')
                  .width(64)
                  .height(30)
                  .fontSize(12)
                  .backgroundColor('#C83B3B')
                  .onClick(() => this.remove(question))
              }
              .width('100%')
              if (question.options.length > 0) {
                ForEach(question.options, (opt: string) => {
                  Text(opt)
                    .width('100%')
                    .fontSize(13)
                    .fontColor('#555555')
                }, (opt: string) => opt)
              }
              if (question.answer.length > 0) {
                Text(`答案：${question.answer}`)
                  .width('100%')
                  .fontSize(13)
                  .fontWeight(FontWeight.Bold)
                  .fontColor('#1D9E75')
              }
            }
            .width('100%')
            .padding(14)
            .borderRadius(14)
            .backgroundColor(Color.White)
          }
        }, (question: Question, index: number) => `${this.bankId}:${question.id}:${index}`)
      }
      .width('100%')
      .layoutWeight(1)
      .padding({ left: 16, right: 16, top: 12, bottom: 20 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F7F7F7')
  }
}
