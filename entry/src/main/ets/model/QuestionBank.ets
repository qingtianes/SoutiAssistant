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
 * 存储：Preferences（和安卓 SharedPreferences 同思路）
 */
import preferences from '@ohos.data.preferences';
import common from '@ohos.app.ability.common';

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

/** 题库管理器 */
export class QuestionBank {
  static readonly STORE_NAME: string = 'souti_bank';
  static readonly KEY_BANKS: string = 'banks';
  static readonly KEY_QUESTIONS: string = 'questions';
  static readonly KEY_ACTIVE: string = 'activeBankIds';

  private async getPref(context: common.Context): Promise<preferences.Preferences> {
    return await preferences.getPreferences(context, QuestionBank.STORE_NAME);
  }

  // ===== 题库操作 =====
  async loadBanks(context: common.Context): Promise<Bank[]> {
    const pref = await this.getPref(context);
    const raw = await pref.get(QuestionBank.KEY_BANKS, '[]');
    const list: Bank[] = [];
    try {
      const arr = JSON.parse(raw as string) as Bank[];
      arr.forEach((o: Bank) => {
        list.push(new Bank(o.id, o.name, o.sourceFile, o.createdAt, o.sourceModifiedAt, o.type));
      });
    } catch (e) {
      // 数据损坏返回空列表
    }
    return list;
  }

  private async saveBanks(context: common.Context, list: Bank[]): Promise<void> {
    const pref = await this.getPref(context);
    await pref.put(QuestionBank.KEY_BANKS, JSON.stringify(list));
    await pref.flush();
  }

  async deleteBank(context: common.Context, id: number): Promise<void> {
    const banks = await this.loadBanks(context);
    await this.saveBanks(context, banks.filter((b: Bank) => b.id !== id));
    const allQ = await this.loadAllQuestions(context);
    await this.saveQuestions(context, allQ.filter((q: Question) => q.bankId !== id));
  }

  // ===== 题目操作 =====
  async loadAllQuestions(context: common.Context): Promise<Question[]> {
    const pref = await this.getPref(context);
    const raw = await pref.get(QuestionBank.KEY_QUESTIONS, '[]');
    const list: Question[] = [];
    try {
      const arr = JSON.parse(raw as string) as Question[];
      arr.forEach((o: Question) => {
        list.push(new Question(o.id, o.bankId, o.stem, o.options, o.answer, o.source));
      });
    } catch (e) {
      // 数据损坏返回空列表
    }
    return list;
  }

  async loadQuestions(context: common.Context, bankId: number): Promise<Question[]> {
    const all = await this.loadAllQuestions(context);
    return all.filter((q: Question) => q.bankId === bankId);
  }

  async questionCount(context: common.Context, bankId: number): Promise<number> {
    const list = await this.loadQuestions(context, bankId);
    return list.length;
  }

  async deleteQuestion(context: common.Context, id: number): Promise<void> {
    const all = await this.loadAllQuestions(context);
    await this.saveQuestions(context, all.filter((q: Question) => q.id !== id));
  }

  private async saveQuestions(context: common.Context, list: Question[]): Promise<void> {
    const pref = await this.getPref(context);
    await pref.put(QuestionBank.KEY_QUESTIONS, JSON.stringify(list));
    await pref.flush();
  }

  // ===== 导入（创建题库 + 添加题目）=====
  async importBank(context: common.Context, name: string, sourceFile: string,
    sourceModifiedAt: number, type: string, questions: Question[]): Promise<Bank> {
    const bankId = Date.now();
    const bank = new Bank(bankId, name, sourceFile, bankId, sourceModifiedAt, type);
    // 给题目关联 bankId
    const withBankId: Question[] = questions.map((q: Question) =>
      new Question(q.id, bankId, q.stem, q.options, q.answer, q.source)
    );
    const banks = await this.loadBanks(context);
    await this.saveBanks(context, banks.concat(bank));
    const allQ = await this.loadAllQuestions(context);
    await this.saveQuestions(context, allQ.concat(withBankId));
    return bank;
  }

  async clear(context: common.Context): Promise<void> {
    const pref = await this.getPref(context);
    await pref.clear();
    await pref.flush();
  }

  // ===== 激活题库（被勾选用于搜题的）=====
  async getActiveBankIds(context: common.Context): Promise<string[]> {
    const pref = await this.getPref(context);
    const raw = await pref.get(QuestionBank.KEY_ACTIVE, '[]');
    try {
      return JSON.parse(raw as string) as string[];
    } catch (e) {
      return [];
    }
  }

  async setActiveBankIds(context: common.Context, ids: string[]): Promise<void> {
    const pref = await this.getPref(context);
    await pref.put(QuestionBank.KEY_ACTIVE, JSON.stringify(ids));
    await pref.flush();
  }

  async toggleActive(context: common.Context, bankId: number): Promise<void> {
    const cur = await this.getActiveBankIds(context);
    const key = bankId.toString();
    const idx = cur.indexOf(key);
    if (idx >= 0) {
      cur.splice(idx, 1);
    } else {
      cur.push(key);
    }
    await this.setActiveBankIds(context, cur);
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
