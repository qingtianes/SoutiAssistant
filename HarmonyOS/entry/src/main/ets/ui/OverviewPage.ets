/**
 * 题库总览（对齐 Android BankScreens.OverviewScreen）。
 * 显示题库数量/导入时间，勾选参与搜索，删除，进入详情。
 */
import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';
import { Bank } from '../model/QuestionBankModels';
import { QuestionBank } from '../service/QuestionBank';

@Component
struct BankCountText {
  @Prop bankId: number = 0;
  @State label: string = '共 0 题';
  private readonly questionBank: QuestionBank = new QuestionBank();

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      this.label = `共 ${await this.questionBank.questionCount(context, this.bankId)} 题`;
    } catch (_) {
      this.label = '共 0 题';
    }
  }

  build() {
    Text(this.label)
      .width('100%')
      .fontSize(12)
      .fontColor('#888888')
  }
}

@Entry
@Component
struct OverviewPage {
  @State banks: Bank[] = [];
  @State activeBankIds: string[] = [];
  @State status: string = '加载中…';
  @State pendingDeleteId: number = 0;
  @State busy: boolean = false;
  private readonly questionBank: QuestionBank = new QuestionBank();

  aboutToAppear(): void {
    this.refresh();
  }

  onPageShow(): void {
    this.refresh();
  }

  private async refresh(): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      this.banks = await this.questionBank.loadBanks(context);
      this.activeBankIds = await this.questionBank.getActiveBankIds(context);
      this.status = this.banks.length === 0 ? '还没有导入题库' : `已选 ${this.activeBankIds.length} / ${this.banks.length}`;
    } catch (err) {
      const e = err as BusinessError;
      this.status = `读取失败：${e.message ?? ''}`;
    }
  }

  private async toggle(bankId: number): Promise<void> {
    if (this.busy) {
      return;
    }
    this.busy = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      await this.questionBank.toggleActive(context, bankId);
      await this.refresh();
    } finally {
      this.busy = false;
    }
  }

  private async askDelete(bank: Bank): Promise<void> {
    if (this.pendingDeleteId !== bank.id) {
      this.pendingDeleteId = bank.id;
      this.status = `再次点击删除才会删除“${bank.name}”`;
      return;
    }
    if (this.busy) {
      return;
    }
    this.busy = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      await this.questionBank.deleteBank(context, bank.id);
      this.pendingDeleteId = 0;
      this.status = `已删除“${bank.name}”`;
      await this.refresh();
    } catch (err) {
      const e = err as BusinessError;
      this.status = `删除失败：${e.message ?? ''}`;
    } finally {
      this.busy = false;
    }
  }

  private isActive(bankId: number): boolean {
    return this.activeBankIds.includes(bankId.toString());
  }

  build() {
    Column() {
      Row() {
        Text('题库总览')
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
        ForEach(this.banks, (bank: Bank) => {
          ListItem() {
            Column({ space: 8 }) {
              Row() {
                Column({ space: 3 }) {
                  Text(bank.name)
                    .width('100%')
                    .fontSize(15)
                    .fontWeight(FontWeight.Bold)
                    .fontColor('#222222')
                  BankCountText({ bankId: bank.id })
                  Text('导入时间：' + new Date(bank.createdAt).toLocaleDateString())
                    .fontSize(11)
                    .fontColor('#AAAAAA')
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Start)

                Text(this.isActive(bank.id) ? '已启用' : '未启用')
                  .fontSize(12)
                  .fontColor(this.isActive(bank.id) ? '#1D9E75' : '#999999')
              }

              Row({ space: 8 }) {
                Button(this.isActive(bank.id) ? '停用' : '启用')
                  .layoutWeight(1)
                  .height(36)
                  .fontSize(13)
                  .backgroundColor(this.isActive(bank.id) ? '#E24B4A' : '#1D9E75')
                  .onClick(() => this.toggle(bank.id))
                Button('查看题目')
                  .layoutWeight(1)
                  .height(36)
                  .fontSize(13)
                  .backgroundColor('#2F6FED')
                  .onClick(() => router.pushUrl({ url: 'ui/BankDetailPage?bankId=' + bank.id }))
                Button(this.pendingDeleteId === bank.id ? '确认删除' : '删除')
                  .layoutWeight(1)
                  .height(36)
                  .fontSize(13)
                  .backgroundColor(this.pendingDeleteId === bank.id ? '#C83B3B' : '#999999')
                  .onClick(() => this.askDelete(bank))
              }
            }
            .width('100%')
            .padding(14)
            .borderRadius(14)
            .backgroundColor(Color.White)
          }
        }, (bank: Bank) => bank.id.toString())
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
