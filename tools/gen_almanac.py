#!/usr/bin/env python3
"""Generates the Captain's Almanac's chapters from docs/guide.md (work package ALM5, design.md §3.4).

Run (from the repository root, Python 3 standard library only):

    python3 tools/gen_almanac.py              # writes the chapter intermediate (below) and the illustrations
    python3 tools/gen_almanac.py --no-images  # only the intermediate (fast; what AlmanacGuideTest compares)
    python3 tools/gen_almanac.py --out FILE   # writes the intermediate to FILE instead, no illustrations
    ./gradlew :neoforge:runData               # then: the chapter files and the lang entries

Run it after every change to docs/guide.md (next to tools/gen_guideme.py) and commit the output. AlmanacGuideTest
fails while the committed intermediate differs from a fresh run, or the generated chapters and lang differ from it.

Output:
    common/src/datagen/almanac/guide_chapters.json
        The intermediate: every chapter in the chapter file format of docs/almanac.md ("body"), plus a "lang" map of
        every lang key the chapters use to its English text (with the Almanac's markup). It is no resource and not
        shipped; datagen (almanac/data/AlmanacGuideProvider, ./gradlew :neoforge:runData) reads it, checks it with
        the chapter codecs and writes data/pirates_n_ships/almanac/chapters/<id>.json and the lang entries. So the
        final chapter files and the lang come from datagen, as all our JSON does.
    common/src/main/resources/assets/pirates_n_ships/textures/almanac/<render>.png
        Illustrations: a panel of a render from art/renders (ILLUSTRATIONS), its flat background made transparent,
        trimmed to the model and scaled down to at most MAX_W x MAX_H pixels. Only written without --no-images/--out.

Chapters: one per "## N. Title" section of docs/guide.md, id from CHAPTERS, "order" 10 x N, a bookmark colour and an
icon item per chapter. Section 0 ("The guide book") is the welcome chapter and gets a contents page that links every
other chapter. Pages: the first page opens with the chapter title as a heading and the text before the first "###";
every "### Heading" starts a new page with that heading. One illustration per page at most, near the top
(ILLUSTRATIONS: per chapter, or per chapter and subsection heading).

What converts to what (inline, in every text):
    **bold** -> **bold**          *italic* -> _italic_          `code` -> the plain text
    [text](#anchor)  -> [[pirates_n_ships:<chapter holding the anchor>|text]] (plain text inside its own chapter)
    [text](file.md), [text](http...) -> text
    **Name** of one of our items (or its plural) -> {item:pirates_n_ships:<id>}**Name**, the first time per page
    literal *, \\, {, [[ and word-edge _ are escaped with a backslash (Markup reads them as markup otherwise)
Blocks: a paragraph (lines joined) -> text; "- " lists -> list (at most 64 entries each); "1. " lists -> one text per
entry with its number in bold; a table -> a list, one entry per row: the first cell in bold (with item icons when the
column names items, as in gen_guideme), then the other cells ("What it does" columns plain, others as "header: value");
"---" separators are dropped.
"""
import argparse
import json
import re
import struct
import sys
import zlib
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ next to the tools

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_guideme as guideme  # noqa: E402  (items, sections, slugs and table cells, shared with the GuideME pages)

NS = guideme.NS
ROOT = Path(__file__).resolve().parent.parent
OUT_JSON = ROOT / "common" / "src" / "datagen" / "almanac" / "guide_chapters.json"
RENDERS = ROOT / "art" / "renders"
TEXTURES = ROOT / "common" / "src" / "main" / "resources" / "assets" / NS / "textures" / "almanac"

