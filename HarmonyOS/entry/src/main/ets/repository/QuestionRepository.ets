import preferences from '@ohos.data.preferences';
import { common } from '@kit.AbilityKit';
import { fileIo } from '@kit.CoreFileKit';
import { Bank, Question } from '../model/QuestionBankModels';

/** 持久化快照：题库、题目和启用状态在一次写入中保持一致。 */
class QuestionBankStore {
  banks: Bank[];
  questions: Question[];
  activeBankIds: string[];

  constructor(banks: Bank[] = [], questions: Question[] = [], activeBankIds: string[] = []) {
    this.banks = banks;
    this.questions = questions;
    this.activeBankIds = activeBankIds;
  }
}

/** 题库存取：只负责把题库、题目、启用状态读写到应用私有 JSON 文件。 */
export class QuestionRepository {
  static readonly STORE_NAME: string = 'souti_bank';
  static readonly KEY_BANKS: string = 'banks';
  static readonly KEY_QUESTIONS: string = 'questions';
  static readonly KEY_ACTIVE: string = 'activeBankIds';
  private static readonly STORE_FILE_NAME: string = 'souti_bank_store.json';

  private async getPref(context: common.Context): Promise<preferences.Preferences> {
    return await preferences.getPreferences(context, QuestionRepository.STORE_NAME);
  }

  private storePath(context: common.Context): string {
    return `${context.filesDir}/${QuestionRepository.STORE_FILE_NAME}`;
  }

  private normalizeStore(value: QuestionBankStore): QuestionBankStore {
    const banks: Bank[] = [];
    const questions: Question[] = [];
    const activeBankIds: string[] = [];
    if (value.banks !== undefined) {
      value.banks.forEach((bank: Bank) => {
        banks.push(new Bank(bank.id, bank.name, bank.sourceFile, bank.createdAt,
          bank.sourceModifiedAt, bank.type));
      });
    }
    if (value.questions !== undefined) {
      value.questions.forEach((question: Question) => {
        questions.push(new Question(question.id, question.bankId, question.stem,
          question.options, question.answer, question.source));
      });
    }
    if (value.activeBankIds !== undefined) {
      value.activeBankIds.forEach((id: string) => {
        if (!activeBankIds.includes(id)) {
          activeBankIds.push(id);
        }
      });
    }
    const validIds: string[] = banks.map((bank: Bank) => bank.id.toString());
    return new QuestionBankStore(banks, questions,
      activeBankIds.filter((id: string) => validIds.includes(id)));
  }

  private async readStore(context: common.Context): Promise<QuestionBankStore> {
    const path = this.storePath(context);
    const exists: boolean = await fileIo.access(path);
    if (!exists) {
      return await this.migrateLegacyStore(context);
    }
    const raw: string = await fileIo.readText(path, { encoding: 'utf-8' });
    if (raw.trim().length === 0) {
      throw new Error('本地题库数据为空，请先备份后清除并重新导入。');
    }
    try {
      return this.normalizeStore(JSON.parse(raw) as QuestionBankStore);
    } catch (error) {
      throw new Error('本地题库数据已损坏，请先备份后清除并重新导入。');
    }
  }

  private async migrateLegacyStore(context: common.Context): Promise<QuestionBankStore> {
    const pref = await this.getPref(context);
    let banks: Bank[] = [];
    let questions: Question[] = [];
    let activeBankIds: string[] = [];
    try {
      const rawBanks = await pref.get(QuestionRepository.KEY_BANKS, '[]');
      banks = JSON.parse(rawBanks as string) as Bank[];
    } catch (error) {
      banks = [];
    }
    try {
      const rawQuestions = await pref.get(QuestionRepository.KEY_QUESTIONS, '[]');
      questions = JSON.parse(rawQuestions as string) as Question[];
    } catch (error) {
      questions = [];
    }
    try {
      const rawActive = await pref.get(QuestionRepository.KEY_ACTIVE, '[]');
      activeBankIds = JSON.parse(rawActive as string) as string[];
    } catch (error) {
      activeBankIds = [];
    }
    const store = this.normalizeStore(new QuestionBankStore(banks, questions, activeBankIds));
    await this.writeStore(context, store);
    await pref.clear();
    await pref.flush();
    return store;
  }

