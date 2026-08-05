# -*- coding: utf-8 -*-
"""
搜题助手 · 题库转换工具 v2（切块模式）
把各种格式题库转换成 APP 可导入的标准 txt。

支持：
    .txt   纯文本（有序号 / 无序号有选项 / 无序号空行分隔）
    .docx  Word（题干+选项+答案连续排列，整块保留）
    .pdf   文字版 PDF（提取文本后切块；扫描版判定无法导入）
    .xls   旧版 Excel（列：题型/题目内容/可选项(分号)/答案 → 三列模式）

切块规则（按优先级）：
    1. 数字.开头 → 新题开始
    2. 空行 → 题目边界（无序号时）
    3. 选项行（A./B./C./D. 开头）→ 归属当前题
    4. 最坏情况（无序号无空行纯文本流）→ 甄别并提示

安全：只读源文件，输出到独立目录，绝不修改源文件。
"""

import sys
import os
import re
import glob

# ============================================================
# 切块核心逻辑
# ============================================================

def is_section_title(t):
    """章节标题 / 页眉 / 说明行"""
    if not t:
        return True
    if re.match(r'^[一二三四五六七八九十]+、', t):  # 一、单选题
        return True
    if '作业' in t and len(t) < 10 and not re.search(r'[（(]', t) and not re.match(r'^\d+[.、]', t):
        return True  # 章节名
    if re.match(r'^\d+[.、]\S{1,6}$', t) and not re.search(r'[（(]', t):
        return True  # 1.动火作业 短标题
    if re.match(r'^\d+/\d+$', t):  # 页脚 1/105
        return True
    if re.match(r'^[一二三四五六七八九十]+、.+共\d+道', t):  # 一、判断题，共206道。
        return True
    return False


def is_question_start(t):
    """数字开头 + 有括号或足够长 → 新题"""
    m = re.match(r'^(\d+)[.、]\s*(.+)$', t)
    if not m:
        return False
    stem = m.group(2).strip()
    if len(stem) < 4 and not re.search(r'[（(]', stem):
        return False
    return True


def is_option_line(t):
    """选项行：A./B./C./D. 开头"""
    return bool(re.match(r'^[A-Da-d]\s*[.、．)）]', t))


def chunk_lines(txts):
    """对行列表切块（多层兜底）→ [(题号|None, [行])] + 诊断"""
    chunks = []
    current = None
    current_qid = None
    has_numbered = False      # 是否有序号题
    no_number_with_option = False  # 无序号但识别到选项
    blank_separated = False   # 是否用空行分隔

    # 预扫描：是否全部无序号
    q_lines = [t for t in txts if re.match(r'^\d+[.、]', t) and not is_section_title(t)]
    has_numbered = len(q_lines) > 0

    # ★ 跳过文档头部说明（如"搜题助手切块测试题库"），直到第一个有题目特征的行
    start = 0
    for i, t in enumerate(txts):
        if not t.strip():
            continue
        if is_section_title(t):
            continue
        # 题目特征：数字开头 / 含括号 / 选项行
        if is_question_start(t) or re.search(r'[（(]', t) or is_option_line(t):
            start = i
            break
    # 若整个开头都是无特征行（如无序号判断题），保留全部
    if start == 0:
        pass

    prev_blank = False
    for idx, t in enumerate(txts):
        if idx < start and not is_question_start(t) and not is_option_line(t):
            continue  # 跳过头部说明
        if not t.strip():
            # 空行 → 题目边界（如果当前有题，且不是刚开的）
            prev_blank = True
            if current is not None:
                chunks.append((current_qid, current))
                current = None
                current_qid = None
                blank_separated = True
            continue
        if is_section_title(t):
            continue

        if is_question_start(t):
            if current is not None:
                chunks.append((current_qid, current))
            m = re.match(r'^(\d+)[.、]\s*(.+)$', t)
            current_qid = int(m.group(1))
            current = [t]
        elif is_option_line(t) and current is not None:
            # 选项行归属当前题
            current.append(t)
            no_number_with_option = True
        else:
            # 普通行：若当前无题 → 若前面有空行，作为新题开头（无序号场景）
            if current is None:
                # ★ 过滤文档标题/说明（短且无括号，如"搜题助手切块测试题库"）
                if len(t) < 8 and not re.search(r'[（(]', t) and not is_option_line(t):
                    continue
                current_qid = None
                current = [t]
            else:
                current.append(t)
        prev_blank = False

    if current is not None:
        chunks.append((current_qid, current))

    # 诊断
    diag = {
        'has_numbered': has_numbered,
        'no_number_with_option': no_number_with_option,
        'blank_separated': blank_separated,
    }
    return chunks, diag


def fmt_chunk(qid, lines):
    """切块输出：整块原样（第一行作为题干，其余保留）"""
    return "\n".join(lines) + "\n\n"


# ============================================================
# 各格式解析入口
# ============================================================

def parse_txt(path):
    with open(path, encoding='utf-8-sig') as f:
        lines = [l.rstrip('\n') for l in f]
    # 去头部说明（如 "一、单选题" 前的说明段）
    txts = [l.strip() for l in lines]
    return chunk_lines(txts)


def parse_docx(path):
    import docx
    doc = docx.Document(path)
    txts = [p.text.strip() for p in doc.paragraphs if p.text.strip()]
    return chunk_lines(txts)