# Chapter id, icon item and bookmark colour per section number. Colours: muted inks that read on parchment.
CHAPTERS = {
    0: ("welcome", "captains_almanac", "#8b1a1a"),
    1: ("first_ship", "helm", "#7a4a1e"),
    2: ("ships", "hull_patch", "#5b3a29"),
    3: ("sailing", "yard", "#2f5d7c"),
    4: ("crew", "captains_whistle", "#4f6b3a"),
    5: ("flags", "jolly_roger_flag", "#262626"),
    6: ("provisions", "pantry", "#8a5a1f"),
    7: ("trade", "cargo_crate", "#a07a24"),
    8: ("law", "shackles", "#3b4a6b"),
    9: ("combat", "cannon", "#4a4a4a"),
    10: ("blocks", "capstan", "#6e5a3c"),
    11: ("items", "rope", "#7c6a4a"),
    12: ("commands", "minecraft:command_block", "#5a4f6e"),
    13: ("configuration", "minecraft:comparator", "#4f5f5f"),
    14: ("datapacks", "minecraft:knowledge_book", "#6a4f5f"),
    15: ("not_yet", "minecraft:barrier", "#8a7f6a"),
}
WELCOME = 0
CONTENTS_HEADING = "Contents"
CONTENTS_TEXT = "Every chapter has a bookmark on the edge of the book. Or turn to one here:"

# "Show me" buttons: (chapter, subsection heading) -> (Ponder scene id, label). Placed after the first paragraph.
SHOW_ME = {
    ("combat", "Cannons"): (f"{NS}:cannon_loading", "Show me: loading and firing a cannon"),
}

# Illustrations: (chapter, subsection heading or None for the chapter's first page) -> (render under art/renders,
# panel of it as fractions x0, y0, x1, y1). The panel is trimmed to the model, so it only has to exclude the other
# views and the labels of a sheet.
FULL = (0.0, 0.0, 1.0, 1.0)
ILLUSTRATIONS = {
    ("welcome", None): ("pirate_captain.png", (0.0, 0.0, 0.3, 1.0)),
    ("first_ship", None): ("helm.png", FULL),
    ("ships", None): ("nameplate.png", FULL),
    ("ships", "Fighting a leak"): ("bilge_pump.png", FULL),
    ("sailing", None): ("yard.png", FULL),
    ("sailing", "Sail winch"): ("sail_winch.png", FULL),
    ("sailing", "Capstan and anchor"): ("capstan.png", FULL),
    ("sailing", "Ratlines"): ("ratlines.png", FULL),
    ("sailing", "Decor"): ("ships_bell.png", (0.0, 0.0, 0.25, 1.0)),
    ("sailing", "Chart"): ("chart.png", (0.0, 0.0, 0.34, 1.0)),
    ("crew", None): ("crew_member.png", (0.0, 0.0, 0.36, 1.0)),
    ("crew", "Crow's nest"): ("crows_nest.png", (0.0, 0.0, 0.25, 1.0)),
    ("crew", "Pirates, sailors and the navy"): ("mob_pirate.png", (0.0, 0.0, 0.36, 1.0)),
    ("crew", "The kraken"): ("kraken.png", (0.335, 0.1, 0.5, 1.0)),
    ("crew", "Sharks"): ("shark.png", FULL),
    ("flags", None): ("jolly_roger_flag.png", (0.5, 0.0, 1.0, 1.0)),
    ("provisions", None): ("pantry.png", FULL),
    ("provisions", "Water barrel"): ("water_barrel.png", FULL),
    ("provisions", "Sea chest"): ("sea_chest.png", (0.0, 0.0, 0.5, 1.0)),
    ("trade", None): ("cargo_crate.png", FULL),
    ("trade", "Doubloons"): ("doubloon.png", (0.0, 0.0, 0.25, 1.0)),
    ("trade", "Harbor master's desk"): ("harbor_desk.png", FULL),
    ("law", None): ("notice_board.png", (0.0, 0.0, 0.34, 1.0)),
    ("law", "Shackles and prisoners"): ("shackles.png", FULL),
    ("law", "Brig bars and brig door"): ("brig_door.png", FULL),
    ("combat", None): ("cutlass.png", FULL),
    ("combat", "Firearms"): ("pistol.png", FULL),
    ("combat", "Cannons"): ("cannon.png", (0.4, 0.0, 0.67, 1.0)),
    ("combat", "Swivel gun"): ("swivel_gun.png", (0.0, 0.0, 0.34, 1.0)),
    ("combat", "Grappling hook"): ("grappling_hook.png", FULL),
    ("blocks", None): ("figurehead_mermaid.png", FULL),
    ("blocks", "Hats"): ("captains_hat.png", (0.0, 0.0, 0.25, 1.0)),
    ("items", None): ("rum.png", FULL),
}
# Where an illustration sits on its page (unit square): below the heading line, across the page.
ILLUSTRATION_BOX = (0.05, 0.09, 0.9, 0.35)
MAX_W, MAX_H = 256, 160

