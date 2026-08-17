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
  type: string;

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
