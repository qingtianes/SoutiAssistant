import { describe, beforeAll, beforeEach, afterEach, afterAll, it, expect } from '@ohos/hypium';
import { QuestionBank } from '../main/ets/service/QuestionBank';
import { QuestionMatcher } from '../main/ets/service/QuestionMatcher';

export default function localUnitTest() {
  describe('localUnitTest', () => {
    // Defines a test suite. Two parameters are supported: test suite name and test suite function.
    beforeAll(() => {
      // Presets an action, which is performed only once before all test cases of the test suite start.
      // This API supports only one parameter: preset action function.
    });
    beforeEach(() => {
      // Presets an action, which is performed before each unit test case starts.
      // The number of execution times is the same as the number of test cases defined by **it**.
      // This API supports only one parameter: preset action function.
    });
    afterEach(() => {
      // Presets a clear action, which is performed after each unit test case ends.
      // The number of execution times is the same as the number of test cases defined by **it**.
      // This API supports only one parameter: clear action function.
    });
    afterAll(() => {
      // Presets a clear action, which is performed after all test cases of the test suite end.
      // This API supports only one parameter: clear action function.
    });
    it('assertContain', 0, () => {
      // Defines a test case. This API supports three parameters: test case name, filter parameter, and test case function.
      let a = 'abc';
      let b = 'b';
      // Defines a variety of assertion methods, which are used to declare expected boolean conditions.
      expect(a).assertContain(b);
      expect(a).assertEqual(a);
    });

    it('parses numbered questions without blank lines', 0, () => {
      const text = '\uFEFF1. 第一题的题干？\r\nA. 选项一\r\nb) 选项二\r\n正确答案：A\r\n' +
        '第2题：第二题的题干\r\n（A）甲\r\nB、乙\r\n【标准答案】B';
      const questions = QuestionBank.parseTxt(text, 'sample.txt');
      expect(questions.length).assertEqual(2);
      expect(questions[0].stem).assertEqual('第一题的题干？');
      expect(questions[0].options.length).assertEqual(2);
      expect(questions[0].answer).assertEqual('A');
      expect(questions[1].stem).assertEqual('第二题的题干');
      expect(questions[1].answer).assertEqual('B');
    });

    it('parses blank-separated questions and option continuations', 0, () => {
      const text = '第一道没有编号的题目\nA. 选项一\n  续行内容\nB）选项二\n答案：A\n\n' +
        '第二道没有编号的题目\n\nA：选项甲\nB、选项乙\n参考答案 B';
      const questions = QuestionBank.parseTxt(text, 'blank.txt');
      expect(questions.length).assertEqual(2);
      expect(questions[0].options[0]).assertEqual('A. 选项一 续行内容');
      expect(questions[0].answer).assertEqual('A');
      expect(questions[1].options.length).assertEqual(2);
      expect(questions[1].answer).assertEqual('B');
    });

    it('parses plain questions separated only by blank lines', 0, () => {
      const text = '第一道简答题\n\n第二道简答题\n\n第三道简答题';
      const questions = QuestionBank.parseTxt(text, 'plain.txt');
      expect(questions.length).assertEqual(3);
      expect(questions[1].stem).assertEqual('第二道简答题');
    });

    it('matches BOM, numbering, full width punctuation and OCR spaces', 0, () => {
      const score = QuestionMatcher.score(
        '\uFEFF（1） 下列 说法正确的是（ ）？',
        '1.下列说法正确的是（）？'
      );
      expect(score > 100).assertTrue();
    });

    it('ignores section titles and page footers', 0, () => {
      const text = '一、单选题，共2道\n1.动火作业\n1. 题目一\nA. 是\n答案：A\n1/10\n' +
        '二、判断题\n2. 题目二\n答案：B';
      const questions = QuestionBank.parseTxt(text, 'sample.txt');
      expect(questions.length).assertEqual(2);
      expect(questions[0].stem).assertEqual('题目一');
    });
  });
}