MAX_LIST = 64          # Page element limits of the chapter format (ListItems, Page)
MAX_ELEMENTS = 64
# Table columns whose cell follows the first one without its header ("Helm: Assembles ...").
PLAIN_COLUMNS = {"what it does", "meaning for the law rules", "today", "effect", "contents", "what it controls",
                 "holds", "good at", "points"}

LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)\s]+)\)")
# Inline tokens in order of precedence: code span, link, bold run without nested markup, lone **, *italic*
INLINE_RE = re.compile(
    r"(?P<code>`[^`]*`)"
    r"|(?P<link>\[(?P<ltext>[^\]]+)\]\((?P<href>[^)\s]+)\))"
    r"|\*\*(?P<bold>[^*`\[\]]+?)\*\*"
    r"|(?P<star2>\*\*)"
    r"|(?<![*\w])\*(?P<ital>[^*\s][^*]*?)\*(?![*\w])")
ORDERED_RE = re.compile(r"^(\d+)\.\s+(.*)$")


# ---------------------------------------------------------------------------------------------------- inline

def escape(text):
    """Literal text for the Almanac's markup (almanac/client/Markup): nothing in it may read as markup."""
    out = []
    n = len(text)
    for i, c in enumerate(text):
        prev = text[i - 1] if i > 0 else " "
        nxt = text[i + 1] if i + 1 < n else " "
        if c in "\\*{":
            out.append("\\" + c)
        elif c == "[" and nxt == "[":
            out.append("\\[")
        elif c == "_" and not (prev.isalnum() and nxt.isalnum()):
            out.append("\\_")
        else:
            out.append(c)
    return "".join(out)


def plain(text):
    """The text with the guide's inline markdown removed (link labels, headings)."""
    text = LINK_RE.sub(lambda m: m.group(1), text)
    return text.replace("`", "").replace("**", "")


class Context:
    """What inline conversion needs: the item names, the anchors, the chapter being written, the icons on its page."""

    def __init__(self, by_name, anchors, chapter):
        self.by_name = by_name
        self.anchors = anchors
        self.chapter = chapter
        self.icons_on_page = set()

    def new_page(self):
        self.icons_on_page = set()

    def item(self, name):
        n = name.strip().lower()
        rid = self.by_name.get(n)
        if rid is None and n.endswith("s"):
            rid = self.by_name.get(n[:-1])
        return rid


def convert_inline(text, ctx):
    """One line or paragraph of guide markdown -> Almanac markup."""
    out, pos = [], 0
    for m in INLINE_RE.finditer(text):
        out.append(escape(text[pos:m.start()]))
        pos = m.end()
        if m.group("code") is not None:
            out.append(escape(m.group("code")[1:-1]))
        elif m.group("link") is not None:
            out.append(convert_link(m.group("ltext"), m.group("href"), ctx))
        elif m.group("bold") is not None:
            inner = m.group("bold")
            rid = ctx.item(inner) if ctx is not None else None
            icon = ""
            if rid is not None and rid not in ctx.icons_on_page:
                ctx.icons_on_page.add(rid)
                icon = "{item:" + rid + "}"
            out.append(icon + "**" + escape(inner) + "**")
        elif m.group("star2") is not None:
            out.append("**")
        else:
            out.append("_" + convert_inline(m.group("ital"), ctx) + "_")
    out.append(escape(text[pos:]))
    return "".join(out)


