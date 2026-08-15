/**
 * 搜题助手鸿蒙版 - 题库管理器（与安卓 QuestionBank.kt 逻辑一致）
 *
 * 功能：
 * - 题库（Bank）管理：导入/删除/列表
 * - 题目（Question）管理：增删/加载
 * - 激活题库：勾选用于搜题的题库集合
 * - 搜索匹配：LCS 最长公共子串 + 完全包含打分（OCR 截短也能命中）
 * - txt 解析：题干 + A./B./C./D. 选项 + 答案:X + 空行分隔
 *
 * 存储：应用私有目录中的 JSON 文件；Preferences 仅用于首次迁移旧数据。
 */
import preferences from '@ohos.data.preferences';
import common from '@ohos.app.ability.common';
import { fileIo } from '@kit.CoreFileKit';

/** 搜索结果（含题库名 + 匹配分数） */
export class SearchResult {
  question: Question;
  bankName: string;
  score: number;

  constructor(question: Question, bankName: string, score: number) {
    this.question = question;
    this.bankName = bankName;
    this.score = score;
  }
}

/** 题库（一个导入文件 = 一个题库） */
export class Bank {
  id: number;
  name: string;
  sourceFile: string;
  createdAt: number;
  sourceModifiedAt: number;
  type: string; // "manual" 或 "ai"

  constructor(id: number, name: string, sourceFile: string, createdAt: number,
    sourceModifiedAt: number, type: string) {
    this.id = id;
    this.name = name;
    this.sourceFile = sourceFile;
    this.createdAt = createdAt;
    this.sourceModifiedAt = sourceModifiedAt;
    this.type = type;
  }
}

/** 题目 */
export class Question {
  id: number;
  bankId: number;
  stem: string;
  options: string[];
  answer: string;
  source: string;

  constructor(id: number, bankId: number, stem: string, options: string[],
    answer: string, source: string) {
    this.id = id;
    this.bankId = bankId;
    this.stem = stem;
    this.options = options;
    this.answer = answer;
    this.source = source;
  }
}

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

/** 题库管理器 */
export class QuestionBank {
  static readonly STORE_NAME: string = 'souti_bank';
  static readonly KEY_BANKS: string = 'banks';
  static readonly KEY_QUESTIONS: string = 'questions';
  static readonly KEY_ACTIVE: string = 'activeBankIds';
  private static readonly STORE_FILE_NAME: string = 'souti_bank_store.json';

  private async getPref(context: common.Context): Promise<preferences.Preferences> {
    return await preferences.getPreferences(context, QuestionBank.STORE_NAME);
  }

