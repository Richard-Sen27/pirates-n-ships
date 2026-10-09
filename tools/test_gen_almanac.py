#!/usr/bin/env python3
"""Unit tests of tools/gen_almanac.py (standard library unittest; AlmanacGuideTest runs this file).

    python3 tools/test_gen_almanac.py
"""
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_almanac as ga  # noqa: E402

NS = ga.NS
ITEMS = {f"{NS}:helm", f"{NS}:yard", f"{NS}:cannon", f"{NS}:captains_almanac"}
CHAPTERS = {f"{NS}:welcome", f"{NS}:sailing", f"{NS}:combat"}

WELCOME = """---
id: welcome
title: Welcome aboard
order: 10
icon: captains_almanac
color: "#8B1A1A"
---
Read this first, hand.

## Contents
contents:
"""

SAILING = """---
id: sailing
title: Sailing
order: 40
icon: yard
color: #2f5d7c
illustrations:
  - yard: 0.05 0.09 0.9 0.35
---

## Sails
illustration: yard
Hang a {item:pirates_n_ships:yard}**yard** high on the mast,
then another below it.
show_me: sails_and_winch | Show me: rigging a sail

- one
  continued
- two

1. **Hoist** it
2. Sail

> Tip: furl before a storm.

>! Never sail into a waterspout.

## The helm
See [[pirates_n_ships:combat|the guns]] and [[welcome]].
"""

COMBAT = """---
id: combat
title: Weapons and combat
order: 100
icon: cannon
color: #4a4a4a
illustrations:
  - cannon: 0.1 0.1 0.8 0.3
---
Steel and powder.

## Cannons
illustration: cannon
Powder first, then the ball.
show_me: pirates_n_ships:cannon_loading
"""


def build(files, illustrations=None):
    """Chapters and lang of these source files (name -> text), with the illustrations they use."""
    saved = ga.ILLUSTRATIONS
    ga.ILLUSTRATIONS = illustrations or {"yard": ("yard.png", ga.FULL), "cannon": ("cannon.png", ga.FULL)}
    try:
        with tempfile.TemporaryDirectory() as tmp:
            for name, text in files.items():
                (Path(tmp) / name).write_text(text, encoding="utf-8")
            chapters, lang, _ = ga.build_chapters(ga.read_sources(tmp), ITEMS)
            return chapters, lang
    finally:
        ga.ILLUSTRATIONS = saved


BOOK = {"01-welcome.md": WELCOME, "04-sailing.md": SAILING, "10-combat.md": COMBAT}


def page_texts(chapter, lang, page):
    out = []
    for e in chapter["body"]["pages"][page]["elements"]:
        if "key" in e:
            out.append((e["type"], lang[e["key"]]))
        elif "items" in e:
            out.append(("list", [lang[k] for k in e["items"]]))
        elif "label" in e:
            out.append(("show_me", e["scene"], lang[e["label"]]))
        else:
            out.append((e["type"], e["texture"]))
    return out


class FrontMatterTest(unittest.TestCase):

    def test_keys_lists_and_quotes(self):
        meta, start = ga.parse_front_matter(SAILING.split("\n"), "x")
        self.assertEqual(meta["id"], "sailing")
        self.assertEqual(meta["color"], "#2f5d7c")
        self.assertEqual(meta["illustrations"], ["yard: 0.05 0.09 0.9 0.35"])
        self.assertEqual(SAILING.split("\n")[start - 1], "---")
        meta, _ = ga.parse_front_matter(WELCOME.split("\n"), "x")
        self.assertEqual(meta["color"], "#8B1A1A", "quotes are removed")

    def test_illustration_boxes(self):
        saved = ga.ILLUSTRATIONS
        ga.ILLUSTRATIONS = {"yard": ("yard.png", ga.FULL)}
        try:
            self.assertEqual(ga.parse_illustrations(["yard: 0.1 0.2 0.5 0.25"], "x"), {"yard": (0.1, 0.2, 0.5, 0.25)})
            for bad in (["yard: 0.5 0.1 0.6 0.2"], ["yard: 1 2"], ["unknown: 0 0 0.5 0.5"], ["yard 0 0 1 1"]):
                with self.assertRaises(SystemExit, msg=bad):
                    ga.parse_illustrations(bad, "x")
        finally:
            ga.ILLUSTRATIONS = saved

    def test_missing_front_matter_or_key_stops(self):
        with self.assertRaises(SystemExit):
            ga.Source("01-welcome.md", "Just text")
        with self.assertRaises(SystemExit):
            ga.Source("01-welcome.md", WELCOME.replace("order: 10\n", ""))
        with self.assertRaises(SystemExit):
            ga.Source("01-welcome.md", WELCOME.replace("order: 10\n", "order: 10\nsecret: 1\n"))

    def test_file_name_must_match_the_id(self):
        with self.assertRaises(SystemExit):
            ga.Source("01-hello.md", WELCOME)
        with self.assertRaises(SystemExit):
            ga.Source("welcome.md", WELCOME)
        self.assertEqual(ga.Source("01-welcome.md", WELCOME).id, "welcome")


