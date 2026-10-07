#!/usr/bin/env python3
"""Generates the in-game guide (GuideME) from docs/guide.md (work package D1).

Run (from the repository root, Python 3 standard library only):

    python3 tools/gen_guideme.py            # writes into common/src/main/resources/assets/pirates_n_ships/
    python3 tools/gen_guideme.py --out DIR  # writes the same files under DIR (GuideBookTest compares the two)
    python3 tools/gen_guideme.py --root R --out DIR  # reads the inputs below from R instead of the repository (tests)

Run it after every change to docs/guide.md, the lang file or the recipes, and commit the output. The JUnit test
GuideBookTest fails while the committed pages differ from a fresh run. Output is deterministic (same input, same
bytes), and the script deletes pages of sections that no longer exist.

Output (relative to the assets root of our namespace):
    guideme_guides/guide.json               the guide definition: guide id pirates_n_ships:guide
    guides/pirates_n_ships/guide/index.md   start page: intro, a "new captain" path, <SubPages> of all sections
    guides/pirates_n_ships/guide/<slug>.md  one page per "## N. Title" section of docs/guide.md

Inputs:
    docs/guide.md                                                         the player guide (single source of truth)
    common/src/generated/resources/assets/pirates_n_ships/lang/en_us.json display names -> registry ids
    common/src/{generated,main}/resources/assets/pirates_n_ships/models/item/*.json   item models (checked, below)
    common/src/generated/resources/data/pirates_n_ships/recipe/*.json     which items have a recipe

Which ids are items: the registered items, i.e. the ids with an "item.pirates_n_ships.<id>" lang key, plus the
"block.pirates_n_ships.<id>" keys that have an item model (block items; a block key without one is a block without an
item and is skipped). Every "item." id must have an item model, or the script stops naming it. An item model file
without a lang key is ignored when another item model lists it as an "overrides" target (a variant such as
pistol_loaded, selected by an item property predicate), and stops the script as an orphan otherwise.

What it does to each section:
    - "### Heading" becomes "## Heading" (the page title is the only "#"); "---" separators are dropped.
    - Links to "#anchor" in guide.md become links to the page (and GuideME heading anchor) that holds that heading.
      Links to other repository files (design.md, playtests/, ...) become their plain text; http(s) links stay.
      An anchor that matches no heading stops the script (fix guide.md).
    - Tables stay GFM tables (GuideME renders them). In a column headed Block, Item, Flag or with an empty header,
      a cell whose text is entirely our item names ("Helm", "Pistol, Musket", "Mermaid, Lion, Eagle and Skull
      Figurehead") or a few vanilla items ("sugar", "oak log") becomes <ItemLink id="..." /> tags. Anything else
      stays text.
    - A table with a Recipe column gets a <Row> of <RecipeFor id="..." /> below it, for every linked item of ours that
      has a generated recipe; the All blocks and All items pages also start with an <ItemGrid> of their items.
    - "<", "{" and "}" outside code spans are escaped (GuideME pages are MDX, where they would start a tag or an
      expression). Commands and config keys are already code spans in guide.md and stay so.
    - Front matter: navigation title, parent index.md, position (10 x section number), an icon item per section,
      and item_ids: each of our items is listed on exactly one page, the first page with a "###" heading naming
      it (Pantry -> provisions, Cannon -> combat), else All blocks / All items. GuideME's <ItemLink> and the
      open-guide hotkey jump to that page.
Limits: no prose linking (only table cells), no images or game scenes, one page per section (sub-sections are
headings on that page, findable through GuideME's search), English only.

GuideME facts this relies on (version 21.1.19 for Minecraft 1.21.1; source in refs/guideme, branch 1.21.1):
    - Maven Central, org.appliedenergistics:guideme:21.1.19 (NeoForge jar, plus an ":api" classifier). No Fabric
      build exists for 1.21.1 (refs/guideme/settings.gradle and build.gradle use ModDevGradle only), so the guide
      is NeoForge-only and optional. https://repo1.maven.org/maven2/org/appliedenergistics/guideme/
    - Data-driven guides: every assets/<ns>/guideme_guides/<path>.json defines the guide <ns>:<path>
      (src/main/java/guideme/internal/GuideReloadListener.java:109, format DataDrivenGuide.java: item_settings,
      default_language, custom_colors). https://guideme.appliedenergistics.org/data-driven-guides
    - Pages: every .md under assets/<ns>/guides/<guide ns>/<guide path>/ (GuideBuilder.java:49), start page
      index.md (GuideBuilder.java:50). Front matter "navigation" (title, parent, position, icon) in
      compiler/Frontmatter.java, "item_ids" in indices/ItemIndex.java:32.
      https://guideme.appliedenergistics.org/authoring/
    - Markdown: CommonMark, GFM tables and strikethrough, YAML front matter, MDX tags
      (docs/docs/30-authoring/markdown.md). Tags: ItemLink, ItemImage, ItemGrid/ItemIcon, BlockImage, Recipe,
      RecipeFor, RecipesFor, CategoryIndex, SubPages, Row, Column, CommandLink, KeyBind, Color
      (src/main/java/guideme/compiler/tags/, scene/). Heading anchors are the heading text, lower case, whitespace
      runs replaced by "-" (compiler/AnchorIndexer.java:64), not GitHub's slugs.
    - The generic guide item is guideme:guide with the data component guideme:guide_id = the guide id
      (internal/GuideME.java:32 and :51); /guideme open and /guideme give exist (internal/command/GuideCommand.java).
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ next to the tools

NS = "pirates_n_ships"
GUIDE_PATH = "guide"


def set_root(root):
    """Points the input paths at a repository root (the real one by default; GuideBookTest passes a temp copy)."""
    global ROOT, GUIDE_MD, ASSETS_MAIN, ASSETS_GEN, LANG, RECIPES
    ROOT = Path(root).resolve()
    GUIDE_MD = ROOT / "docs" / "guide.md"
    common = ROOT / "common" / "src"
    ASSETS_MAIN = common / "main" / "resources" / "assets" / NS
    ASSETS_GEN = common / "generated" / "resources" / "assets" / NS
    LANG = ASSETS_GEN / "lang" / "en_us.json"
    RECIPES = common / "generated" / "resources" / "data" / NS / "recipe"


set_root(Path(__file__).resolve().parent.parent)

# Lang keys of the guide item's name and tooltip (written by GuideModule's datagen, checked by GuideBookTest).
NAME_KEY = f"guide.{NS}.{GUIDE_PATH}.name"
TOOLTIP_KEY = f"guide.{NS}.{GUIDE_PATH}.tooltip"

# Page file names per section title; an unknown title falls back to a slug of the title.
SLUGS = {
    "Your first ship": "first_ship",
    "Ships": "ships",
    "Sailing": "sailing",
    "Crew": "crew",
    "Flags": "flags",
    "Provisions": "provisions",
    "Cargo and trade": "trade",
    "Law, bounties and the brig": "law",
    "Weapons and combat": "combat",
    "All blocks": "blocks",
    "All items": "items",
    "Commands": "commands",
    "Configuration": "configuration",
    "Datapacks": "datapacks",
    "What does not exist yet": "not_yet",
}

# Navigation icon per page (item ids; ours without namespace). Pages without an entry get no icon.
ICONS = {
    "index": "helm",
    "first_ship": "minecraft:oak_boat",
    "ships": "helm",
    "sailing": "yard",
    "crew": "captains_whistle",
    "flags": "jolly_roger_flag",
    "provisions": "pantry",
    "trade": "doubloon",
    "law": "bounty_proof",
    "combat": "cutlass",
    "blocks": "cargo_crate",
    "items": "rope",
    "commands": "minecraft:command_block",
    "configuration": "minecraft:comparator",
    "datapacks": "minecraft:knowledge_book",
    "not_yet": "minecraft:barrier",
}

# Vanilla items that guide.md tables name in lower case (trade goods). Also the allowlist GuideBookTest accepts.
VANILLA_ITEMS = {
    "sugar": "minecraft:sugar",
    "cod": "minecraft:cod",
    "oak log": "minecraft:oak_log",
    "iron ingot": "minecraft:iron_ingot",
    "wheat": "minecraft:wheat",
    "leather": "minecraft:leather",
    "cocoa beans": "minecraft:cocoa_beans",
    "gunpowder": "minecraft:gunpowder",
}

# The new captain's path on the index page: (label, page slug, heading on that page or None).
CAPTAINS_PATH = [
    ("Build your first ship", "first_ship", None),
    ("Learn to sail", "sailing", None),
    ("Take on a crew", "crew", None),
    ("Stock the provisions", "provisions", None),
    ("Trade between ports", "trade", None),
    ("Mind the law", "law", None),
    ("Fight", "combat", None),
    ("Steer clear of sea hazards", None, "Sea hazards"),
]

# Table columns whose cells name items.
ITEM_COLUMNS = {"block", "item", "flag", ""}

SECTION_RE = re.compile(r"^## (\d+)\. (.+?)\s*$")
SUBHEADING_RE = re.compile(r"^### (.+?)\s*$")
LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)\s]+)\)")
CODE_RE = re.compile(r"`[^`]*`")


def github_slug(heading):
    """The anchor GitHub gives a heading (what guide.md's own links use)."""
    s = heading.strip().lower()
    s = re.sub(r"[^\w\- ]", "", s)
    return s.replace(" ", "-")


def guideme_anchor(heading):
    """The anchor GuideME gives a heading (AnchorIndexer.normalizeAnchor: lower case, whitespace runs -> '-')."""
    return re.sub(r"\s+", "-", heading.strip().lower())


def slug_of(title):
    return SLUGS.get(title) or re.sub(r"[^a-z0-9]+", "_", title.lower()).strip("_")


def yaml_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def full_id(item):
    return item if ":" in item else f"{NS}:{item}"


# ---------------------------------------------------------------------------------------------------- inputs

def override_target(model):
    """The item model id ("pirates_n_ships:x") an "overrides" entry's model reference names, else None."""
    if not isinstance(model, str):
        return None
    ns, _, path = model.rpartition(":")
    if (ns or "minecraft") != NS or not path.startswith("item/"):
        return None
    return f"{NS}:{path[len('item/'):]}"


def load_items():
    """(display name lower -> id, id -> display name, set of item ids) of our items.

    The items are the registered ones: every "item." lang key, and every "block." key with an item model (generated
    or hand-made). Fails, naming the ids, when an "item." key has no item model (the guide could not show it) or an
    item model has no lang key and is no "overrides" target of another item model (an orphan: a model of nothing, or
    an item without a name). Override targets without a lang key are variants of an item (pistol_loaded), ignored.
    """
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    models = {}
    for base in (ASSETS_MAIN, ASSETS_GEN):
        d = base / "models" / "item"
        if d.is_dir():
            models.update((f"{NS}:{p.stem}", p) for p in d.glob("*.json"))
    variants = set()
    for rid in sorted(models):
        overrides = json.loads(models[rid].read_text(encoding="utf-8")).get("overrides")
        for entry in overrides if isinstance(overrides, list) else []:
            target = override_target(entry.get("model")) if isinstance(entry, dict) else None
            if target is not None and target != rid:
                variants.add(target)
    by_name, names, problems = {}, {}, []
    for key in sorted(lang):
        m = re.fullmatch(rf"(item|block)\.{NS}\.([a-z0-9_]+)", key)
        if not m:
            continue
        rid = f"{NS}:{m.group(2)}"
        if rid not in models:
            if m.group(1) == "item":
                problems.append(f"{rid}: lang key {key} but no item model (models/item/{m.group(2)}.json)")
            continue
        names.setdefault(rid, lang[key])
        by_name.setdefault(lang[key].lower(), rid)
    for rid in sorted(set(models) - set(names) - variants):
        problems.append(f"{rid}: item model but no lang name (item.{NS}.{rid.split(':')[1]} or block.{NS}.…) "
                        f"and no item model lists it under \"overrides\"")
    if problems:
        raise SystemExit("gen_guideme: items the guide cannot name or show:\n  " + "\n  ".join(problems))
    return by_name, names, set(names)


def load_recipe_results():
    results = set()
    if RECIPES.is_dir():
        for p in sorted(RECIPES.glob("*.json")):
            result = json.loads(p.read_text(encoding="utf-8")).get("result")
            if isinstance(result, dict) and "id" in result:
                results.add(result["id"])
    return results


def parse_sections(text):
    """[(number, title, [body lines])] of the "## N. Title" sections, in order."""
    sections, current = [], None
    for line in text.splitlines():
        m = SECTION_RE.match(line)
        if m:
            current = (int(m.group(1)), m.group(2), [])
            sections.append(current)
        elif line.startswith("## "):
            current = None  # "## Contents" and other unnumbered sections are not pages
        elif current is not None:
            current[2].append(line)
    return sections


# ---------------------------------------------------------------------------------------------------- transforms

class Resolver:
    def __init__(self, by_name):
        self.by_name = by_name

    def one(self, name):
        n = name.strip().lower()
        return self.by_name.get(n) or VANILLA_ITEMS.get(n)

    def cell(self, text):
        """Item ids for a cell that consists only of item names, else None. Keeps a trailing "(...)" note."""
        m = re.fullmatch(r"(.*?)\s*(\([^)]*\))?", text.strip())
        names, note = m.group(1), m.group(2)
        if not names:
            return None
        single = self.one(names)
        if single:
            return [single], note
        parts = [p for p in re.split(r",\s*|\s+and\s+", names) if p]
        if len(parts) < 2:
            return None
        suffix = " ".join(parts[-1].split()[1:])  # "Skull Figurehead" -> "Figurehead"
        ids = []
        for p in parts:
            rid = self.one(p) or (self.one(f"{p} {suffix}") if suffix else None)
            if not rid:
                return None
            ids.append(rid)
        return ids, note


def item_link(rid):
    return f'<ItemLink id="{rid}" />'


def split_row(line):
    """Cells of a GFM table row; "\\|" inside a cell is not a separator."""
    inner = line.strip()[1:-1] if line.strip().endswith("|") else line.strip()[1:]
    cells, cur, i = [], "", 0
    while i < len(inner):
        if inner[i] == "\\" and i + 1 < len(inner) and inner[i + 1] == "|":
            cur += "\\|"
            i += 2
            continue
        if inner[i] == "|":
            cells.append(cur)
            cur = ""
        else:
            cur += inner[i]
        i += 1
    cells.append(cur)
    return [c.strip() for c in cells]


def transform_table(rows, resolver, recipes, plain):
    """(new table lines, linked ids, linked ids with a recipe) for one GFM table. plain(text) renders other cells."""
    header = split_row(rows[0])
    keys = [h.lower() for h in header]
    has_recipe = "recipe" in keys
    out = ["| " + " | ".join(plain(h) for h in header) + " |", rows[1]]
    linked, with_recipe = [], []
    for row in rows[2:]:
        cells = split_row(row)
        for i, cell in enumerate(cells):
            res = resolver.cell(cell) if i < len(keys) and keys[i] in ITEM_COLUMNS else None
            if res:
                ids, note = res
                cells[i] = ", ".join(item_link(r) for r in ids) + (f" {plain(note)}" if note else "")
                linked.extend(ids)
                if has_recipe:
                    with_recipe.extend(r for r in ids if r in recipes)
            else:
                cells[i] = plain(cell)
        out.append("| " + " | ".join(cells) + " |")
    return out, linked, with_recipe


def escape_mdx(text):
    """Escapes '<', '{' and '}' outside code spans (MDX would read them as a tag or an expression)."""
    out, pos = [], 0
    for m in CODE_RE.finditer(text):
        out.append(re.sub(r"([<{}])", r"\\\1", text[pos:m.start()]))
        out.append(m.group(0))
        pos = m.end()
    out.append(re.sub(r"([<{}])", r"\\\1", text[pos:]))
    return "".join(out)


def unique(seq):
    seen, out = set(), []
    for x in seq:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


# ---------------------------------------------------------------------------------------------------- build

def build(out_root):
    by_name, names, item_ids = load_items()
    recipes = load_recipe_results()
    resolver = Resolver(by_name)
    sections = parse_sections(GUIDE_MD.read_text(encoding="utf-8"))
    if not sections:
        raise SystemExit("no '## N. Title' sections in docs/guide.md")

    # Pages and the anchors guide.md's links can point at
    pages, anchors, headings = [], {}, {}
    for number, title, body in sections:
        slug = slug_of(title)
        pages.append((number, title, slug, body))
        anchors[github_slug(f"{number}. {title}")] = (slug, None)
        for line in body:
            m = SUBHEADING_RE.match(line)
            if m:
                anchors.setdefault(github_slug(m.group(1)), (slug, guideme_anchor(m.group(1))))
                headings.setdefault(slug, []).append(m.group(1))
    slugs = [p[2] for p in pages]
    if len(set(slugs)) != len(slugs):
        raise SystemExit(f"two sections map to the same page: {slugs}")

    def link(m):
        text, href = m.group(1), m.group(2)
        if href.startswith(("http://", "https://")):
            return m.group(0)
        if href.startswith("#"):
            target = anchors.get(href[1:])
            if target is None:
                raise SystemExit(f"docs/guide.md links to #{href[1:]}, which is no heading")
            page, anchor = target
            return f"[{text}]({page}.md" + (f"#{anchor}" if anchor else "") + ")"
        return text  # a repository file: no page in game

    # Which page owns each item (item_ids front matter): first "###" heading naming it, else blocks / items
    owner = {}
    for rid in sorted(item_ids):
        name = names.get(rid, "").lower()
        if not name:
            continue
        for _, _, slug, _ in pages:
            if any(name in h.lower() or name + "s" in h.lower() for h in headings.get(slug, [])):
                owner[rid] = slug
                break

    rendered = {}
    table_ids = {}
    for number, title, slug, body in pages:
        lines, i = [], 0
        page_linked, page_recipes = [], []
        while i < len(body):
            line = body[i]
            if line.strip() == "---":
                i += 1
                continue
            m = SUBHEADING_RE.match(line)
            if m:
                lines.append(f"## {m.group(1)}")
                i += 1
                continue
            if line.startswith("|") and i + 1 < len(body) and re.match(r"^\|[\s\-:|]+\|\s*$", body[i + 1]):
                j = i
                while j < len(body) and body[j].startswith("|"):
                    j += 1
                table, linked, with_recipe = transform_table(
                    body[i:j], resolver, recipes, lambda t: LINK_RE.sub(link, escape_mdx(t)))
                lines.extend(table)
                page_linked.extend(linked)
                if with_recipe:
                    lines.append("")
                    lines.append("<Row>")
                    lines.extend(f'  <RecipeFor id="{r}" />' for r in unique(with_recipe))
                    lines.append("</Row>")
                page_recipes.extend(with_recipe)
                i = j
                continue
            lines.append(line)
            i += 1
        # escape and relink the prose (tables were done above; their tags must not be escaped)
        text, out, buf = "\n".join(lines), [], []
        for line in text.split("\n"):
            if line.startswith("|") or line.strip() in ("<Row>", "</Row>") or line.startswith("  <RecipeFor "):
                if buf:
                    out.append(LINK_RE.sub(link, escape_mdx("\n".join(buf))))
                    buf = []
                out.append(line)
            else:
                buf.append(line)
        if buf:
            out.append(LINK_RE.sub(link, escape_mdx("\n".join(buf))))
        rendered[slug] = "\n".join(out)
        table_ids[slug] = unique(r for r in page_linked if r.startswith(NS + ":"))

    # Items in no heading: the All blocks / All items page if it lists them, else by kind
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    for rid in sorted(item_ids):
        if rid in owner or rid not in names:
            continue
        listed = [s for s in ("blocks", "items") if rid in table_ids.get(s, [])]
        if listed:
            owner[rid] = listed[0]
        else:
            owner[rid] = "blocks" if f"block.{NS}.{rid.split(':')[1]}" in lang else "items"
        if owner[rid] not in slugs:
            del owner[rid]

    files = {}
    for number, title, slug, _ in pages:
        fm = ["---", "navigation:", f"  title: {yaml_str(title)}", "  parent: index.md",
              f"  position: {number * 10}"]
        if slug in ICONS:
            fm.append(f"  icon: {full_id(ICONS[slug])}")
        owned = [rid for rid in sorted(owner) if owner[rid] == slug]
        if owned:
            fm.append("item_ids:")
            fm.extend(f"  - {rid}" for rid in owned)
        fm.append("---")
        body = rendered[slug].strip("\n")
        if slug in ("blocks", "items") and table_ids.get(slug):
            grid = ["<ItemGrid>"] + [f'  <ItemIcon id="{r}" />' for r in table_ids[slug]] + ["</ItemGrid>", ""]
            body = "\n".join(grid) + "\n" + body
        body = re.sub(r"\n{3,}", "\n\n", body)
        files[f"{slug}.md"] = "\n".join(fm) + f"\n\n# {title}\n\n" + body + "\n"

    # Index page
    path_lines = []
    for n, (label, slug, heading) in enumerate(CAPTAINS_PATH, 1):
        if heading is not None:
            target = anchors.get(github_slug(heading))
            if target is None:
                raise SystemExit(f"captain's path: no heading '{heading}' in docs/guide.md")
            slug, anchor = target
            href = f"{slug}.md#{anchor}"
        else:
            if slug not in slugs:
                raise SystemExit(f"captain's path: no page '{slug}'")
            href = f"{slug}.md"
        path_lines.append(f"{n}. [{label}]({href})")
    mod_name = "Pirates 'n' Ships"
    index = "\n".join([
        "---",
        "navigation:",
        f"  title: {yaml_str(mod_name)}",
        "  position: 0",
        f"  icon: {full_id(ICONS['index'])}",
        "---",
        "",
        "# Pirates 'n' Ships",
        "",
        "Everything that exists in the mod today: what each block, item and system does and how to use it.",
        "Several features have no in-world source yet (no ports, no navy ships, no hiring), so operators reach them",
        "through commands under `/pirates`. Use the search for any block, item or config key.",
        "",
        "## A new captain's path",
        "",
        *path_lines,
        "",
        "## All topics",
        "",
        "<SubPages icons={true} />",
        "",
    ])
    files["index.md"] = index

    definition = {
        "item_settings": {
            "display_name": {"translate": NAME_KEY},
            "tooltip_lines": [{"translate": TOOLTIP_KEY, "color": "gray"}],
        }
    }

    # Write
    guide_dir = out_root / "guides" / NS / GUIDE_PATH
    guide_dir.mkdir(parents=True, exist_ok=True)
    for stale in guide_dir.glob("*.md"):
        if stale.name not in files:
            stale.unlink()
    for name in sorted(files):
        (guide_dir / name).write_text(files[name], encoding="utf-8", newline="\n")
    def_dir = out_root / "guideme_guides"
    def_dir.mkdir(parents=True, exist_ok=True)
    (def_dir / f"{GUIDE_PATH}.json").write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8",
                                                newline="\n")
    return files


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", type=Path, default=ASSETS_MAIN,
                    help="assets root to write into (default: the mod's assets/pirates_n_ships)")
    ap.add_argument("--root", type=Path, default=None,
                    help="repository root to read the inputs from (default: this script's repository)")
    args = ap.parse_args()
    if args.root is not None:
        set_root(args.root)
    files = build(args.out.resolve())
    print(f"wrote {len(files)} pages and guideme_guides/{GUIDE_PATH}.json under {args.out}")


if __name__ == "__main__":
    main()