def parse_pdf(path):
    """文字版 PDF 提取文本切块；扫描版返回 None"""
    try:
        import fitz
    except ImportError:
        raise RuntimeError('缺少 PyMuPDF 库')
    doc = fitz.open(path)
    all_lines = []
    for page in doc:
        text = page.get_text()
        if not text.strip():
            continue
        for line in text.split('\n'):
            s = line.strip()
            if s:
                all_lines.append(s)
    if not all_lines:
        return None, None  # 扫描版
    return chunk_lines(all_lines)


def parse_xls(path):
    """xls 保持三列模式（题型/题干/选项/答案）"""
    import xlrd
    wb = xlrd.open_workbook(path, on_demand=True)
    name = os.path.splitext(os.path.basename(path))[0]
    blocks = []
    seen = set()
    for si in range(wb.nsheets):
        sh = wb.sheet_by_index(si)
        header_row = None
        for r in range(min(10, sh.nrows)):
            vals = [str(sh.cell_value(r, c)).strip() for c in range(min(sh.ncols, 14))]
            if any(v in ('题型', '题目内容', '题干') for v in vals):
                header_row = r
                break
        if header_row is None:
            header_row = 2
        cols = {str(sh.cell_value(header_row, c)).strip(): c for c in range(min(sh.ncols, 14))}
        type_col = cols.get('题型')
        stem_col = cols.get('题目内容') or cols.get('题干')
        opt_col = cols.get('可选项')
        ans_col = cols.get('答案') or cols.get('正确答案')

        for r in range(header_row + 1, sh.nrows):
            stem = str(sh.cell_value(r, stem_col)).strip() if stem_col is not None else ''
            if not stem or stem in ('题目内容', '题干'):
                continue
            key = stem[:40]
            if key in seen:
                continue
            seen.add(key)
            options = []
            if opt_col is not None:
                for part in re.split(r'[;；\t]+', str(sh.cell_value(r, opt_col))):
                    p = part.strip()
                    if p:
                        options.append(p)
            ans = str(sh.cell_value(r, ans_col)).strip() if ans_col is not None else ''
            lines = [stem]
            for i, opt in enumerate(options):
                letter = chr(ord('A') + i)
                opt_clean = re.sub(r'^[A-Za-z][.、．)）]\s*', '', opt.strip())
                lines.append(f"{letter}. {opt_clean}")
            if ans and ans not in ('无', ''):
                lines.append(f"答案:{ans}")
            lines.append(f"来源:{name}")
            blocks.append("\n".join(lines) + "\n\n")
    return blocks


# ============================================================
# 主流程
# ============================================================

def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    src, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)

    files = []
    if os.path.isdir(src):
        for ext in ('.xls', '.xlsx', '.docx', '.txt', '.pdf'):
            files += glob.glob(os.path.join(src, '**', '*' + ext), recursive=True)
    else:
        files = [src]

    total_ok = 0
    total_fail = 0
    for f in sorted(files):
        if os.path.basename(f).startswith('~$'):
            continue
        ext = os.path.splitext(f)[1].lower()
        base = os.path.splitext(os.path.basename(f))[0]
        try:
            if ext == '.xls':
                blocks = parse_xls(f)
                if not blocks:
                    raise RuntimeError('未解析到任何题目')
                out_file = os.path.join(out_dir, base + '.txt')
                with open(out_file, 'w', encoding='utf-8') as fp:
                    fp.write(''.join(blocks))
                print(f'✅ {base}.txt  ({len(blocks)} 题)')
                total_ok += len(blocks)
            elif ext in ('.txt', '.docx'):
                chunks, diag = (parse_txt(f) if ext == '.txt' else parse_docx(f))
                if not chunks:
                    raise RuntimeError('未识别到任何题目')
                # 最坏情况甄别：无序号 + 无空行 + 有纯文本
                if not diag['has_numbered'] and not diag['no_number_with_option'] and not diag['blank_separated']:
                    print(f'⚠️  {base}: 无序号且无空行分隔，无法可靠切块，已跳过（请人工修改后重试）')
                    total_fail += 1
                    continue
                out_file = os.path.join(out_dir, base + '.txt')
                with open(out_file, 'w', encoding='utf-8') as fp:
                    for qid, lines in chunks:
                        fp.write(fmt_chunk(qid, lines))
                print(f'✅ {base}.txt  ({len(chunks)} 题)')
                total_ok += len(chunks)
            elif ext == '.pdf':
                chunks, diag = parse_pdf(f)
                if chunks is None:
                    print(f'❌ {base}: 扫描版 PDF（无文本层），无法导入')
                    total_fail += 1
                    continue
                if not chunks:
                    raise RuntimeError('未识别到任何题目')
                out_file = os.path.join(out_dir, base + '.txt')
                with open(out_file, 'w', encoding='utf-8') as fp:
                    for qid, lines in chunks:
                        fp.write(fmt_chunk(qid, lines))
                print(f'✅ {base}.txt  ({len(chunks)} 题)')
                total_ok += len(chunks)
            else:
                print(f'⚠️  {base}: 不支持的格式 {ext}')
                total_fail += 1
        except Exception as e:
            print(f'❌ {base}: {e}')
            total_fail += 1

    print(f'\n===== 导入结果 =====')
    print(f'成功: {total_ok} 题    失败: {total_fail} 个文件')
    print(f'输出目录: {out_dir}')


if __name__ == '__main__':
    main()
