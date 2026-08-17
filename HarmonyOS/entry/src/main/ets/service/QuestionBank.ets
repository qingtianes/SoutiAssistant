import { common } from '@kit.AbilityKit';
import { Bank, Question, SearchResult } from '../model/QuestionBankModels';
import { QuestionMatcher } from './QuestionMatcher';
import { QuestionRepository } from '../repository/QuestionRepository';

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
    const normalizedText = text
      .replace(/\r\n/g, '\n')
      .replace(/\r/g, '\n')
      .replace(/^\uFEFF/, '');
    const lines = normalizedText.split('\n');
    let idCounter = Date.now();

    let stemLines: string[] = [];
    let options: string[] = [];
    let answer = '';
    let seenQuestion = false;
    let waitingForAnswerValue = false;
    let pendingBlankBoundary = false;

    const flush = (): void => {
      const stem = stemLines.join('\n').replace(/\n{2,}/g, '\n').trim();
      if (seenQuestion && stem.length > 0) {
        questions.push(new Question(idCounter++, 0, stem, options.slice(), answer, source));
      }
      stemLines = [];
      options = [];
      answer = '';
      seenQuestion = false;
      waitingForAnswerValue = false;
      pendingBlankBoundary = false;
    };

    for (let index = 0; index < lines.length; index++) {
      const trimmed = lines[index].trim();
      if (trimmed.length === 0) {
        // 空行是“可能的题目边界”。先不立即提交，避免题干与选项之间
        // 的空行把同一道题拆开；遇到下一行普通题干时再确认边界。
        if (seenQuestion) {
          pendingBlankBoundary = true;
        }
        continue;
      }

      if (QuestionBank.isSectionTitle(trimmed)) {
        if (seenQuestion && (options.length > 0 || answer.length > 0)) {
          flush();
        }
        continue;
      }

      const questionStem = QuestionBank.extractQuestionStem(trimmed);
      if (questionStem !== undefined) {
        flush();
        seenQuestion = true;
        stemLines.push(questionStem);
        continue;
      }

      const option = QuestionBank.extractOption(trimmed);
      const answerValue = QuestionBank.extractAnswer(trimmed);

      // 普通文本出现在空行之后，通常是下一道无编号题；选项或答案
      // 出现在空行之后时仍归入当前题。
      if (pendingBlankBoundary && seenQuestion && option === undefined && answerValue === undefined) {
        flush();
      }
      pendingBlankBoundary = false;

      if (option !== undefined) {
        if (seenQuestion) {
          options.push(option);
        }
        continue;
      }

      if (answerValue !== undefined) {
        if (seenQuestion) {
          answer = answerValue;
          waitingForAnswerValue = answerValue.length === 0;
        }
        continue;
      }

      if (!seenQuestion) {
        // 无编号题库只能依靠空行分隔，因此第一段普通文本也必须作为
        // 题干开始，而不能像旧实现一样全部丢弃。
        seenQuestion = true;
      }

      if (waitingForAnswerValue) {
        answer = trimmed;
        waitingForAnswerValue = false;
        continue;
      }

      if (answer.length > 0) {
        // 答案后的解析或备注不属于题干，避免污染后续匹配文本。
        continue;
      }

      if (options.length > 0) {
        // 手工排版或 OCR 可能把一个选项折成多行，续行并入上一选项。
        const lastOptionIndex = options.length - 1;
        options[lastOptionIndex] = `${options[lastOptionIndex]} ${trimmed}`;
      } else {
        stemLines.push(trimmed);
      }
    }
    flush();
    return questions;
  }

  private static extractQuestionStem(text: string): string | undefined {
    const match =
      /^(?:题目\s*)?(?:第\s*\d+\s*题\s*[:：.．、]?|\(\s*\d+\s*\)|（\s*\d+\s*）|\d+\s*[.．、)）:：])\s*(.+)$/.exec(text);
    if (match === null) {
      return undefined;
    }
    const stem = match[1].trim();
    return stem.length >= 2 ? stem : undefined;
  }

  private static extractOption(text: string): string | undefined {
    const match =
      /^(?:\(\s*([A-Ha-h])\s*\)|（\s*([A-Ha-h])\s*）|([A-Ha-h])\s*[.．、)）:：])\s*(.+)$/.exec(text);
    if (match === null) {
      return undefined;
    }
    const label = (match[1] ?? match[2] ?? match[3]).toUpperCase();
    return `${label}. ${match[4].trim()}`;
  }

  private static extractAnswer(text: string): string | undefined {
    const match =
      /^(?:【|\[)?(?:参考答案|正确答案|标准答案|答案|answer)(?:】|\])?\s*(?:为)?\s*[:：]?\s*(.*)$/i.exec(text);
    return match === null ? undefined : match[1].trim();
  }

  private static isSectionTitle(text: string): boolean {
    if (/^\d+\s*\/\s*\d+$/.test(text)) {
      return true;
    }

    if (/^[一二三四五六七八九十百千]+、/.test(text) &&
      !/[？?。！!；;（）()【】\[\]]/.test(text)) {
      return true;
    }

    if (/^(?:第\s*\d+\s*[章节部分]|单元|单选题|多选题|判断题|填空题|选择题|题库|说明|目录).*$/i.test(text)) {
      return true;
    }

    if (/共\s*\d+\s*道/.test(text) && !/[？?。！!]/.test(text)) {
      return true;
    }

    // 对照 Android 版，过滤“1.动火作业”这类短章节名；带问号、
    // 括号或明显题干标点的短句仍按题目保留。
    const numberedStem = QuestionBank.extractQuestionStem(text);
    if (numberedStem !== undefined &&
      numberedStem.length <= 8 &&
      !/[？?。！!；;（）()【】\[\]]/.test(numberedStem) &&
      /(?:作业|练习|章节|单元|部分|目录|第.+章|第.+节)$/.test(numberedStem)) {
      return true;
    }

    return false;
  }
}
