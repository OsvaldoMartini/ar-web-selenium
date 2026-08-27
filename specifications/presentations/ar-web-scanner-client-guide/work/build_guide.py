#!/usr/bin/env python3
"""Build the AR Web Scanner HTML, DOCX, PDF, and video-step manifest.

The Markdown file in the parent directory remains the content source of truth.
The DOCX writer deliberately uses standard OOXML so this repository does not
need a new Python dependency merely to publish the client guide.
"""

from __future__ import annotations

import html
import json
import re
import sys
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from xml.sax.saxutils import escape as xml_escape

import markdown
from markdown.extensions.toc import TocExtension
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    KeepTogether,
    ListFlowable,
    ListItem,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "AR-Web-Scanner-Complete-Client-Guide.md"
DOCX = ROOT / "AR-Web-Scanner-Complete-Client-Guide.docx"
PDF = ROOT / "AR-Web-Scanner-Complete-Client-Guide.pdf"


def write_utf8(path: Path, value: str) -> None:
    """Write deterministic UTF-8/LF text artifacts on every host OS."""
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(value)


def slugify(value: str, separator: str = "-") -> str:
    value = re.sub(r"[`*_]", "", value).strip().lower()
    value = re.sub(r"[^\w\s-]", "", value, flags=re.UNICODE)
    return re.sub(r"[-\s]+", separator, value).strip(separator) or "section"


def strip_inline(value: str) -> str:
    value = re.sub(r"!\[([^]]*)\]\([^)]*\)", r"\1", value)
    value = re.sub(r"\[([^]]+)\]\([^)]*\)", r"\1", value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"\1", value)
    value = re.sub(r"`([^`]+)`", r"\1", value)
    return value.replace("  ", " ").strip()


@dataclass
class Heading:
    level: int
    text: str
    anchor: str


def headings(markdown_text: str) -> list[Heading]:
    result: list[Heading] = []
    used: dict[str, int] = {}
    for raw in markdown_text.splitlines():
        match = re.match(r"^(#{1,3})\s+(.+?)\s*$", raw)
        if not match:
            continue
        text = strip_inline(match.group(2))
        base = slugify(text)
        count = used.get(base, 0)
        used[base] = count + 1
        anchor = base if count == 0 else f"{base}-{count + 1}"
        result.append(Heading(len(match.group(1)), text, anchor))
    return result


def markdown_html(markdown_text: str) -> str:
    return markdown.markdown(
        markdown_text,
        extensions=[
            "tables",
            "sane_lists",
            "fenced_code",
            TocExtension(slugify=slugify, permalink=False),
        ],
        output_format="html5",
    )


def sidebar(items: list[Heading], prefix: str = "") -> str:
    links = []
    for item in items:
        if item.level > 2:
            continue
        cls = "depth-2" if item.level == 2 else "depth-1"
        links.append(
            f'<a class="{cls}" href="{prefix}#{html.escape(item.anchor)}">'
            f"{html.escape(item.text)}</a>"
        )
    return "\n".join(links)


PARTS = [
    ("Overview", "index.html"),
    ("Part 1 · Setup", "parts/part-1-setup.html"),
    ("Part 2 · Bot Job", "parts/part-2-bot-job.html"),
    ("Part 3 · Scanner", "parts/part-3-page-scanner.html"),
    ("Part 4 · Data", "parts/part-4-data.html"),
    ("Part 5 · Help", "parts/part-5-troubleshooting.html"),
]


def part_navigation(relative_depth: int, current: str) -> str:
    root = "../" * relative_depth
    links = []
    for label, target in PARTS:
        href = root + target
        current_attr = ' aria-current="page"' if target == current else ""
        links.append(f'<a href="{href}"{current_attr}>{html.escape(label)}</a>')
    return '<nav class="part-nav" aria-label="Guide parts">' + "".join(links) + "</nav>"