class MarkupTest(unittest.TestCase):

    def check(self, text):
        return ga.check_markup(text, "x", CHAPTERS, ITEMS)

    def test_clean_markup_passes_unchanged(self):
        for text in ("a **bold** and _italic_ word", "{#8b1a1a}**Beware:**{/} the kraken",
                     "{item:pirates_n_ships:helm}**Helm** and {item:minecraft:gunpowder}",
                     "see [[pirates_n_ships:combat|the guns]] or [[welcome]]", "snake_case stays",
                     "a literal \\* star, \\{ brace and \\[[ bracket", "the Captain's whistle"):
            self.assertEqual(self.check(text), text)

    def test_broken_markup_stops(self):
        for text in ("an **unclosed bold", "a *markdown* italic", "a `code` span", "{#8b1a1a}never closed",
                     "{/} too many", "[[nowhere|a link]]", "[[combat|unclosed", "{item:helm}",
                     "{item:pirates_n_ships:nothing}", "[a link](#somewhere)", "a {brace}", "trailing \\"):
            with self.assertRaises(SystemExit, msg=text):
                self.check(text)


class BlockTest(unittest.TestCase):

    def test_blocks_directives_and_callouts(self):
        lines = list(enumerate(SAILING.split("\n")[11:], 12))
        blocks = [(k, d) for k, d, _ in ga.split_blocks(lines)]
        self.assertEqual(blocks[0], ("illustration", "yard"))
        self.assertEqual(blocks[1][0], "para")
        self.assertTrue(blocks[1][1].endswith("high on the mast, then another below it."))
        self.assertEqual(blocks[2], ("show_me", (f"{NS}:sails_and_winch", "Show me: rigging a sail")))
        self.assertEqual(blocks[3], ("bullets", ["one continued", "two"]))
        self.assertEqual(blocks[4], ("ordered", [(1, "**Hoist** it"), (2, "Sail")]))
        self.assertEqual(blocks[5], ("tip", "Tip: furl before a storm."))
        self.assertEqual(blocks[6], ("warning", "Never sail into a waterspout."))

    def test_tables_and_deep_headings_stop(self):
        with self.assertRaises(SystemExit):
            ga.split_blocks([(1, "| a | b |")])
        with self.assertRaises(SystemExit):
            ga.split_pages(["### Too deep"], 1)

    def test_pages_split_at_headings(self):
        pages = ga.split_pages(["intro", "## A", "a text", "## B", "b text"], 1)
        self.assertEqual([(h, [t for _, t in ls]) for h, ls in pages],
                         [(None, ["intro"]), ("A", ["a text"]), ("B", ["b text"])])


