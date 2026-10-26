import markdown
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent
SOURCE = ROOT / "BAO_CAO_ENGQA.md"
OUTPUT = ROOT / "BAO_CAO_ENGQA.html"

text = SOURCE.read_text(encoding="utf-8")

# The report uses a few HTML images with width attributes; enable raw HTML in Markdown.
html_body = markdown.markdown(
    text,
    extensions=["tables", "fenced_code", "toc", "md_in_html", "sane_lists"],
    extension_configs={"toc": {"toc_depth": "1-3"}},
)

css = """
@page {
  size: A4;
  margin: 18mm 16mm 18mm 16mm;
}
* { box-sizing: border-box; }
body {
  font-family: "Segoe UI", "Noto Sans", Arial, sans-serif;
  font-size: 10.5pt;
  line-height: 1.55;
  color: #1b2430;
  margin: 0;
  background: #ffffff;
}
h1, h2, h3, h4 { color: #0f2742; line-height: 1.25; page-break-after: avoid; }
h1 { font-size: 20pt; margin: 0 0 12pt; border-bottom: 2px solid #ff6b35; padding-bottom: 6pt; }
h2 { font-size: 14.5pt; margin: 16pt 0 7pt; }
h3 { font-size: 12pt; margin: 12pt 0 5pt; }
p { margin: 0 0 7pt; text-align: justify; }
ul, ol { margin: 0 0 8pt 18pt; padding: 0; }
li { margin-bottom: 3pt; }
table { width: 100%; border-collapse: collapse; margin: 8pt 0 12pt; font-size: 9pt; page-break-inside: avoid; }
th, td { border: 1px solid #c9d3df; padding: 5pt 6pt; vertical-align: top; }
th { background: #eef3f8; color: #0f2742; text-align: left; }
tr:nth-child(even) td { background: #f8fafc; }
code { font-family: "Cascadia Code", Consolas, monospace; font-size: 8.5pt; background: #f1f5f9; padding: 1pt 3pt; border-radius: 3pt; }
pre { background: #0f1720; color: #e6edf5; padding: 8pt 10pt; border-radius: 5pt; overflow-x: auto; font-size: 8.5pt; line-height: 1.4; page-break-inside: avoid; }
pre code { background: transparent; color: inherit; padding: 0; }
img { max-width: 100%; height: auto; display: block; margin: 6pt auto 4pt; border: 1px solid #dbe3ec; border-radius: 4pt; }
.fig { text-align: center; font-size: 8.5pt; font-style: italic; color: #5b6b7f; margin: 0 0 10pt; }
blockquote { border-left: 3pt solid #ff6b35; background: #fff7f0; margin: 8pt 0; padding: 6pt 10pt; color: #4a3527; }
a { color: #0b63b6; text-decoration: none; }
.page-break { page-break-before: always; }
.cover {
  min-height: 245mm;
  display: flex;
  flex-direction: column;
  justify-content: center;
  text-align: center;
  border: 1px solid #dbe3ec;
  padding: 22mm 18mm;
}
.cover-university { font-size: 13pt; font-weight: 700; letter-spacing: 0.4pt; margin: 0; color: #0f2742; }
.cover-faculty { font-size: 11pt; margin: 4pt 0 18pt; color: #43556b; }
.cover-mark { font-size: 56pt; font-weight: 800; color: #ff6b35; letter-spacing: 3pt; margin: 16pt 0 8pt; }
.cover-title { font-size: 22pt; border: 0; margin: 0 0 6pt; padding: 0; color: #0f2742; }
.cover-subtitle { font-size: 12pt; color: #43556b; margin: 0 0 20pt; }
.cover-table { width: 78%; margin: 0 auto 18pt; font-size: 10pt; }
.cover-table td { border: 0; padding: 4pt 6pt; }
.cover-table td:first-child { text-align: right; color: #43556b; width: 38%; }
.cover-table td:last-child { text-align: left; font-weight: 600; }
.cover-note { font-size: 9pt; color: #6b7b8f; margin-top: 12pt; }
.toc ul { list-style: none; margin-left: 0; }
.toc li { margin: 3pt 0; }
"""

# Make the cover a page of its own and keep anchors stable.
html = f"""<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="utf-8">
<title>Báo cáo đồ án EngQA</title>
<style>{css}</style>
</head>
<body>
{html_body}
</body>
</html>
"""

OUTPUT.write_text(html, encoding="utf-8")
print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")