def html_shell(title: str, body: str, items: list[Heading], depth: int, current: str) -> str:
    root = "../" * depth
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html.escape(title)} · AR Web Scanner Guide</title>
  <link rel="stylesheet" href="{root}assets/guide.css">
</head>
<body>
  <header class="site-header">
    <div class="brand"><strong>AR Web Scanner</strong><span>{html.escape(title)}</span></div>
    <div class="header-actions">
      <input type="search" data-guide-search placeholder="Search this guide part" aria-label="Search guide">
      <span class="search-count" data-search-count>All sections</span>
      <a class="classic-button" href="{root}AR-Web-Scanner-Complete-Client-Guide.pdf">PDF</a>
    </div>
  </header>
  <div class="layout">
    <aside class="sidebar"><h2>On this page</h2><nav>{sidebar(items)}</nav></aside>
    <main class="main">
      {part_navigation(depth, current)}
      <article class="document">{body}</article>
    </main>
  </div>
  <footer class="guide-footer">Source-verified client operations guide · Screenshot capture pending a safe synthetic session</footer>
  <script src="{root}assets/guide.js"></script>
</body>
</html>
"""


def split_parts(text: str) -> list[tuple[str, str, str]]:
    markers = list(re.finditer(r"(?m)^# Part ([1-5])\s+—\s+(.+)$", text))
    result: list[tuple[str, str, str]] = []
    for index, marker in enumerate(markers):
        end = markers[index + 1].start() if index + 1 < len(markers) else text.find("\n# 8. Glossary", marker.start())
        if end < 0:
            end = len(text)
        number = marker.group(1)
        label = marker.group(2).strip()
        result.append((number, label, text[marker.start():end].strip() + "\n"))
    return result


def build_html(text: str) -> None:
    all_headings = headings(text)
    write_utf8(
        ROOT / "index.html",
        html_shell("Complete Guide", markdown_html(text), all_headings, 0, "index.html"),
    )
    paths = {
        "1": "parts/part-1-setup.html",
        "2": "parts/part-2-bot-job.html",
        "3": "parts/part-3-page-scanner.html",
        "4": "parts/part-4-data.html",
        "5": "parts/part-5-troubleshooting.html",
    }
    for number, label, part_text in split_parts(text):
        target = paths[number]
        path = ROOT / target
        write_utf8(
            path,
            html_shell(f"Part {number} · {label}", markdown_html(part_text), headings(part_text), 1, target),
        )


def build_steps(text: str) -> None:
    lines = text.splitlines()
    steps: list[dict[str, object]] = []
    current_h1 = ""
    for index, line in enumerate(lines):
        if line.startswith("# "):
            current_h1 = strip_inline(line[2:])
        if not line.startswith("## "):
            continue
        title = strip_inline(line[3:])
        next_heading = next((j for j in range(index + 1, len(lines)) if lines[j].startswith("#")), len(lines))
        section = "\n".join(lines[index + 1:next_heading])
        figure = re.search(r"Figure\s+(\d+)\s+—\s+([^*\n]+)", section)
        screenshot = None
        if figure:
            screen_id = figure.group(1)
            screenshot_plan = next(
                (line for line in (ROOT / "screenshots" / "README.md").read_text(encoding="utf-8").splitlines()
                 if re.match(rf"\|\s*{re.escape(screen_id)}\s*\|", line)),
                "",
            )
            match = re.search(r"`([^`]+\.png)`", screenshot_plan)
            screenshot = match.group(1) if match else None
        steps.append({
            "id": len(steps) + 1,
            "chapter": current_h1,
            "title": title,
            "anchor": slugify(title),
            "screenshot": screenshot,
            "screenshotStatus": "PENDING" if screenshot else "NOT_REQUIRED",
            "caution": next((strip_inline(value[2:].strip()) for value in section.splitlines() if value.startswith("> ")), ""),
        })
    payload = {
        "schemaVersion": 1,
        "guide": "AR Web Scanner Complete Client Guide",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": SOURCE.name,
        "steps": steps,
    }
    write_utf8(ROOT / "guide-steps.json", json.dumps(payload, indent=2, ensure_ascii=False) + "\n")


@dataclass
class Block:
    kind: str
    text: str = ""
    level: int = 0
    rows: list[list[str]] | None = None


def parse_blocks(text: str) -> list[Block]:
    lines = text.splitlines()
    blocks: list[Block] = []
    i = 0
    paragraph: list[str] = []

    def flush() -> None:
        if paragraph:
            blocks.append(Block("paragraph", " ".join(value.strip() for value in paragraph)))
            paragraph.clear()

    while i < len(lines):
        line = lines[i]
        heading = re.match(r"^(#{1,3})\s+(.+)$", line)
        if heading:
            flush()
            blocks.append(Block("heading", heading.group(2).strip(), len(heading.group(1))))
            i += 1
            continue
        if line.startswith("| ") and i + 1 < len(lines) and re.match(r"^\|[\s:|-]+\|\s*$", lines[i + 1]):
            flush()
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = [cell.strip() for cell in lines[i].strip().strip("|").split("|")]
                if i == 0 or not all(re.fullmatch(r":?-{3,}:?", cell.replace(" ", "")) for cell in cells):
                    rows.append(cells)
                i += 1
            if len(rows) >= 2 and all(re.fullmatch(r":?-{3,}:?", cell.replace(" ", "")) for cell in rows[1]):
                rows.pop(1)
            blocks.append(Block("table", rows=rows))
            continue
        if line.startswith("> "):
            flush()
            quote = [line[2:].strip()]
            i += 1
            while i < len(lines) and lines[i].startswith("> "):
                quote.append(lines[i][2:].strip())
                i += 1
            blocks.append(Block("quote", " ".join(quote)))
            continue
        bullet = re.match(r"^[-*]\s+(.+)$", line)
        numbered = re.match(r"^(\d+)\.\s+(.+)$", line)
        if bullet or numbered:
            flush()
            if bullet:
                blocks.append(Block("bullet", bullet.group(1)))
            else:
                blocks.append(Block("number", numbered.group(2), int(numbered.group(1))))
            i += 1
            continue
        if not line.strip():
            flush()
        else:
            paragraph.append(line)
        i += 1
    flush()
    return blocks


def bookmark_name(text: str, used: dict[str, int]) -> str:
    base = "b_" + re.sub(r"[^A-Za-z0-9_]", "_", slugify(strip_inline(text), "_"))[:34]
    count = used.get(base, 0)
    used[base] = count + 1
    return base if count == 0 else f"{base}_{count + 1}"


def ooxml_run(text: str, *, bold: bool = False, code: bool = False, size: int | None = None, color: str | None = None) -> str:
    preserve = ' xml:space="preserve"' if text.startswith(" ") or text.endswith(" ") else ""
    props = []
    if bold:
        props.append("<w:b/>")
    if code:
        props.append('<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/>')
        props.append('<w:shd w:fill="EFF1F3"/>')
    if size:
        props.append(f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/>')
    if color:
        props.append(f'<w:color w:val="{color}"/>')
    rpr = f"<w:rPr>{''.join(props)}</w:rPr>" if props else ""
    return f"<w:r>{rpr}<w:t{preserve}>{xml_escape(text)}</w:t></w:r>"


INLINE_PATTERN = re.compile(r"(\*\*[^*]+\*\*|`[^`]+`|\[[^]]+\]\([^)]+\))")


def ooxml_inline(text: str) -> str:
    parts = []
    last = 0
    for match in INLINE_PATTERN.finditer(text):
        if match.start() > last:
            parts.append(ooxml_run(text[last:match.start()]))
        token = match.group(0)
        if token.startswith("**"):
            parts.append(ooxml_run(token[2:-2], bold=True))
        elif token.startswith("`"):
            parts.append(ooxml_run(token[1:-1], code=True))
        else:
            link = re.match(r"\[([^]]+)\]\(([^)]+)\)", token)
            if link and link.group(2).startswith("#"):
                target = bookmark_name_for_anchor(link.group(2)[1:])
                parts.append(f'<w:hyperlink w:anchor="{target}" w:history="1">{ooxml_run(link.group(1), color="154F78")}</w:hyperlink>')
            else:
                parts.append(ooxml_run(link.group(1) if link else token))
        last = match.end()
    if last < len(text):
        parts.append(ooxml_run(text[last:]))
    return "".join(parts)


def bookmark_name_for_anchor(anchor: str) -> str:
    return "b_" + re.sub(r"[^A-Za-z0-9_]", "_", anchor.replace("-", "_"))[:34]


def ooxml_paragraph(text: str, style: str = "BodyText", prefix: str = "") -> str:
    return f'<w:p><w:pPr><w:pStyle w:val="{style}"/></w:pPr>{ooxml_inline(prefix + text)}</w:p>'


def ooxml_table(rows: list[list[str]]) -> str:
    width = max((len(row) for row in rows), default=1)
    grid = "".join(f'<w:gridCol w:w="{9000 // width}"/>' for _ in range(width))
    tr_xml = []
    for r_index, row in enumerate(rows):
        cells = []
        for cell in row + [""] * (width - len(row)):
            shade = '<w:shd w:fill="DFE3E6"/>' if r_index == 0 else ""
            cells.append(
                '<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/>' + shade + '</w:tcPr>'
                + ooxml_paragraph(cell, "TableText") + '</w:tc>'
            )
        row_properties = '<w:trPr><w:cantSplit/>'
        if r_index == 0:
            row_properties += '<w:tblHeader/>'
        row_properties += '</w:trPr>'
        tr_xml.append(f'<w:tr>{row_properties}{"".join(cells)}</w:tr>')
    return (
        '<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:w="0" w:type="auto"/>'
        '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/>'
        f'</w:tblPr><w:tblGrid>{grid}</w:tblGrid>{"".join(tr_xml)}</w:tbl>'
    )


def build_docx(text: str) -> None:
    blocks = parse_blocks(text)
    used: dict[str, int] = {}
    body = []
    first_heading = True
    for block in blocks:
        if block.kind == "heading":
            plain = strip_inline(block.text)
            anchor = slugify(plain)
            bookmark = bookmark_name_for_anchor(anchor)
            style = "Title" if first_heading else f"Heading{block.level}"
            first_heading = False
            body.append(
                f'<w:p><w:pPr><w:pStyle w:val="{style}"/><w:keepNext/></w:pPr>'
                f'<w:bookmarkStart w:id="{len(used) + 1}" w:name="{bookmark}"/>'
                f'{ooxml_inline(block.text)}<w:bookmarkEnd w:id="{len(used) + 1}"/></w:p>'
            )
            used[bookmark] = 1
        elif block.kind == "paragraph":
            body.append(ooxml_paragraph(block.text))
        elif block.kind == "quote":
            body.append(ooxml_paragraph(block.text, "Quote"))
        elif block.kind == "bullet":
            body.append(ooxml_paragraph(block.text, "ListParagraph", "• "))
        elif block.kind == "number":
            body.append(ooxml_paragraph(block.text, "ListParagraph", f"{block.level}. "))
        elif block.kind == "table" and block.rows:
            body.append(ooxml_table(block.rows))

    document = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>{''.join(body)}
<w:sectPr><w:headerReference w:type="default" r:id="rId1"/><w:footerReference w:type="default" r:id="rId2"/>
<w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/>
</w:sectPr></w:body></w:document>'''
    styles = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="19"/><w:szCs w:val="19"/><w:color w:val="17212B"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="110" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="BodyText"><w:name w:val="Body Text"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="110"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="720" w:after="360"/><w:jc w:val="left"/><w:keepNext/></w:pPr><w:rPr><w:b/><w:color w:val="173F5B"/><w:sz w:val="42"/><w:szCs w:val="42"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="300" w:after="150"/><w:keepNext/><w:outlineLvl w:val="0"/><w:pBdr><w:bottom w:val="single" w:sz="10" w:space="4" w:color="78838D"/></w:pBdr></w:pPr><w:rPr><w:b/><w:color w:val="173F5B"/><w:sz w:val="30"/><w:szCs w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="100"/><w:keepNext/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:color w:val="173F5B"/><w:sz w:val="25"/><w:szCs w:val="25"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="180" w:after="80"/><w:keepNext/><w:outlineLvl w:val="2"/></w:pPr><w:rPr><w:b/><w:color w:val="294B62"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:pPr><w:ind w:left="360" w:right="180"/><w:spacing w:before="90" w:after="130"/><w:shd w:fill="FFF7DC"/><w:pBdr><w:left w:val="single" w:sz="22" w:space="8" w:color="B17B05"/></w:pBdr></w:pPr><w:rPr><w:color w:val="4F3B0E"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:pPr><w:ind w:left="360" w:hanging="180"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="TableText"><w:name w:val="Table Text"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="40" w:line="230" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="17"/><w:szCs w:val="17"/></w:rPr></w:style>
<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:color="BBC1C7"/><w:left w:val="single" w:sz="4" w:color="BBC1C7"/><w:bottom w:val="single" w:sz="4" w:color="BBC1C7"/><w:right w:val="single" w:sz="4" w:color="BBC1C7"/><w:insideH w:val="single" w:sz="4" w:color="BBC1C7"/><w:insideV w:val="single" w:sz="4" w:color="BBC1C7"/></w:tblBorders><w:tblCellMar><w:top w:w="90" w:type="dxa"/><w:left w:w="90" w:type="dxa"/><w:bottom w:w="90" w:type="dxa"/><w:right w:w="90" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
</w:styles>'''
    header = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="4" w:space="4" w:color="B6BCC2"/></w:pBdr></w:pPr><w:r><w:rPr><w:b/><w:color w:val="4B5965"/><w:sz w:val="16"/></w:rPr><w:t>AR Web Scanner · Client Operations Guide</w:t></w:r></w:p></w:hdr>'''
    footer = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:color w:val="68737D"/><w:sz w:val="16"/></w:rPr><w:t xml:space="preserve">Authorized client use · Page </w:t></w:r><w:fldSimple w:instr="PAGE"><w:r><w:rPr><w:color w:val="68737D"/><w:sz w:val="16"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>'''
    content_types = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/><Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>'''
    package_rels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>'''
    document_rels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/></Relationships>'''
    settings = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:zoom w:percent="100"/><w:updateFields w:val="true"/><w:defaultTabStop w:val="720"/></w:settings>'''
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    core = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>AR Web Scanner Complete Client Guide</dc:title><dc:subject>Client operations reference</dc:subject><dc:creator>AllinWeb</dc:creator><cp:keywords>AR Web Scanner; Bot Job; Page Scanner; Excel Data; Locator Recovery</cp:keywords><dc:description>Source-verified guide for authorized AR Web Scanner client operation.</dc:description><dcterms:created xsi:type="dcterms:W3CDTF">{now}</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">{now}</dcterms:modified></cp:coreProperties>'''
    app = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"><Application>AR Web Scanner Guide Builder</Application><Company>AllinWeb</Company><AppVersion>1.0</AppVersion></Properties>'''
    DOCX.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DOCX, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", package_rels)
        archive.writestr("docProps/core.xml", core)
        archive.writestr("docProps/app.xml", app)
        archive.writestr("word/document.xml", document)
        archive.writestr("word/styles.xml", styles)
        archive.writestr("word/settings.xml", settings)
        archive.writestr("word/header1.xml", header)
        archive.writestr("word/footer1.xml", footer)
        archive.writestr("word/_rels/document.xml.rels", document_rels)


def pdf_inline(text: str) -> str:
    escaped = html.escape(text)
    escaped = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", escaped)
    escaped = re.sub(r"`([^`]+)`", r'<font name="ArialMono">\1</font>', escaped)
    escaped = re.sub(r"\[([^]]+)\]\((#[^)]+)\)", r'<link href="\2" color="#154f78">\1</link>', escaped)
    return escaped


class GuideDocTemplate(BaseDocTemplate):
    def __init__(self, filename: str, **kwargs):
        super().__init__(filename, **kwargs)
        frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height, id="body")
        self.addPageTemplates(PageTemplate(id="main", frames=[frame], onPage=self.header_footer))

    @staticmethod
    def header_footer(canvas, doc):
        canvas.saveState()
        canvas.setStrokeColor(colors.HexColor("#B6BCC2"))
        canvas.setLineWidth(.4)
        canvas.line(18 * mm, A4[1] - 14 * mm, A4[0] - 18 * mm, A4[1] - 14 * mm)
        canvas.setFont("Arial", 8)
        canvas.setFillColor(colors.HexColor("#4B5965"))
        canvas.drawString(18 * mm, A4[1] - 11 * mm, "AR Web Scanner · Client Operations Guide")
        canvas.setFillColor(colors.HexColor("#68737D"))
        canvas.drawCentredString(A4[0] / 2, 10 * mm, f"Authorized client use · Page {doc.page}")
        canvas.restoreState()


def register_fonts() -> None:
    fonts = Path("C:/Windows/Fonts")
    regular = fonts / "arial.ttf"
    bold = fonts / "arialbd.ttf"
    mono = fonts / "consola.ttf"
    if regular.exists():
        pdfmetrics.registerFont(TTFont("Arial", str(regular)))
        pdfmetrics.registerFont(TTFont("Arial-Bold", str(bold if bold.exists() else regular)))
    else:
        pdfmetrics.registerFont(TTFont("Arial", str(fonts / "segoeui.ttf")))
        pdfmetrics.registerFont(TTFont("Arial-Bold", str(fonts / "segoeuib.ttf")))
    pdfmetrics.registerFont(TTFont("ArialMono", str(mono if mono.exists() else regular)))


def build_pdf(text: str) -> None:
    register_fonts()
    sample = getSampleStyleSheet()
    styles = {
        "body": ParagraphStyle("Body", parent=sample["BodyText"], fontName="Arial", fontSize=9.2, leading=12.4, textColor=colors.HexColor("#17212B"), spaceAfter=5),
        "title": ParagraphStyle("Title", parent=sample["Title"], fontName="Arial-Bold", fontSize=25, leading=28, textColor=colors.HexColor("#173F5B"), alignment=TA_LEFT, spaceAfter=16),
        "h1": ParagraphStyle("H1", parent=sample["Heading1"], fontName="Arial-Bold", fontSize=17, leading=20, textColor=colors.HexColor("#173F5B"), spaceBefore=8, spaceAfter=8),
        "h2": ParagraphStyle("H2", parent=sample["Heading2"], fontName="Arial-Bold", fontSize=13.2, leading=16, textColor=colors.HexColor("#173F5B"), spaceBefore=10, spaceAfter=6),
        "h3": ParagraphStyle("H3", parent=sample["Heading3"], fontName="Arial-Bold", fontSize=11, leading=13, textColor=colors.HexColor("#294B62"), spaceBefore=8, spaceAfter=4),
        "quote": ParagraphStyle("Quote", parent=sample["BodyText"], fontName="Arial", fontSize=8.8, leading=12, textColor=colors.HexColor("#4F3B0E"), backColor=colors.HexColor("#FFF7DC"), borderColor=colors.HexColor("#B17B05"), borderWidth=.7, borderPadding=7, leftIndent=8, rightIndent=4, spaceBefore=5, spaceAfter=7),
        "cell": ParagraphStyle("Cell", parent=sample["BodyText"], fontName="Arial", fontSize=7.3, leading=9.2, spaceAfter=0),
        "cellhead": ParagraphStyle("CellHead", parent=sample["BodyText"], fontName="Arial-Bold", fontSize=7.4, leading=9.3, spaceAfter=0),
    }
    story = []
    first_heading = True
    for block in parse_blocks(text):
        if block.kind == "heading":
            plain = strip_inline(block.text)
            anchor = slugify(plain)
            if block.level == 1 and not first_heading:
                story.append(PageBreak())
            style = styles["title"] if first_heading else styles[f"h{block.level}"]
            story.append(Paragraph(f'<a name="{anchor}"/>{pdf_inline(block.text)}', style))
            first_heading = False
        elif block.kind == "paragraph":
            story.append(Paragraph(pdf_inline(block.text), styles["body"]))
        elif block.kind == "quote":
            story.append(Paragraph(pdf_inline(block.text), styles["quote"]))
        elif block.kind in {"bullet", "number"}:
            bullet_type = "bullet" if block.kind == "bullet" else "1"
            list_options = {
                "bulletType": bullet_type,
                "leftIndent": 14,
                "bulletFontName": "Arial",
                "bulletFontSize": 8.5,
            }
            if block.kind == "number":
                list_options["start"] = block.level
            story.append(ListFlowable([ListItem(Paragraph(pdf_inline(block.text), styles["body"]))], **list_options))
        elif block.kind == "table" and block.rows:
            width = max(len(row) for row in block.rows)
            data = []
            for r_index, row in enumerate(block.rows):
                style = styles["cellhead"] if r_index == 0 else styles["cell"]
                data.append([Paragraph(pdf_inline(cell), style) for cell in row + [""] * (width - len(row))])
            table = Table(data, colWidths=[(A4[0] - 36 * mm) / width] * width, repeatRows=1, hAlign="LEFT", splitByRow=1)
            table.setStyle(TableStyle([
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#DFE3E6")),
                ("TEXTCOLOR", (0, 0), (-1, -1), colors.HexColor("#17212B")),
                ("GRID", (0, 0), (-1, -1), .35, colors.HexColor("#BBC1C7")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 4),
                ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]))
            story.extend([Spacer(1, 4), table, Spacer(1, 8)])
    doc = GuideDocTemplate(str(PDF), pagesize=A4, rightMargin=18 * mm, leftMargin=18 * mm, topMargin=20 * mm, bottomMargin=18 * mm, title="AR Web Scanner Complete Client Guide", author="AllinWeb")
    doc.build(story)


def validate_html() -> dict[str, object]:
    files = [ROOT / "index.html", *sorted((ROOT / "parts").glob("*.html"))]
    failures: list[str] = []
    for path in files:
        text = path.read_text(encoding="utf-8")
        ids = set(re.findall(r'\sid="([^"]+)"', text))
        for href in re.findall(r'href="([^"]+)"', text):
            if href.startswith("#") and href[1:] not in ids:
                failures.append(f"{path.name}: missing internal anchor {href}")
                continue
            if href.startswith(("http://", "https://", "#")):
                continue
            target = (path.parent / href.split("#", 1)[0]).resolve()
            if not target.exists():
                failures.append(f"{path.name}: missing file {href}")
    report = {"htmlFiles": len(files), "failures": failures, "passed": not failures}
    write_utf8(ROOT / "work" / "html-validation.json", json.dumps(report, indent=2) + "\n")
    return report


def main() -> int:
    text = SOURCE.read_text(encoding="utf-8")
    build_html(text)
    build_steps(text)
    build_docx(text)
    build_pdf(text)
    report = validate_html()
    result = {
        "source": str(SOURCE),
        "html": str(ROOT / "index.html"),
        "docx": str(DOCX),
        "pdf": str(PDF),
        "htmlValidation": report,
    }
    write_utf8(ROOT / "work" / "build-report.json", json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    return 0 if report["passed"] else 2


if __name__ == "__main__":
    sys.exit(main())