def convert_link(label, href, ctx):
    label_text = plain(label)
    if href.startswith("#") and ctx is not None:
        target = ctx.anchors.get(href[1:])
        if target is None:
            raise SystemExit(f"docs/guide.md links to #{href[1:]}, which is no heading")
        if target != ctx.chapter:
            safe = label_text.replace("|", "/").replace("]", ")").replace("[", "(")
            return f"[[{NS}:{target}|{safe}]]"
    return escape(label_text)


# ---------------------------------------------------------------------------------------------------- blocks

def split_blocks(lines):
    """Body lines of one page -> [("para", text) | ("bullets", [text]) | ("ordered", [(n, text)]) | ("table", rows)]."""
    blocks, i = [], 0
    while i < len(lines):
        line = lines[i]
        s = line.strip()
        if not s or s == "---":
            i += 1
            continue
        if s.startswith("|"):
            j = i
            while j < len(lines) and lines[j].strip().startswith("|"):
                j += 1
            blocks.append(("table", [l.strip() for l in lines[i:j]]))
            i = j
            continue
        if s.startswith("- ") or ORDERED_RE.match(s):
            ordered = not s.startswith("- ")
            items = []
            while i < len(lines):
                s = lines[i].strip()
                if not s or s == "---":
                    break
                m = ORDERED_RE.match(s) if ordered else None
                if (not ordered and s.startswith("- ")) or m:
                    items.append([int(m.group(1)), m.group(2)] if ordered else [0, s[2:]])
                elif lines[i].startswith((" ", "\t")) and items:
                    items[-1][1] += " " + s
                else:
                    break
                i += 1
            if ordered:
                blocks.append(("ordered", [(n, t) for n, t in items]))
            else:
                blocks.append(("bullets", [t for _, t in items]))
            continue
        para = []
        while i < len(lines):
            s = lines[i].strip()
            if not s or s == "---" or s.startswith(("|", "- ")) or ORDERED_RE.match(s) or s.startswith("### "):
                break
            para.append(s)
            i += 1
        blocks.append(("para", " ".join(para)))
    return blocks


def table_entries(rows, ctx):
    """A GFM table -> one list entry per row (Almanac markup)."""
    header = [h for h in guideme.split_row(rows[0])]
    keys = [plain(h).lower() for h in header]
    resolver = guideme.Resolver(ctx.by_name)
    entries = []
    for row in rows[2:]:
        cells = [c.replace("\\|", "|") for c in guideme.split_row(row)]
        first = cells[0] if cells else ""
        icons = ""
        res = resolver.cell(first) if keys and keys[0] in guideme.ITEM_COLUMNS else None
        if res:
            ids, _ = res
            icons = "".join("{item:" + r + "}" for r in ids)
            for r in ids:
                ctx.icons_on_page.add(r)
        head = icons + "**" + convert_inline(first.replace("**", ""), ctx) + "**"
        rest, notes = [], []
        for k, cell in zip(keys[1:], cells[1:]):
            if not cell.strip():
                continue
            value = convert_inline(cell, ctx)
            res = resolver.cell(cell) if k in guideme.ITEM_COLUMNS else None
            if res:
                value = "".join("{item:" + r + "}" for r in res[0]) + value
            if k in PLAIN_COLUMNS or not k:
                rest.append(value)
            else:
                notes.append(f"{escape(k)}: {value}")
        text = head
        if notes:
            text += " (" + "; ".join(notes) + ")"
        if rest:
            text += ": " + " ".join(rest)
        entries.append(text)
    return entries


# ---------------------------------------------------------------------------------------------------- chapters