  private async writeStore(context: common.Context, store: QuestionBankStore): Promise<void> {
    const normalized = this.normalizeStore(store);
    const targetPath = this.storePath(context);
    const temporaryPath = `${targetPath}.tmp`;
    const stream: fileIo.Stream = await fileIo.createStream(temporaryPath, 'w');
    try {
      await stream.write(JSON.stringify(normalized));
    } finally {
      stream.close();
    }
    await fileIo.moveFile(temporaryPath, targetPath, 0);
  }

  async loadBanks(context: common.Context): Promise<Bank[]> {
    return (await this.readStore(context)).banks;
  }

  async deleteBank(context: common.Context, id: number): Promise<void> {
    const store = await this.readStore(context);
    const key = id.toString();
    await this.writeStore(context, new QuestionBankStore(
      store.banks.filter((bank: Bank) => bank.id !== id),
      store.questions.filter((question: Question) => question.bankId !== id),
      store.activeBankIds.filter((activeId: string) => activeId !== key)
    ));
  }

  async loadAllQuestions(context: common.Context): Promise<Question[]> {
    return (await this.readStore(context)).questions;
  }

  async loadQuestions(context: common.Context, bankId: number): Promise<Question[]> {
    return (await this.readStore(context)).questions.filter((question: Question) => question.bankId === bankId);
  }

  async questionCount(context: common.Context, bankId: number): Promise<number> {
    return (await this.loadQuestions(context, bankId)).length;
  }

  async deleteQuestion(context: common.Context, id: number): Promise<void> {
    const store = await this.readStore(context);
    store.questions = store.questions.filter((question: Question) => question.id !== id);
    await this.writeStore(context, store);
  }

  async importBank(context: common.Context, name: string, sourceFile: string,
    sourceModifiedAt: number, type: string, questions: Question[]): Promise<Bank> {
    const store = await this.readStore(context);
    let bankId = Date.now();
    while (store.banks.some((bank: Bank) => bank.id === bankId)) {
      bankId++;
    }
    const bank = new Bank(bankId, name, sourceFile, bankId, sourceModifiedAt, type);
    let nextQuestionId = 1;
    store.questions.forEach((question: Question) => {
      if (question.id >= nextQuestionId) {
        nextQuestionId = question.id + 1;
      }
    });
    const withBankId: Question[] = questions.map((question: Question) =>
      new Question(nextQuestionId++, bankId, question.stem, question.options,
        question.answer, question.source)
    );
    store.banks.push(bank);
    store.questions = store.questions.concat(withBankId);
    if (!store.activeBankIds.includes(bankId.toString())) {
      store.activeBankIds.push(bankId.toString());
    }
    await this.writeStore(context, store);
    return bank;
  }

  async clear(context: common.Context): Promise<void> {
    await this.writeStore(context, new QuestionBankStore());
    const pref = await this.getPref(context);
    await pref.clear();
    await pref.flush();
  }

  async getActiveBankIds(context: common.Context): Promise<string[]> {
    return (await this.readStore(context)).activeBankIds;
  }

  async setActiveBankIds(context: common.Context, ids: string[]): Promise<void> {
    const store = await this.readStore(context);
    store.activeBankIds = ids;
    await this.writeStore(context, store);
  }

  async toggleActive(context: common.Context, bankId: number): Promise<void> {
    const store = await this.readStore(context);
    const key = bankId.toString();
    const idx = store.activeBankIds.indexOf(key);
    if (idx >= 0) {
      store.activeBankIds.splice(idx, 1);
    } else if (store.banks.some((bank: Bank) => bank.id === bankId)) {
      store.activeBankIds.push(key);
    }
    await this.writeStore(context, store);
  }
}
