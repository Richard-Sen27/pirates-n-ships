#!/usr/bin/env python3
"""Generates the Captain's Almanac's chapters from docs/almanac/ (ALM5, ALM6; design.md §3.4, docs/almanac.md).

Run (from the repository root, Python 3 standard library only):

    python3 tools/gen_almanac.py              # writes the chapter intermediate (below) and the illustrations
    python3 tools/gen_almanac.py --no-images  # only the intermediate (fast; what AlmanacGuideTest compares)
    python3 tools/gen_almanac.py --out FILE   # writes the intermediate to FILE instead, no illustrations
    ./gradlew :neoforge:runData               # then: the chapter files and the lang entries

Run it after every change to docs/almanac/ and commit the output. AlmanacGuideTest fails while the committed
intermediate differs from a fresh run, or the generated chapters and lang differ from it.

Source: docs/almanac/<nn>-<chapter>.md, one file per chapter (bookmark), written for the player in the Almanac's own
voice. docs/guide.md is the testing and reference guide and is not read here. Each file:

    ---
    id: sailing                         chapter id (pirates_n_ships:<id>); the file name is <nn>-<id with - for _>.md
    title: Sailing                      the chapter title (a heading on its first page and the bookmark's tooltip)
    order: 40                           position in the book; strictly increasing in file name order
    icon: yard                          the bookmark's item (no namespace = pirates_n_ships)
    color: #2f5d7c                      the bookmark's colour
    illustrations:                      every illustration the chapter uses, with its box on the page (unit square)
      - yard: 0.05 0.09 0.9 0.35
    ---
    Text before the first "##" goes on the first page, under the chapter title.

    ## A subsection                     every "##" starts a new page with that heading
    illustration: yard                  places a declared illustration on this page (at its box)
    show_me: sails_and_winch | Show me: rigging a sail     a "Show me" button here (scene id, optional label)
    contents:                           a list linking every other chapter, in book order (the welcome chapter)
    {spotlight:helm}                    a framed item at twice the size with its name under it (ALM8; a line of its own)
    {recipe:helm}                       the crafting panel of one of our recipes (ALM8; a line of its own; the id must
                                        exist in the generated recipes, common/src/generated/resources/data/<ns>/recipe)
    {spotlight3d:block:capstan}         a block, item or mob turning in a framed card with its name under it (ALM10; a
    {spotlight3d:entity:shark|The shark}  line of its own): <kind>:<id>[|caption], kind block, item or entity, the id
                                        one of ours (lang block./entity. keys, our items) or a vanilla id in VANILLA;
                                        the caption is plain text and replaces the registry name under the card

Body blocks: a paragraph (lines joined) -> text; "- " lines -> one list (indented lines continue an entry; at most
64 entries); "1. " lines -> one text per entry with its number in bold; "> " paragraphs -> a tip (TIP_COLOR ink);
">! " paragraphs -> a warning (WARNING_COLOR ink). The text is the Almanac's markup as the screen reads it (**bold**,
_italic_, {#rrggbb}...{/}, {item:ns:id}, [[ns:chapter|text]], backslash escapes), checked here: balanced bold and
colours, closed links to chapters that exist, our item ids that exist; no tables, no markdown italics (*x*), no code
spans, no raw markdown links.

Output:
    common/src/datagen/almanac/chapters.json
        The intermediate: every chapter in the chapter file format of docs/almanac.md ("body"), plus a "lang" map of
        every lang key the chapters use to its English text. It is no resource and not shipped; datagen
        (almanac/data/AlmanacChapterProvider, ./gradlew :neoforge:runData) checks it with the chapter codecs and
        writes data/pirates_n_ships/almanac/chapters/<id>.json and the lang entries.
    common/src/main/resources/assets/pirates_n_ships/textures/almanac/<name>.png
        Illustrations: a panel of a render from art/renders (ILLUSTRATIONS), its flat background made transparent,
        trimmed to the model and scaled down to at most MAX_W x MAX_H pixels. Only written without --no-images/--out.
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
import almanac_lib as lib  # noqa: E402  (our item ids from the lang and item models)

NS = lib.NS
ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs" / "almanac"
OUT_JSON = ROOT / "common" / "src" / "datagen" / "almanac" / "chapters.json"
RENDERS = ROOT / "art" / "renders"
TEXTURES = ROOT / "common" / "src" / "main" / "resources" / "assets" / NS / "textures" / "almanac"

TIP_COLOR = "#2e6a24"       # a tip: the green ink of the screens (GuiKit.INK_GREEN)
WARNING_COLOR = "#9a1c14"   # a warning: the red ink of the screens (GuiKit.INK_RED)
DEFAULT_SHOW_ME_LABEL = "Show me"

# Illustrations: texture name (textures/almanac/<name>.png) -> (render under art/renders, panel of it as fractions
# x0, y0, x1, y1). The panel is trimmed to the model, so it only has to exclude the other views and labels of a sheet.
# A chapter declares the ones it shows in its front matter; every entry here must be shown somewhere.
FULL = (0.0, 0.0, 1.0, 1.0)
ILLUSTRATIONS = {
    "anchor": ("anchor.png", FULL),
    "bilge_pump": ("bilge_pump.png", FULL),
    "captains_almanac": ("captains_almanac.png", (0.79, 0.0, 1.0, 1.0)),
    "cargo_barrel": ("cargo_barrel.png", FULL),
    "hammock": ("hammock.png", (0.0, 0.0, 0.31, 1.0)),
    "map_tile": ("map_tile.png", (0.37, 0.0, 0.7, 1.0)),
    "mob_navy_officer": ("mob_navy_officer.png", (0.0, 0.0, 0.3, 1.0)),
    "ship_commission": ("ship_commission.png", (0.0, 0.0, 0.22, 1.0)),
    "shipwright_toolkit": ("shipwright_toolkit.png", (0.0, 0.0, 0.24, 1.0)),
    "treasure_map": ("treasure_map.png", (0.0, 0.0, 0.18, 1.0)),
    "brig_door": ("brig_door.png", FULL),
    "cannon": ("cannon.png", (0.4, 0.0, 0.67, 1.0)),
    "capstan": ("capstan.png", FULL),
    "captains_hat": ("captains_hat.png", (0.0, 0.0, 0.25, 1.0)),
    "cargo_crate": ("cargo_crate.png", FULL),
    "chart": ("chart.png", (0.0, 0.0, 0.34, 1.0)),
    "crew_member": ("crew_member.png", (0.0, 0.0, 0.36, 1.0)),
    "crows_nest": ("crows_nest.png", (0.0, 0.0, 0.25, 1.0)),
    "cutlass": ("cutlass.png", FULL),
    "doubloon": ("doubloon.png", (0.0, 0.0, 0.25, 1.0)),
    "figurehead_mermaid": ("figurehead_mermaid.png", FULL),
    "grappling_hook": ("grappling_hook.png", FULL),
    "harbor_desk": ("harbor_desk.png", FULL),
    "helm": ("helm.png", FULL),
    "jolly_roger_flag": ("jolly_roger_flag.png", (0.5, 0.0, 1.0, 1.0)),
    "kraken": ("kraken.png", (0.335, 0.1, 0.5, 1.0)),
    "mob_pirate": ("mob_pirate.png", (0.0, 0.0, 0.36, 1.0)),
    "nameplate": ("name_board_row.png", (0.15, 0.2, 0.8, 0.85)),
    "notice_board": ("notice_board.png", (0.0, 0.0, 0.34, 1.0)),
    "pantry": ("pantry.png", FULL),
    "pirate_captain": ("pirate_captain.png", (0.0, 0.0, 0.3, 1.0)),
    "pistol": ("pistol.png", FULL),
    "ratlines": ("ratlines.png", FULL),
    "rum": ("rum.png", FULL),
    "sail_winch": ("sail_winch.png", FULL),
    "sea_chest": ("sea_chest.png", (0.0, 0.0, 0.5, 1.0)),
    "shackles": ("shackles.png", FULL),
    "shark": ("shark.png", FULL),
    "ships_bell": ("ships_bell.png", (0.0, 0.0, 0.25, 1.0)),
    "swivel_gun": ("swivel_gun.png", (0.0, 0.0, 0.34, 1.0)),
    "water_barrel": ("water_barrel.png", FULL),
    "yard": ("yard.png", FULL),
}
MAX_W, MAX_H = 256, 160

# ALM10: the vanilla ids a 3D spotlight may show (the generator has no vanilla registry; AlmanacGuideTest checks these
# against the game's). Add one here before using it.
VANILLA = {
    "block": {"minecraft:barrel", "minecraft:bell", "minecraft:chest", "minecraft:crafting_table", "minecraft:lantern",
              "minecraft:lectern", "minecraft:oak_door", "minecraft:oak_fence_gate", "minecraft:oak_planks"},
    "item": {"minecraft:bow", "minecraft:clock", "minecraft:compass", "minecraft:crossbow", "minecraft:filled_map",
             "minecraft:gunpowder", "minecraft:iron_sword", "minecraft:shield", "minecraft:spyglass",
             "minecraft:trident"},
    "entity": {"minecraft:axolotl", "minecraft:cod", "minecraft:dolphin", "minecraft:drowned", "minecraft:guardian",
               "minecraft:parrot", "minecraft:pillager", "minecraft:pufferfish", "minecraft:salmon", "minecraft:squid",
               "minecraft:tropical_fish", "minecraft:turtle", "minecraft:villager", "minecraft:wandering_trader"},
}
SPOTLIGHT_3D_KINDS = ("block", "item", "entity")

MAX_LIST = 64          # Page element limits of the chapter format (ListItems, Page)
MAX_ELEMENTS = 64
MAX_PAGES = 256

FILE_RE = re.compile(r"^(\d+)-([a-z0-9-]+)\.md$")
ID_RE = re.compile(r"^[a-z0-9_]+$")
COLOR_RE = re.compile(r"^#[0-9a-fA-F]{6}$")
ORDERED_RE = re.compile(r"^(\d+)\.\s+(.*)$")
DIRECTIVE_RE = re.compile(r"^(illustration|show_me|contents):\s*(.*)$")
BLOCK_TOKEN_RE = re.compile(r"^\{(recipe|spotlight):([^}]*)}$")   # ALM8: a recipe panel or an item spotlight
INLINE_BLOCK_RE = re.compile(r"\{(recipe|spotlight|spotlight3d):")
SPOTLIGHT_3D_RE = re.compile(r"^\{spotlight3d:([^}]*)}$")     # ALM10: a block, item or mob turning in a card
HEADING_RE = re.compile(r"^##\s+(.+?)\s*$")
ITEM_RE = re.compile(r"\{item:([^}]*)}")
LINK_RE = re.compile(r"\[\[([^\]|]*)(?:\|([^\]]*))?]]")
COLOR_TOKEN_RE = re.compile(r"\{(#[0-9a-fA-F]{6}|/)}")
RESOURCE_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_/.-]+$")


class SourceError(SystemExit):
    """A problem in docs/almanac/, naming the file and line."""

    def __init__(self, where, message):
        super().__init__(f"{where}: {message}")


# ---------------------------------------------------------------------------------------------------- front matter

def parse_front_matter(lines, where):
    """The "---" block at the start of a file -> (dict, index of the first body line).

    Values are strings; a key with no value followed by "  - entry" lines is a list of strings. Quotes around a value
    are removed. Standard library only, so this is a small subset of YAML, not YAML.
    """
    if not lines or lines[0].strip() != "---":
        raise SourceError(where, "no front matter (the file must start with a '---' line)")
    data, current = {}, None
    for i in range(1, len(lines)):
        raw = lines[i]
        s = raw.strip()
        if s == "---":
            return data, i + 1
        if not s or s.startswith("#"):
            continue
        if s.startswith("- "):
            if current is None or not isinstance(data[current], list):
                raise SourceError(f"{where}:{i + 1}", "a list entry without a list key above it")
            data[current].append(unquote(s[2:].strip()))
            continue
        key, sep, value = s.partition(":")
        key = key.strip()
        if not sep or not ID_RE.match(key):
            raise SourceError(f"{where}:{i + 1}", f"expected 'key: value', got {s!r}")
        if key in data:
            raise SourceError(f"{where}:{i + 1}", f"duplicate key {key!r}")
        value = value.strip()
        data[key] = unquote(value) if value else []
        current = key
    raise SourceError(where, "the front matter has no closing '---' line")


def unquote(value):
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def parse_illustrations(entries, where):
    """["name: x y w h", ...] -> {name: (x, y, w, h)} (unit square of the page)."""
    out = {}
    for entry in entries:
        name, sep, box = entry.partition(":")
        name = name.strip()
        try:
            x, y, w, h = (float(v) for v in box.split())
        except ValueError:
            raise SourceError(where, f"illustration {entry!r}: expected 'name: x y w h'") from None
        if not sep or not ID_RE.match(name):
            raise SourceError(where, f"illustration {entry!r}: expected 'name: x y w h'")
        if name not in ILLUSTRATIONS:
            raise SourceError(where, f"illustration {name!r} is not in ILLUSTRATIONS (tools/gen_almanac.py)")
        if w <= 0 or h <= 0 or x < 0 or y < 0 or x + w > 1 or y + h > 1:
            raise SourceError(where, f"illustration {name!r}: the box {x} {y} {w} {h} leaves the page")
        if name in out:
            raise SourceError(where, f"illustration {name!r} declared twice")
        out[name] = (x, y, w, h)
    return out


def full_id(item):
    return item if ":" in item else f"{NS}:{item}"


# ---------------------------------------------------------------------------------------------------- markup

def check_markup(text, where, chapter_ids, items):
    """Raises SourceError when the text is not clean Almanac markup (see the module doc). Returns the text."""
    # Strip escapes first: an escaped character is never markup.
    bare = re.sub(r"\\.", "", text)
    if "\\" in bare:
        raise SourceError(where, "a lone backslash at the end of the text")
    if "`" in bare:
        raise SourceError(where, "a code span (`...`) has no place in the Almanac; write the plain words")
    if re.search(r"(?<!\])\]\([^)]*\)", bare):
        raise SourceError(where, "a markdown link [text](...); link chapters with [[pirates_n_ships:<id>|text]]")
    for m in LINK_RE.finditer(bare):
        target = m.group(1).strip()
        rid = target if ":" in target else f"{NS}:{target}"
        if rid not in chapter_ids:
            raise SourceError(where, f"link to {target!r}, which is no chapter of the Almanac")
        if m.group(2) is not None and not m.group(2).strip():
            raise SourceError(where, f"link to {target!r} with an empty text")
    rest = LINK_RE.sub("", bare)
    if "[[" in rest or "]]" in rest:
        raise SourceError(where, "an unclosed or broken [[link]]")
    for m in ITEM_RE.finditer(bare):
        rid = m.group(1)
        if ":" not in rid or not RESOURCE_RE.match(rid):
            raise SourceError(where, f"{{item:{rid}}}: write the full id (pirates_n_ships:helm, minecraft:gunpowder)")
        if rid.startswith(NS + ":") and rid not in items:
            raise SourceError(where, f"{{item:{rid}}} is no item of ours")
    depth = 0
    for m in COLOR_TOKEN_RE.finditer(bare):
        depth += -1 if m.group(1) == "/" else 1
        if depth < 0:
            raise SourceError(where, "a {/} without a colour to close")
    if depth:
        raise SourceError(where, "a {#rrggbb} colour that is never closed with {/}")
    if INLINE_BLOCK_RE.search(bare):
        raise SourceError(where, "{recipe:...}, {spotlight:...} and {spotlight3d:...} are blocks: put them on a line "
                                 "of their own")
    other = re.sub(r"\{(#[0-9a-fA-F]{6}|/|item:[^}]*)}", "", bare)
    if "{" in other or "}" in other:
        raise SourceError(where, "a brace that is no markup; escape a literal one as \\{")
    if bare.count("**") % 2:
        raise SourceError(where, "an unclosed **bold**")
    if "*" in bare.replace("**", ""):
        raise SourceError(where, "a single * (italics are _like this_; a literal star is \\*)")
    return text


# ---------------------------------------------------------------------------------------------------- body

def split_pages(lines, first_line):
    """Body lines -> [(heading or None, [(line number, text)])]: the text before the first "##", then one per "##"."""
    pages = [(None, [])]
    for n, line in enumerate(lines, first_line):
        m = HEADING_RE.match(line)
        if m:
            pages.append((m.group(1), []))
        elif line.startswith("#"):
            raise SourceError(f"line {n}", f"only '##' headings start a page: {line.strip()!r}")
        else:
            pages[-1][1].append((n, line))
    return pages


def split_blocks(numbered):
    """[(line number, text)] of one page -> [(kind, data, line number)].

    Kinds: "para" (text), "tip" (text), "warning" (text), "bullets" ([text]), "ordered" ([(n, text)]),
    "illustration" (name), "show_me" ((scene, label)), "contents" (None), "recipe" (full id), "spotlight" (full id),
    "spotlight3d" ((kind, full id, caption or None)).
    """
    blocks, i = [], 0
    while i < len(numbered):
        n, line = numbered[i]
        s = line.strip()
        if not s:
            i += 1
            continue
        if s.startswith("|"):
            raise SourceError(f"line {n}", "tables do not fit the Almanac's pages; write a list")
        b3 = SPOTLIGHT_3D_RE.match(s)
        if b3:
            blocks.append(("spotlight3d", parse_spotlight_3d(b3.group(1), f"line {n}"), n))
            i += 1
            continue
        b = BLOCK_TOKEN_RE.match(s)
        if b:
            value = b.group(2).strip()
            if not value or not RESOURCE_RE.match(full_id(value)):
                raise SourceError(f"line {n}", f"{{{b.group(1)}:{value}}}: no valid id")
            blocks.append((b.group(1), full_id(value), n))
            i += 1
            continue
        d = DIRECTIVE_RE.match(s)
        if d:
            kind, value = d.group(1), d.group(2).strip()
            if kind == "illustration":
                blocks.append(("illustration", value, n))
            elif kind == "show_me":
                scene, _, label = value.partition("|")
                scene, label = scene.strip(), label.strip() or DEFAULT_SHOW_ME_LABEL
                if not scene:
                    raise SourceError(f"line {n}", "show_me without a scene id")
                blocks.append(("show_me", (full_id(scene), label), n))
            else:
                if value:
                    raise SourceError(f"line {n}", "contents: takes no value")
                blocks.append(("contents", None, n))
            i += 1
            continue
        if s.startswith("- ") or ORDERED_RE.match(s):
            ordered = not s.startswith("- ")
            items = []
            while i < len(numbered):
                _, raw = numbered[i]
                t = raw.strip()
                if not t:
                    break
                m = ORDERED_RE.match(t) if ordered else None
                if (not ordered and t.startswith("- ")) or m:
                    items.append([int(m.group(1)), m.group(2)] if ordered else [0, t[2:]])
                elif raw.startswith((" ", "\t")) and items:
                    items[-1][1] += " " + t
                else:
                    break
                i += 1
            if ordered:
                blocks.append(("ordered", [(k, t) for k, t in items], n))
            else:
                blocks.append(("bullets", [t for _, t in items], n))
            continue
        kind = "para"
        if s.startswith(">!"):
            kind = "warning"
        elif s.startswith(">"):
            kind = "tip"
        para = []
        while i < len(numbered):
            _, raw = numbered[i]
            t = raw.strip()
            if (not t or t.startswith(("|", "- ")) or ORDERED_RE.match(t) or DIRECTIVE_RE.match(t)
                    or BLOCK_TOKEN_RE.match(t) or SPOTLIGHT_3D_RE.match(t)):
                break
            if kind != "para":
                if not t.startswith(">"):
                    break
                t = t[2:].strip() if t.startswith(">!") else t[1:].strip()
            elif t.startswith(">"):
                break
            para.append(t)
            i += 1
        blocks.append((kind, " ".join(para), n))
    return blocks


def parse_spotlight_3d(value, where):
    """"<kind>:<id>[|caption]" of a {spotlight3d:...} line -> (kind, full id, caption or None). The id is checked
    against the registries later (write_page)."""
    body, bar, caption = value.partition("|")
    kind, _, rid = body.strip().partition(":")
    kind, rid = kind.strip(), rid.strip()
    if kind not in SPOTLIGHT_3D_KINDS:
        raise SourceError(where, f"{{spotlight3d:{value}}}: the kind is block, item or entity, not {kind!r}")
    if not rid or not RESOURCE_RE.match(full_id(rid)):
        raise SourceError(where, f"{{spotlight3d:{value}}}: no valid id")
    caption = caption.strip()
    if bar and not caption:
        raise SourceError(where, f"{{spotlight3d:{value}}}: an empty caption after '|'")
    if bar and re.search(r"[{}\[\]*_\\`]", caption):
        raise SourceError(where, f"{{spotlight3d:{value}}}: the caption is plain text (no markup)")
    return kind, full_id(rid), caption if bar else None


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

    def text(self, text, color=None):
        element = {"type": "text", "key": self.key("text", text)}
        if color is not None:
            element["color"] = color
        self.elements.append(element)

    def bullets(self, items):
        if len(items) > MAX_LIST:
            raise SystemExit(f"{self.chapter} page {self.number}: a list of {len(items)} entries (max {MAX_LIST})")
        self.counts["list"] = self.counts.get("list", 0) + 1
        n = self.counts["list"]
        keys = []
        for j, t in enumerate(items, 1):
            k = f"{NS}.almanac.{self.chapter}.p{self.number}.list{n}_{j}"
            self.lang[k] = t
            keys.append(k)
        self.elements.append({"type": "list", "items": keys})

    def illustration(self, name, box):
        x, y, w, h = box
        self.elements.append({"type": "illustration", "texture": f"{NS}:textures/almanac/{name}.png",
                              "x": x, "y": y, "w": w, "h": h})

    def show_me(self, scene, label):
        self.elements.append({"type": "show_me", "scene": scene, "label": self.key("show_me", label)})

    def recipe(self, recipe):
        self.elements.append({"type": "recipe", "recipe": recipe})

    def spotlight(self, item):
        self.elements.append({"type": "spotlight", "item": item})

    def spotlight_3d(self, kind, rid, caption):
        element = {"type": "spotlight3d", "subject": kind, "id": rid}
        if caption is not None:
            element["caption"] = self.key("spotlight3d", caption)
        self.elements.append(element)

    def build(self):
        if len(self.elements) > MAX_ELEMENTS:
            raise SystemExit(f"{self.chapter} page {self.number}: {len(self.elements)} elements (max {MAX_ELEMENTS})")
        return {"elements": self.elements}


# ---------------------------------------------------------------------------------------------------- chapters

class Source:
    """One parsed file of docs/almanac/."""

    def __init__(self, name, text):
        self.name = name
        m = FILE_RE.match(name)
        if not m:
            raise SourceError(name, "chapter files are named <nn>-<chapter>.md")
        lines = text.split("\n")
        meta, body_start = parse_front_matter(lines, name)
        for key in ("id", "title", "order", "icon", "color"):
            if key not in meta or meta[key] == []:
                raise SourceError(name, f"the front matter has no {key!r}")
        unknown = set(meta) - {"id", "title", "order", "icon", "color", "illustrations"}
        if unknown:
            raise SourceError(name, f"unknown front matter keys {sorted(unknown)}")
        self.id = meta["id"]
        if not ID_RE.match(self.id):
            raise SourceError(name, f"bad chapter id {self.id!r}")
        if m.group(2).replace("-", "_") != self.id:
            raise SourceError(name, f"the file name does not match the id {self.id!r}")
        self.title = meta["title"]
        try:
            self.order = int(meta["order"])
        except ValueError:
            raise SourceError(name, f"order {meta['order']!r} is no number") from None
        self.icon = full_id(meta["icon"])
        if not COLOR_RE.match(meta["color"]):
            raise SourceError(name, f"color {meta['color']!r} is no #rrggbb")
        self.color = meta["color"].lower()
        ill = meta.get("illustrations", [])
        self.illustrations = parse_illustrations(ill if isinstance(ill, list) else [ill], name)
        try:
            self.pages = split_pages(lines[body_start:], body_start + 1)
        except SourceError as e:
            raise SourceError(name, str(e)) from None


def read_sources(directory=None):
    directory = Path(directory) if directory is not None else SOURCE
    files = sorted(p for p in directory.glob("*.md"))
    if not files:
        raise SystemExit(f"no chapters in {directory}")
    return [Source(p.name, p.read_text(encoding="utf-8")) for p in files]


def load_recipes():
    """Our recipe ids ("pirates_n_ships:helm") from the generated data (run ./gradlew :neoforge:runData first)."""
    directory = lib.ROOT / "common" / "src" / "generated" / "resources" / "data" / NS / "recipe"
    return {f"{NS}:{p.relative_to(directory).with_suffix('').as_posix()}" for p in directory.rglob("*.json")}


def load_registry(items):
    """{kind: our ids of that kind} for the 3D spotlights: blocks and entity types from the lang, the items given."""
    blocks, entities = lib.load_registry()
    return {"block": blocks, "item": set(items), "entity": entities}


def build_chapters(sources, items, recipes=None, registry=None):
    """Parsed sources (file order), our item ids, recipe ids and {kind: ids} of our blocks, items and entity types ->
    (chapters for the intermediate, lang map, shown illustrations). Recipes default to the generated ones
    (load_recipes), the registry to load_registry."""
    if recipes is None:
        recipes = load_recipes()
    if registry is None:
        registry = load_registry(items)
    ids = [f"{NS}:{s.id}" for s in sources]
    if len(set(ids)) != len(ids):
        raise SystemExit(f"duplicate chapter ids in docs/almanac: {ids}")
    for a, b in zip(sources, sources[1:]):
        if b.order <= a.order:
            raise SourceError(b.name, f"order {b.order} does not come after {a.name}'s {a.order}")
    chapter_ids = set(ids)
    if not any(s.id == "welcome" for s in sources) or sources[0].id != "welcome":
        raise SystemExit("docs/almanac must start with the welcome chapter (id: welcome)")

    lang, chapters, shown = {}, [], set()
    for src in sources:
        if src.icon.startswith(NS + ":") and src.icon not in items:
            raise SourceError(src.name, f"icon {src.icon} is no item of ours")
        title_key = f"{NS}.almanac.{src.id}.title"
        lang[title_key] = check_markup(src.title, src.name, chapter_ids, items)
        placed = set()
        pages = []
        raw = list(src.pages)
        intro = raw[0][1]
        rest = raw[1:]
        page = PageBuilder(src.id, 1, lang)
        page.heading(lang[title_key])
        has_intro = any(line.strip() for _, line in intro)
        write_page(page, intro, src, chapter_ids, items, ids, placed, recipes, registry)
        if not has_intro and rest:
            # Nothing before the first subsection: it shares the first page with the chapter title
            heading, lines = rest[0]
            rest = rest[1:]
            page.heading(check_markup(heading, src.name, chapter_ids, items))
            write_page(page, lines, src, chapter_ids, items, ids, placed, recipes, registry)
        pages.append(page.build())
        for heading, lines in rest:
            page = PageBuilder(src.id, len(pages) + 1, lang)
            page.heading(check_markup(heading, src.name, chapter_ids, items))
            write_page(page, lines, src, chapter_ids, items, ids, placed, recipes, registry)
            pages.append(page.build())
        if len(pages) > MAX_PAGES:
            raise SourceError(src.name, f"{len(pages)} pages (max {MAX_PAGES})")
        unplaced = set(src.illustrations) - placed
        if unplaced:
            raise SourceError(src.name, f"illustrations declared but never placed: {sorted(unplaced)}")
        shown |= placed
        chapters.append({
            "id": f"{NS}:{src.id}",
            "source": f"docs/almanac/{src.name}",
            "body": {
                "title": title_key,
                "order": src.order,
                "bookmark_color": src.color,
                "icon": src.icon,
                "pages": pages,
            },
        })
    return chapters, lang, shown


def write_page(page, numbered, src, chapter_ids, items, ids, placed, recipes, registry):
    try:
        blocks = split_blocks(numbered)
    except SourceError as e:
        raise SourceError(src.name, str(e)) from None
    for kind, data, n in blocks:
        where = f"{src.name}:{n}"
        if kind == "para":
            page.text(check_markup(data, where, chapter_ids, items))
        elif kind == "tip":
            page.text(check_markup(data, where, chapter_ids, items), TIP_COLOR)
        elif kind == "warning":
            page.text(check_markup(data, where, chapter_ids, items), WARNING_COLOR)
        elif kind == "bullets":
            page.bullets([check_markup(t, where, chapter_ids, items) for t in data])
        elif kind == "ordered":
            for k, t in data:
                page.text(f"**{k}.** " + check_markup(t, where, chapter_ids, items))
        elif kind == "illustration":
            if data not in src.illustrations:
                raise SourceError(where, f"illustration {data!r} is not declared in the front matter")
            if any(e["type"] == "illustration" for e in page.elements):
                raise SourceError(where, "one illustration per page")
            page.illustration(data, src.illustrations[data])
            placed.add(data)
        elif kind == "show_me":
            scene, label = data
            if not RESOURCE_RE.match(scene):
                raise SourceError(where, f"show_me {scene!r} is no scene id")
            page.show_me(scene, check_markup(label, where, chapter_ids, items))
        elif kind == "recipe":
            if data not in recipes:
                raise SourceError(where, f"{{recipe:{data}}}: no such recipe in the generated data "
                                         "(only our own recipes; run ./gradlew :neoforge:runData after adding one)")
            page.recipe(data)
        elif kind == "spotlight":
            if data.startswith(NS + ":") and data not in items:
                raise SourceError(where, f"{{spotlight:{data}}} is no item of ours")
            page.spotlight(data)
        elif kind == "spotlight3d":
            what, rid, caption = data
            known = registry[what] if rid.startswith(NS + ":") else VANILLA[what]
            if rid not in known:
                where_known = "of ours" if rid.startswith(NS + ":") else "in VANILLA (tools/gen_almanac.py)"
                raise SourceError(where, f"{{spotlight3d:{what}:{rid}}}: no {what} {where_known}")
            page.spotlight_3d(what, rid, caption)
        elif kind == "contents":
            page.bullets([f"[[{rid}]]" for rid in ids if rid != f"{NS}:{src.id}"])


def render_json(chapters, lang):
    doc = {
        "_comment": "Generated by tools/gen_almanac.py from docs/almanac/; do not edit. "
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
    for stale in TEXTURES.glob("*.png"):
        if stale.stem not in ILLUSTRATIONS:
            stale.unlink()
    total = 0
    for name in sorted(ILLUSTRATIONS):
        render, panel = ILLUSTRATIONS[name]
        w, h, rows = make_illustration(RENDERS / render, panel)
        target = TEXTURES / f"{name}.png"
        write_png(target, w, h, rows)
        total += target.stat().st_size
        print(f"  {name}.png {w}x{h} {target.stat().st_size} bytes")
    print(f"{len(ILLUSTRATIONS)} illustrations, {total} bytes")


# ---------------------------------------------------------------------------------------------------- main

def generate(directory=None):
    _, _, items = lib.load_items()
    chapters, lang, shown = build_chapters(read_sources(directory), items)
    unused = set(ILLUSTRATIONS) - shown
    if unused:
        raise SystemExit(f"ILLUSTRATIONS that no chapter shows (drop them or place them): {sorted(unused)}")
    return chapters, lang


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
