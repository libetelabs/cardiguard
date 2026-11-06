"""접수양식 02 장을 기획서 내용으로 채운다.

사용: python3 tools/fill_form.py <빈 양식.docx> <기획서.md> <출력.docx>

양식의 내용 컨트롤(sdt)은 비어 있고, 실제 글은 각 항목 문단 **뒤에 이어지는 본문 문단**에
들어간다. 그래서 컨트롤을 건드리지 않고 그 문단 뒤에 문단을 끼워 넣는다.
표는 Word 표로 만들지 않고 "머리: 값" 꼴의 줄로 편다. 심사자가 읽는 데 지장이 없고,
양식의 서식을 건드리지 않는 쪽이 안전하다.
"""
import html
import re
import shutil
import sys
import zipfile

SRC, MD, OUT = sys.argv[1], sys.argv[2], sys.argv[3]

# ── 기획서 markdown 을 절별 문단 목록으로 ──────────────────────────────────
def parse(md: str) -> dict[int, list[str]]:
    sections: dict[int, list[str]] = {}
    cur = None
    buf: list[str] = []
    pending_head: list[str] = []
    for raw in md.splitlines():
        m = re.match(r"^##\s+(\d)\.\s", raw)
        if m:
            if cur:
                sections[cur] = buf
            cur, buf, pending_head = int(m.group(1)), [], []
            continue
        if cur is None:
            continue
        line = raw.rstrip()
        if line.startswith("---") or line.startswith(">"):
            continue
        if not line:
            # 빈 줄은 문단 경계다. 이어 붙이지 않는다. 표도 여기서 끝난다.
            # 머리행을 남겨 두면 다음 표의 머리행이 앞 표의 데이터 행으로 먹힌다.
            pending_head = []
            if buf and buf[-1] != "":
                buf.append("")
            continue
        if line.startswith("###"):
            pending_head = []
            buf.append("[" + line.lstrip("# ").strip() + "]")
            continue
        if line.startswith("|"):
            cells = [c.strip() for c in line.strip("|").split("|")]
            if all(re.fullmatch(r":?-{2,}:?", c) for c in cells if c):
                continue
            # 빈 칸을 버리면 안 된다. 머리글 첫 칸이 비는 표(| | A | B |)에서 칸이
            # 한 자리씩 밀려 마지막 열이 통째로 떨어지고, 행 이름이 옆 열의 머리글을
            # 달고 나온다. 「원인 / 현재: 없음 / 카디가드: ...」가
            # 「현재: 원인 / 카디가드: 없음」이 됐다: 뜻이 거꾸로다.
            if not any(cells):
                continue
            if not pending_head:                 # 표 머리행
                pending_head = cells
                continue
            head = pending_head
            pairs = []
            for i, c in enumerate(cells):
                if not c:
                    continue
                h = head[i] if i < len(head) else ""
                pairs.append(f"{h}: {c}" if h else c)   # 머리글이 빈 열은 행 이름이다
            buf.append("· " + " / ".join(pairs))
            continue
        pending_head = []
        if re.match(r"^[-*]\s+", line):
            buf.append(re.sub(r"^[-*]\s+", "· ", line))
        elif buf and buf[-1] and not buf[-1].startswith(("·", "[")):
            buf[-1] += " " + line          # 마크다운 소프트랩은 같은 문단이다
        else:
            buf.append(line)
    if cur:
        sections[cur] = buf
    return {k: [strip_md(x) for x in v if x.strip()] for k, v in sections.items()}


def strip_md(t: str) -> str:
    t = re.sub(r"\*\*(.+?)\*\*", r"\1", t)
    t = re.sub(r"`(.+?)`", r"\1", t)
    return t.strip()


def para(text: str) -> str:
    """양식 본문과 같은 모양의 문단 하나."""
    bold = text.startswith("[") and text.endswith("]")
    if bold:
        text = text[1:-1]
    rpr = '<w:rPr><w:b/></w:rPr>' if bold else ""
    return (
        '<w:p><w:pPr><w:spacing w:after="60" w:line="276" w:lineRule="auto"/></w:pPr>'
        f'<w:r>{rpr}<w:t xml:space="preserve">{html.escape(text)}</w:t></w:r></w:p>'
    )


sections = parse(open(MD, encoding="utf-8").read())
shutil.copy(SRC, OUT)

zin = zipfile.ZipFile(SRC)
doc = zin.read("word/document.xml").decode("utf-8")

# sdt 를 품은 문단 뒤에 그 절의 본문을 끼운다. 뒤에서부터 넣어야 위치가 밀리지 않는다.
spans = []
for m in re.finditer(r"<w:sdt>.*?</w:sdt>", doc, re.S):
    start = doc.rfind("<w:p>", 0, m.start())
    alt = doc.rfind("<w:p ", 0, m.start())
    start = max(start, alt)
    end = doc.index("</w:p>", m.end()) + len("</w:p>")
    spans.append(end)

if len(spans) != 8:
    sys.exit(f"항목 수가 8개가 아니다({len(spans)}개). 양식이 바뀌었는지 확인할 것.")

for idx in range(8, 0, -1):
    body = sections.get(idx)
    if not body:
        continue
    at = spans[idx - 1]
    doc = doc[:at] + "".join(para(t) for t in body) + doc[at:]

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = doc.encode("utf-8") if item.filename == "word/document.xml" else zin.read(item.filename)
        zout.writestr(item, data)

print(f"{OUT}: 채운 항목 {sum(1 for i in range(1, 9) if sections.get(i))}개")
for i in range(1, 9):
    print(f"  {i}. 문단 {len(sections.get(i, []))}개")