class PageBuilder:
    def __init__(self, chapter_id, number, lang):
        self.chapter = chapter_id
        self.number = number
        self.lang = lang
        self.elements = []
        self.counts = {}

    def key(self, kind, text):
        self.counts[kind] = self.counts.get(kind, 0) + 1
        n = self.counts[kind]
        name = kind if (kind == "heading" and n == 1) else f"{kind}{n}"
        k = f"{NS}.almanac.{self.chapter}.p{self.number}.{name}"
        if k in self.lang:
            raise SystemExit(f"duplicate almanac lang key {k}")
        self.lang[k] = text
        return k

    def heading(self, text):
        self.elements.append({"type": "heading", "key": self.key("heading", text)})

    def text(self, text):
        self.elements.append({"type": "text", "key": self.key("text", text)})

    def bullets(self, items):
        for start in range(0, len(items), MAX_LIST):
            self.counts["list"] = self.counts.get("list", 0) + 1
            n = self.counts["list"]
            keys = []
            for j, t in enumerate(items[start:start + MAX_LIST], 1):
                k = f"{NS}.almanac.{self.chapter}.p{self.number}.list{n}_{j}"
                self.lang[k] = t
                keys.append(k)
            self.elements.append({"type": "list", "items": keys})

    def illustration(self, name):
        if any(e["type"] == "illustration" for e in self.elements):
            return
        x, y, w, h = ILLUSTRATION_BOX
        self.elements.append({"type": "illustration", "texture": f"{NS}:textures/almanac/{name}.png",
                              "x": x, "y": y, "w": w, "h": h})

    def show_me(self, scene, label):
        self.elements.append({"type": "show_me", "scene": scene, "label": self.key("show_me", label)})

    def build(self):
        if len(self.elements) > MAX_ELEMENTS:
            raise SystemExit(f"{self.chapter} page {self.number}: {len(self.elements)} elements (max {MAX_ELEMENTS})")
        return {"elements": self.elements}


def illustration_name(render):
    return Path(render).stem


def write_blocks(page, blocks, ctx, show_me=None):
    """Adds the blocks of one page; a show_me (scene, label) goes after the first paragraph."""
    for block in blocks:
        kind, data = block
        if kind == "para":
            page.text(convert_inline(data, ctx))
        elif kind == "bullets":
            page.bullets([convert_inline(t, ctx) for t in data])
        elif kind == "ordered":
            for n, t in data:
                page.text(f"**{n}.** " + convert_inline(t, ctx))
        elif kind == "table":
            page.bullets(table_entries(data, ctx))
        if show_me is not None and kind == "para":
            page.show_me(*show_me)
            show_me = None
    if show_me is not None:
        page.show_me(*show_me)


def split_pages(body):
    """Section body -> [(heading or None, lines)]: the text before the first "###", then one entry per "###"."""
    pages = [(None, [])]
    for line in body:
        m = guideme.SUBHEADING_RE.match(line)
        if m:
            pages.append((m.group(1), []))
        else:
            pages[-1][1].append(line)
    return pages


