#!/usr/bin/env python3
"""Собирает главы учебника (Markdown) в HTML-ассеты Android-приложения.

Использование (из корня репозитория):
    python3 tools/build_html.py

Вход:  textbook/NN-slug.md   (front matter: title, subtitle)
Выход: app/src/main/assets/chapters/NN-slug.html
       app/src/main/assets/index.json
"""

from __future__ import annotations

import html as html_mod
import json
import re
import sys
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = ROOT / "textbook"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "chapters"
INDEX_PATH = ROOT / "app" / "src" / "main" / "assets" / "index.json"
SEARCH_PATH = ROOT / "app" / "src" / "main" / "assets" / "search_index.json"

TAG_RE = re.compile(r"<[^>]+>")
ARTICLE_RE = re.compile(r"<article.*?</article>", re.S)


def html_to_text(fragment: str) -> str:
    """Превращает HTML-фрагмент в чистый текст для полнотекстового поиска."""
    text = TAG_RE.sub(" ", fragment)
    text = html_mod.unescape(text)
    text = re.sub(r"[ \t\u00a0]+", " ", text)
    text = re.sub(r" *\n *", "\n", text)
    text = re.sub(r"\n{2,}", "\n\n", text)
    return text.strip()

FRONT_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.S)

HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{title}</title>
<link rel="stylesheet" href="../style.css">
</head>
<body>
<article class="chapter">
<header class="chapter-header">
<p class="chapter-kicker">Глава {number}</p>
<h1 class="chapter-title">{title}</h1>
<p class="chapter-subtitle">{subtitle}</p>
</header>
{toc}
{body}
</article>
</body>
</html>
"""


def parse_front_matter(text: str) -> tuple[dict, str]:
    m = FRONT_RE.match(text)
    if not m:
        raise ValueError("отсутствует front matter (--- ... ---)")
    meta: dict[str, str] = {}
    for line in m.group(1).splitlines():
        if not line.strip():
            continue
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()
    return meta, text[m.end():]


def strip_heading(body: str) -> str:
    """Убирает ведущий H1 из Markdown — H1 генерирует шаблон."""
    lines = body.splitlines()
    for i, line in enumerate(lines):
        if line.startswith("# "):
            return "\n".join(lines[:i] + lines[i + 1:]).lstrip("\n")
    return body


def build() -> int:
    if not SRC_DIR.is_dir():
        print(f"нет каталога {SRC_DIR}", file=sys.stderr)
        return 1

    sources = sorted(SRC_DIR.glob("*.md"))
    if not sources:
        print(f"нет глав в {SRC_DIR}", file=sys.stderr)
        return 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # Удаляем устаревшие HTML, чтобы главы не «кочевали» по номерам
    for old in OUT_DIR.glob("*.html"):
        old.unlink()

    extensions = ["tables", "fenced_code", "sane_lists", "toc", "attr_list"]
    extension_configs = {"toc": {"toc_depth": "2-2", "permalink": False}}

    index = []
    search_index = []
    for number, src in enumerate(sources, start=1):
        raw = src.read_text(encoding="utf-8")
        meta, body = parse_front_matter(raw)
        title = meta.get("title", src.stem)
        subtitle = meta.get("subtitle", "")
        body = strip_heading(body)

        md = markdown.Markdown(extensions=extensions, extension_configs=extension_configs)
        body_html = md.convert(body)
        toc_html = ""
        if getattr(md, "toc", "").strip():
            toc_html = f'<nav class="toc-box"><p class="toc-caption">Содержание главы</p>{md.toc}</nav>'

        html = HTML_TEMPLATE.format(
            number=number,
            title=title,
            subtitle=subtitle,
            toc=toc_html,
            body=body_html,
        )
        out_file = OUT_DIR / f"{src.stem}.html"
        out_file.write_text(html, encoding="utf-8")

        index.append(
            {
                "id": src.stem,
                "file": out_file.name,
                "title": title,
                "subtitle": subtitle,
            }
        )

        article = ARTICLE_RE.search(html)
        plain = html_to_text(article.group(0) if article else html)
        search_index.append({"id": src.stem, "title": title, "text": plain})
        print(f"  [{number:02d}] {src.name} -> {out_file.name}")

    INDEX_PATH.write_text(
        json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    SEARCH_PATH.write_text(
        json.dumps(search_index, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(
        f"Готово: {len(index)} глав, индекс: {INDEX_PATH.relative_to(ROOT)}, "
        f"поиск: {SEARCH_PATH.relative_to(ROOT)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(build())
