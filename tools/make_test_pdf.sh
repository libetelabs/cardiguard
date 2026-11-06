#!/usr/bin/env bash
# 시험 기록 markdown 을 제출용 PDF 로 찍는다.
#
# pandoc 으로 HTML 을 만들고 Chrome 의 인쇄 기능으로 PDF 를 뽑는다.
# wkhtmltopdf·weasyprint 는 한글 폰트가 깨져서 쓰지 않는다.
#
# 사용: bash tools/make_test_pdf.sh
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MD="$ROOT/docs/test-log-2026-11-03.md"
PDF="$ROOT/docs/proposal/카디가드_시험기록_2026-11-03.pdf"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/style.css" <<'CSS'
@page { size: A4; margin: 18mm 16mm; }
body {
  font-family: Pretendard, -apple-system, "Apple SD Gothic Neo", sans-serif;
  font-size: 10.5pt; line-height: 1.7; color: #14161a; margin: 0;
}
h1 { font-size: 20pt; margin: 0 0 .6em; letter-spacing: -.02em; }
h2 { font-size: 13pt; margin: 1.8em 0 .6em; padding-top: .8em; border-top: 1px solid #d3d7dd; }
code, pre { font-family: Menlo, monospace; font-size: 9pt; }
code { background: #f1f3f5; padding: .1em .3em; border-radius: 3px; }
/* 시험 로그 한 덩이가 한 쪽을 거의 다 먹는다. 통째로 넘기면 앞 쪽이 반 비어 버린다. */
pre { background: #f7f8fa; padding: 14px 16px; border-radius: 6px; line-height: 1.5;
      white-space: pre-wrap; }
h2 { page-break-after: avoid; }
pre code { background: none; padding: 0; }
table { border-collapse: collapse; width: 100%; margin: .8em 0; }
th, td { border: 1px solid #d3d7dd; padding: 7px 11px; text-align: left; vertical-align: top; }
th { background: #f1f3f5; }
ol, ul { padding-left: 1.3em; }
li { margin: .35em 0; }
CSS

pandoc "$MD" -f gfm -t html5 --standalone --metadata title="" \
       --css style.css -o "$TMP/doc.html"
"$CHROME" --headless --disable-gpu --no-pdf-header-footer \
          --print-to-pdf="$TMP/out.pdf" "file://$TMP/doc.html" >/dev/null 2>&1
mv "$TMP/out.pdf" "$PDF"
echo "$PDF  $(stat -f%z "$PDF") bytes"