def build_chapters(guide_text, by_name):
    sections = sorted(guideme.parse_sections(guide_text), key=lambda s: s[0])
    if not sections:
        raise SystemExit("no '## N. Title' sections in docs/guide.md")
    numbers = [n for n, _, _ in sections]
    if sorted(numbers) != list(CHAPTERS):
        raise SystemExit(f"docs/guide.md sections {numbers} do not match the chapters {sorted(CHAPTERS)} "
                         f"(add the new section to CHAPTERS in tools/gen_almanac.py)")

    anchors = {}
    titles = {}
    for number, title, body in sections:
        cid = CHAPTERS[number][0]
        titles[cid] = title
        anchors[guideme.github_slug(f"{number}. {title}")] = cid
        for line in body:
            m = guideme.SUBHEADING_RE.match(line)
            if m:
                anchors.setdefault(guideme.github_slug(m.group(1)), cid)

    lang, chapters = {}, []
    used_illustrations = set()
    for number, title, body in sections:
        cid, icon, color = CHAPTERS[number]
        ctx = Context(by_name, anchors, cid)
        title_key = f"{NS}.almanac.{cid}.title"
        lang[title_key] = escape(title)
        pages = []
        raw_pages = split_pages(body)
        intro_heading, intro_lines = raw_pages[0]
        intro_blocks = split_blocks(intro_lines)
        rest = raw_pages[1:]

        page = PageBuilder(cid, 1, lang)
        ctx.new_page()
        page.heading(escape(title))
        if (cid, None) in ILLUSTRATIONS:
            page.illustration(illustration_name(ILLUSTRATIONS[(cid, None)][0]))
            used_illustrations.add((cid, None))
        write_blocks(page, intro_blocks, ctx)
        if not intro_blocks and rest:
            # Nothing before the first subsection: it shares the first page with the chapter title
            heading, lines = rest[0]
            rest = rest[1:]
            add_subsection(page, heading, lines, ctx, used_illustrations)
        pages.append(page.build())

        for heading, lines in rest:
            page = PageBuilder(cid, len(pages) + 1, lang)
            ctx.new_page()
            add_subsection(page, heading, lines, ctx, used_illustrations)
            pages.append(page.build())

        if number == WELCOME:
            page = PageBuilder(cid, len(pages) + 1, lang)
            page.heading(CONTENTS_HEADING)
            page.text(CONTENTS_TEXT)
            page.bullets([f"[[{NS}:{CHAPTERS[n][0]}]]" for n, _, _ in sections if n != WELCOME])
            pages.append(page.build())

        chapters.append({
            "id": f"{NS}:{cid}",
            "section": number,
            "body": {
                "title": title_key,
                "order": number * 10,
                "bookmark_color": color,
                "icon": guideme.full_id(icon),
                "pages": pages,
            },
        })

    missing = set(ILLUSTRATIONS) - used_illustrations
    if missing:
        raise SystemExit(f"illustrations for headings that docs/guide.md does not have: {sorted(missing, key=str)}")
    missing_show = set(SHOW_ME) - {k for k in SHOW_ME if any(
        e.get("type") == "show_me" and e.get("scene") == SHOW_ME[k][0]
        for c in chapters if c["id"] == f"{NS}:{k[0]}" for p in c["body"]["pages"] for e in p["elements"])}
    if missing_show:
        raise SystemExit(f"'Show me' for headings that docs/guide.md does not have: {sorted(missing_show)}")
    return chapters, lang


def add_subsection(page, heading, lines, ctx, used_illustrations):
    page.heading(convert_inline(heading, None))
    key = (ctx.chapter, heading)
    if key in ILLUSTRATIONS:
        page.illustration(illustration_name(ILLUSTRATIONS[key][0]))
        used_illustrations.add(key)
    write_blocks(page, split_blocks(lines), ctx, SHOW_ME.get(key))


def render_json(chapters, lang):
    doc = {
        "_comment": "Generated by tools/gen_almanac.py from docs/guide.md; do not edit. "
                    "./gradlew :neoforge:runData turns it into the Almanac's chapter files and lang.",
        "chapters": chapters,
        "lang": lang,
    }
    return json.dumps(doc, indent=1, ensure_ascii=False) + "\n"


# ---------------------------------------------------------------------------------------------------- images

def read_png(path):
    """(width, height, RGBA rows as bytearrays) of an 8-bit RGB or RGBA, non-interlaced PNG."""
    data = Path(path).read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path}: no PNG")
    pos, idat, w = 8, [], None
    while pos < len(data):
        length, ctype = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            w, h, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            if depth != 8 or color not in (2, 6) or interlace:
                raise SystemExit(f"{path}: only 8-bit RGB/RGBA non-interlaced PNGs are supported")
            bpp = 4 if color == 6 else 3
        elif ctype == b"IDAT":
            idat.append(chunk)
        elif ctype == b"IEND":
            break
    raw = zlib.decompress(b"".join(idat))
    stride = w * bpp
    rows, prev = [], bytearray(stride)
    p = 0
    for _ in range(h):
        f = raw[p]
        line = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        rows.append(line)
        prev = line
    if bpp == 3:
        rgba = []
        for line in rows:
            out = bytearray(w * 4)
            out[0::4], out[1::4], out[2::4] = line[0::3], line[1::3], line[2::3]
            out[3::4] = b"\xff" * w
            rgba.append(out)
        rows = rgba
    return w, h, rows


