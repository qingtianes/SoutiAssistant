/**
 * 题目匹配打分：只负责比较 OCR 查询文本和题库题干的相关度。
 */
export class QuestionMatcher {
  static score(query: string, stem: string): number {
    if (query.length === 0 || stem.length === 0) {
      return 0;
    }
    let score = 0;
    if (query.length >= 4 && (stem.includes(query) || query.includes(stem))) {
      score += 100;
    }
    const lcs = QuestionMatcher.lcsLen(query, stem);
    if (lcs >= 3) {
      score += lcs;
    }
    return score;
  }

  private static lcsLen(a: string, b: string): number {
    if (a.length === 0 || b.length === 0) {
      return 0;
    }
    const m = a.length;
    const n = b.length;
    let maxLen = 0;
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
      for (let j = 0; j <= n; j++) {
        prev[j] = curr[j];
      }
    }
    return maxLen;
  }
}