  private storePath(context: common.Context): string {
    return `${context.filesDir}/${QuestionBank.STORE_FILE_NAME}`;
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
      const rawBanks = await pref.get(QuestionBank.KEY_BANKS, '[]');
      banks = JSON.parse(rawBanks as string) as Bank[];
    } catch (error) {
      banks = [];
    }
    try {
      const rawQuestions = await pref.get(QuestionBank.KEY_QUESTIONS, '[]');
      questions = JSON.parse(rawQuestions as string) as Question[];
    } catch (error) {
      questions = [];
    }
    try {
      const rawActive = await pref.get(QuestionBank.KEY_ACTIVE, '[]');
      activeBankIds = JSON.parse(rawActive as string) as string[];
    } catch (error) {
      activeBankIds = [];
    }
    const store = this.normalizeStore(new QuestionBankStore(banks, questions, activeBankIds));
    await this.writeStore(context, store);
    // 成功写入文件后清理旧的大字符串，避免继续占用 Preferences。
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
    // mode=0 表示覆盖目标文件；临时文件写完后再替换，降低中途退出造成损坏的概率。
    await fileIo.moveFile(temporaryPath, targetPath, 0);
  }

  // ===== 题库操作 =====
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

  // ===== 题目操作 =====
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

  // ===== 导入（创建题库 + 添加题目）=====
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

  // ===== 激活题库（被勾选用于搜题的）=====
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

  /**
   * 搜索：在激活题库的所有题目里找匹配的题干
   * @return 按相关度排序的搜索结果
   */
  async search(context: common.Context, query: string, limit: number = 5): Promise<SearchResult[]> {
    if (query.trim().length === 0) {
      return [];
    }
    const q = query.trim();
    const activeIds = await this.getActiveBankIds(context);
    if (activeIds.length === 0) {
      return [];
    }
    const banks = await this.loadBanks(context);
    const bankMap: Map<number, string> = new Map();
    banks.forEach((b: Bank) => {
      bankMap.set(b.id, b.name);
    });
    const allQ = await this.loadAllQuestions(context);
    const results: SearchResult[] = [];
    allQ.forEach((question: Question) => {
      if (activeIds.includes(question.bankId.toString())) {
        const score = this.scoreMatch(q, question.stem);
        if (score > 0) {
          const bankName = bankMap.get(question.bankId) !== undefined ? (bankMap.get(question.bankId) as string) : '未知题库';
          results.push(new SearchResult(question, bankName, score));
        }
      }
    });
    results.sort((a: SearchResult, b: SearchResult) => b.score - a.score);
    return results.slice(0, limit);
  }

  /**
   * 最长公共子串长度（Longest Common Substring）
   * OCR 截屏可能比题库题干短（被截断），用 LCS 找最长匹配段
   */
  private lcsLen(a: string, b: string): number {
    if (a.length === 0 || b.length === 0) {
      return 0;
    }
    const m = a.length;
    const n = b.length;
    let maxLen = 0;
    // O(m*n) DP 找最长公共子串（只保留上一行，节省内存）
    const prev: number[] = new Array<number>(n + 1).fill(0);
    const curr: number[] = new Array<number>(n + 1).fill(0);
    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        if (a.charCodeAt(i - 1) === b.charCodeAt(j - 1)) {
          curr[j] = (j > 1 ? prev[j - 1] : 0) + 1;
        } else {
          curr[j] = 0;
        }
        if (curr[j] > maxLen) {
          maxLen = curr[j];
        }
      }
      // 滚动：curr → prev
      for (let j = 0; j <= n; j++) {
        prev[j] = curr[j];
      }
    }
    return maxLen;
  }

  /**
   * 匹配打分：基于最长公共子串（OCR 截屏可能比题库短，contains 失效）
   * - 完全包含 +100 分
   * - LCS 长度加分（每字符 +1）
   * - LCS 太短（< 3）视为不匹配
   */
  private scoreMatch(query: string, stem: string): number {
    if (query.length === 0 || stem.length === 0) {
      return 0;
    }
    let score = 0;
    // 完全包含 → 满分
    if (query.length >= 4 && (stem.includes(query) || query.includes(stem))) {
      score += 100;
    }
    // LCS 加分
    const lcs = this.lcsLen(query, stem);
    if (lcs >= 3) {
      score += lcs;
    }
    return score;
  }

  /**
   * 从 .txt 解析题目（不关联 bankId，importBank 时会赋值）
   * 格式：题干 + A./B./C./D. 选项 + 答案:X + 空行分隔
   */
  static parseTxt(text: string, source: string): Question[] {
    const questions: Question[] = [];
    const blocks = text.split(/\n\s*\n/);
    let idCounter = Date.now();

    for (let bi = 0; bi < blocks.length; bi++) {
      const lines = blocks[bi].trim().split('\n').filter((l: string) => l.trim().length > 0);
      if (lines.length === 0) {
        continue;
      }
      let stem = '';
      const options: string[] = [];
      let answer = '';
      let inStem = true;

      for (let li = 0; li < lines.length; li++) {
        const trimmed = lines[li].trim();
        const optionMatch = /^[A-Z][.、)）]\s*(.+)/.exec(trimmed);
        const answerMatch = /^答案[:：]?\s*(.+)/.exec(trimmed);

        if (answerMatch !== null) {
          inStem = false;
          answer = answerMatch[1].trim();
        } else if (optionMatch !== null) {
          inStem = false;
          options.push(trimmed);
        } else {
          // 题干（含跨行）或非标准行，都归入题干
          stem += (stem.length > 0 ? '\n' : '') + trimmed;
        }
      }

      if (stem.trim().length > 0) {
        questions.push(new Question(idCounter++, 0, stem.trim(), options, answer, source));
      }
    }
    return questions;
  }
}