def write_png(path, w, h, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_bytes(png)


def make_illustration(src, panel):
    """The panel of a render: background keyed out (the panel's corner colour), trimmed, scaled to fit MAX_W x MAX_H."""
    w, h, rows = read_png(src)
    x0, y0 = int(panel[0] * w), int(panel[1] * h)
    x1, y1 = max(x0 + 1, int(panel[2] * w)), max(y0 + 1, int(panel[3] * h))
    bg = tuple(rows[y0][x0 * 4:x0 * 4 + 4])
    keyed = bg[3] == 255

    def is_bg(px):
        if px[3] < 16:
            return True
        return keyed and abs(px[0] - bg[0]) <= 10 and abs(px[1] - bg[1]) <= 10 and abs(px[2] - bg[2]) <= 10

    cropped = []
    minx, miny, maxx, maxy = x1, y1, -1, -1
    for y in range(y0, y1):
        line = bytearray(rows[y][x0 * 4:x1 * 4])
        for x in range(x1 - x0):
            px = line[x * 4:x * 4 + 4]
            if is_bg(px):
                line[x * 4:x * 4 + 4] = b"\x00\x00\x00\x00"
            else:
                minx, maxx = min(minx, x), max(maxx, x)
                miny, maxy = min(miny, y - y0), max(maxy, y - y0)
        cropped.append(line)
    if maxx < 0:
        raise SystemExit(f"{src}: panel {panel} is empty")
    pad = 2
    minx, miny = max(0, minx - pad), max(0, miny - pad)
    maxx, maxy = min(x1 - x0 - 1, maxx + pad), min(y1 - y0 - 1, maxy + pad)
    cw, ch = maxx - minx + 1, maxy - miny + 1
    scale = min(1.0, MAX_W / cw, MAX_H / ch)
    ow, oh = max(1, round(cw * scale)), max(1, round(ch * scale))
    out = []
    for oy in range(oh):
        sy0 = miny + oy * ch // oh
        sy1 = max(sy0 + 1, miny + (oy + 1) * ch // oh)
        line = bytearray(ow * 4)
        for ox in range(ow):
            sx0 = minx + ox * cw // ow
            sx1 = max(sx0 + 1, minx + (ox + 1) * cw // ow)
            r = g = b = a = n = 0
            for sy in range(sy0, sy1):
                src_line = cropped[sy]
                for sx in range(sx0, sx1):
                    i = sx * 4
                    pa = src_line[i + 3]
                    r += src_line[i] * pa
                    g += src_line[i + 1] * pa
                    b += src_line[i + 2] * pa
                    a += pa
                    n += 1
            if a:
                line[ox * 4:ox * 4 + 4] = bytes((r // a, g // a, b // a, a // n))
        out.append(line)
    return ow, oh, out


def write_illustrations():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    wanted = {}
    for render, panel in ILLUSTRATIONS.values():
        wanted[illustration_name(render)] = (render, panel)
    for stale in TEXTURES.glob("*.png"):
        if stale.stem not in wanted:
            stale.unlink()
    total = 0
    for name in sorted(wanted):
        render, panel = wanted[name]
        w, h, rows = make_illustration(RENDERS / render, panel)
        target = TEXTURES / f"{name}.png"
        write_png(target, w, h, rows)
        total += target.stat().st_size
        print(f"  {name}.png {w}x{h} {target.stat().st_size} bytes")
    print(f"{len(wanted)} illustrations, {total} bytes")


# ---------------------------------------------------------------------------------------------------- main

def generate():
    by_name, _, _ = guideme.load_items()
    return build_chapters(guideme.GUIDE_MD.read_text(encoding="utf-8"), by_name)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", type=Path, default=None, help="write the intermediate here (no illustrations)")
    ap.add_argument("--no-images", action="store_true", help="do not regenerate the illustrations")
    args = ap.parse_args()
    chapters, lang = generate()
    out = args.out or OUT_JSON
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render_json(chapters, lang), encoding="utf-8", newline="\n")
    pages = sum(len(c["body"]["pages"]) for c in chapters)
    print(f"wrote {len(chapters)} chapters, {pages} pages, {len(lang)} lang entries to {out}")
    if args.out is None and not args.no_images:
        write_illustrations()


if __name__ == "__main__":
    main()
