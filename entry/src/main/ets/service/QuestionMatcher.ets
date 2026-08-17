/**
 * 题目匹配打分：只负责比较 OCR 查询文本和题库题干的相关度。
 */
export class QuestionMatcher {
  static score(query: string, stem: string): number {
    const normalizedQuery = QuestionMatcher.normalize(query);
    const normalizedStem = QuestionMatcher.normalize(stem);
    if (normalizedQuery.length === 0 || normalizedStem.length === 0) {
      return 0;
    }

    let score = 0;
    if (normalizedQuery.length >= 4 &&
      (normalizedStem.includes(normalizedQuery) || normalizedQuery.includes(normalizedStem))) {
      score += 100;
    }

    const lcs = QuestionMatcher.lcsLen(normalizedQuery, normalizedStem);
    if (lcs >= 3) {
      score += lcs;
    }
    return score;
  }

  /**
   * 统一题库文本和 OCR 文本的常见排版差异。
   * 这里不删除中文正文，只去除会干扰题干匹配的版式噪声。
   */
  static normalize(text: string): string {
    let normalized = text
      .replace(/\uFEFF/g, '')
      .toLowerCase()
      .replace(/[\uFF01-\uFF5E]/g, (char: string) =>
        String.fromCharCode(char.charCodeAt(0) - 0xFEE0));

    // 先去掉题号再移除括号；否则“（1）题干”会变成“1题干”，
    // 数字会继续参与匹配并降低 OCR 结果的稳定性。
    normalized = normalized.replace(
      /^(?:题目\s*)?(?:第\s*\d+\s*题\s*[:：.、]?|\(\s*\d+\s*\)|\d+\s*[.、)）:：])/,
      ''
    );
    normalized = normalized
      .replace(/[\s\u3000]/g, '')
      .replace(/[(){}\[\]【】｛｝]/g, '')
      .replace(/[，。！？；：、“”‘’「」『』《》〈〉,.!?;:"'`]/g, '');

    return normalized;
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
