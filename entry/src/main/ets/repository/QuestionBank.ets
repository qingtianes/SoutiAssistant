import common from '@ohos.app.ability.common';
import { Bank, Question, SearchResult } from '../model/QuestionBankModels';
import { QuestionMatcher } from './QuestionMatcher';
import { QuestionRepository } from './QuestionRepository';

/** 题库管理器：对上层提供题库读取、导入、启用和搜索；底层存取委托给 QuestionRepository。 */
export class QuestionBank {
  private repository: QuestionRepository = new QuestionRepository();

  loadBanks(context: common.Context): Promise<Bank[]> {
    return this.repository.loadBanks(context);
  }

  deleteBank(context: common.Context, id: number): Promise<void> {
    return this.repository.deleteBank(context, id);
  }

  loadAllQuestions(context: common.Context): Promise<Question[]> {
    return this.repository.loadAllQuestions(context);
  }

  loadQuestions(context: common.Context, bankId: number): Promise<Question[]> {
    return this.repository.loadQuestions(context, bankId);
  }

  questionCount(context: common.Context, bankId: number): Promise<number> {
    return this.repository.questionCount(context, bankId);
  }

  deleteQuestion(context: common.Context, id: number): Promise<void> {
    return this.repository.deleteQuestion(context, id);
  }

  importBank(context: common.Context, name: string, sourceFile: string,
    sourceModifiedAt: number, type: string, questions: Question[]): Promise<Bank> {
    return this.repository.importBank(context, name, sourceFile, sourceModifiedAt, type, questions);
  }

  clear(context: common.Context): Promise<void> {
    return this.repository.clear(context);
  }

  getActiveBankIds(context: common.Context): Promise<string[]> {
    return this.repository.getActiveBankIds(context);
  }

  setActiveBankIds(context: common.Context, ids: string[]): Promise<void> {
    return this.repository.setActiveBankIds(context, ids);
  }

  toggleActive(context: common.Context, bankId: number): Promise<void> {
    return this.repository.toggleActive(context, bankId);
  }

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
        const score = QuestionMatcher.score(q, question.stem);
        if (score > 0) {
          const bankName = bankMap.get(question.bankId) !== undefined ? (bankMap.get(question.bankId) as string) : '未知题库';
          results.push(new SearchResult(question, bankName, score));
        }
      }
    });
    results.sort((a: SearchResult, b: SearchResult) => b.score - a.score);
    return results.slice(0, limit);
  }

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