class ChapterTest(unittest.TestCase):

    def test_one_chapter_per_file_in_order_with_keys_in_lang(self):
        chapters, lang = build(BOOK)
        self.assertEqual([c["id"] for c in chapters], [f"{NS}:welcome", f"{NS}:sailing", f"{NS}:combat"])
        self.assertEqual([c["body"]["order"] for c in chapters], [10, 40, 100])
        self.assertEqual(chapters[1]["body"]["bookmark_color"], "#2f5d7c")
        self.assertEqual(chapters[0]["body"]["bookmark_color"], "#8b1a1a")
        self.assertEqual(chapters[1]["body"]["icon"], f"{NS}:yard")
        for c in chapters:
            body = c["body"]
            self.assertIn(body["title"], lang)
            for page in body["pages"]:
                for e in page["elements"]:
                    for k in ([e["key"]] if "key" in e else []) + e.get("items", []) + ([e["label"]] if "label" in e else []):
                        self.assertIn(k, lang)
                        self.assertTrue(k.startswith(f"{NS}.almanac.{c['id'].split(':')[1]}."), k)

    def test_sailing_page_holds_its_blocks_in_source_order(self):
        chapters, lang = build(BOOK)
        sailing = chapters[1]
        self.assertEqual(len(sailing["body"]["pages"]), 2, "no intro: the first subsection shares the title page")
        self.assertEqual(page_texts(sailing, lang, 0), [
            ("heading", "Sailing"),
            ("heading", "Sails"),
            ("illustration", f"{NS}:textures/almanac/yard.png"),
            ("text", "Hang a {item:pirates_n_ships:yard}**yard** high on the mast, then another below it."),
            ("show_me", f"{NS}:sails_and_winch", "Show me: rigging a sail"),
            ("list", ["one continued", "two"]),
            ("text", "**1.** **Hoist** it"),
            ("text", "**2.** Sail"),
            ("text", "Tip: furl before a storm."),
            ("text", "Never sail into a waterspout."),
        ])
        elements = sailing["body"]["pages"][0]["elements"]
        self.assertEqual(elements[2]["x"], 0.05)
        self.assertEqual(elements[2]["h"], 0.35)
        self.assertEqual(elements[8]["color"], ga.TIP_COLOR)
        self.assertEqual(elements[9]["color"], ga.WARNING_COLOR)
        self.assertNotIn("color", elements[3])

    def test_show_me_gets_the_default_label_and_namespace(self):
        chapters, lang = build(BOOK)
        cannons = chapters[2]["body"]["pages"][1]["elements"]
        self.assertEqual([e["type"] for e in cannons], ["heading", "illustration", "text", "show_me"])
        self.assertEqual(cannons[3]["scene"], f"{NS}:cannon_loading")
        self.assertEqual(lang[cannons[3]["label"]], "Show me")

    def test_contents_links_every_other_chapter(self):
        chapters, lang = build(BOOK)
        contents = chapters[0]["body"]["pages"][-1]["elements"][-1]
        self.assertEqual([lang[k] for k in contents["items"]], [f"[[{NS}:sailing]]", f"[[{NS}:combat]]"])

    def test_welcome_must_come_first_and_orders_must_rise(self):
        with self.assertRaises(SystemExit):
            build({"01-sailing.md": SAILING, "02-welcome.md": WELCOME.replace("order: 10", "order: 50")})
        with self.assertRaises(SystemExit):
            build({"01-welcome.md": WELCOME.replace("order: 10", "order: 60"), "04-sailing.md": SAILING})

    def test_illustrations_must_be_declared_and_placed(self):
        with self.assertRaises(SystemExit):
            build({"01-welcome.md": WELCOME, "04-sailing.md": SAILING.replace("illustration: yard\n", "")})
        with self.assertRaises(SystemExit):
            build({"01-welcome.md": WELCOME + "illustration: yard\n"})

    def test_links_to_missing_chapters_stop(self):
        with self.assertRaises(SystemExit):
            build({"01-welcome.md": WELCOME, "04-sailing.md": SAILING})  # sailing links to combat


class SourceTest(unittest.TestCase):

    def test_the_real_almanac_builds(self):
        _, _, items = ga.guideme.load_items()
        chapters, lang, shown = ga.build_chapters(ga.read_sources(), items)
        self.assertEqual(chapters[0]["id"], f"{NS}:welcome")
        self.assertEqual(chapters[-1]["id"], f"{NS}:not_yet")
        self.assertEqual(shown, set(ga.ILLUSTRATIONS), "every illustration is shown")
        scenes = {e["scene"] for c in chapters for p in c["body"]["pages"] for e in p["elements"] if e["type"] == "show_me"}
        self.assertIn(f"{NS}:cannon_loading", scenes)


class PngTest(unittest.TestCase):

    def test_round_trip_and_downscale(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "src.png"
            w, h = 600, 300
            grey, red = bytes((200, 200, 200, 255)), bytes((180, 20, 20, 255))
            rows = [bytearray(b"".join(red if 100 <= x < 500 and 50 <= y < 250 else grey for x in range(w)))
                    for y in range(h)]
            ga.write_png(src, w, h, rows)
            rw, rh, back = ga.read_png(src)
            self.assertEqual((rw, rh), (w, h))
            self.assertEqual(bytes(back[0][:4]), grey)
            ow, oh, out = ga.make_illustration(src, ga.FULL)
            self.assertLessEqual(ow, ga.MAX_W)
            self.assertLessEqual(oh, ga.MAX_H)
            self.assertEqual(out[oh // 2][(ow // 2) * 4 + 3], 255, "the model stays opaque")
            self.assertEqual(out[0][3], 0, "the flat background is keyed out")


if __name__ == "__main__":
    unittest.main()
