#!/usr/bin/env python3
"""Unit tests of tools/gen_almanac.py's conversions (standard library unittest; AlmanacGuideTest runs this file).

    python3 tools/test_gen_almanac.py
"""
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_almanac as ga  # noqa: E402

NS = ga.NS
ITEMS = {"helm": f"{NS}:helm", "yard": f"{NS}:yard", "pistol": f"{NS}:pistol", "musket": f"{NS}:musket"}


def ctx(chapter="ships", anchors=None):
    return ga.Context(ITEMS, anchors if anchors is not None else {"limits-to-know": "ships", "sails": "sailing"},
                      chapter)


class EscapeTest(unittest.TestCase):

    def test_markup_characters_are_escaped(self):
        self.assertEqual(ga.escape("a*b"), "a\\*b")
        self.assertEqual(ga.escape("x\\y"), "x\\\\y")
        self.assertEqual(ga.escape("{#ff0000}"), "\\{#ff0000}")
        self.assertEqual(ga.escape("[[x]] [y]"), "\\[[x]] [y]")

    def test_underscores_inside_words_stay(self):
        self.assertEqual(ga.escape("ship_screen.enabled"), "ship_screen.enabled")
        self.assertEqual(ga.escape("_x and y_"), "\\_x and y\\_")


class InlineTest(unittest.TestCase):

    def test_bold_italic_code(self):
        c = ctx()
        self.assertEqual(ga.convert_inline("a **bold** and *Navy:* here", c), "a **bold** and _Navy:_ here")
        self.assertEqual(ga.convert_inline("config `mobs.shark.*` on", c), "config mobs.shark.\\* on")
        self.assertEqual(ga.convert_inline("`pirates_n_ships:terrain`", c), "pirates_n_ships:terrain")

    def test_links_to_other_chapters_become_almanac_links(self):
        c = ctx(chapter="first_ship")
        self.assertEqual(ga.convert_inline("see [Limits](#limits-to-know)", c),
                         f"see [[{NS}:ships|Limits]]")
        self.assertEqual(ga.convert_inline("[`design.md`](design.md)", c), "design.md")
        self.assertEqual(ga.convert_inline("[Sable](https://example.org)", c), "Sable")

    def test_links_inside_the_own_chapter_are_plain_text(self):
        self.assertEqual(ga.convert_inline("see [Limits](#limits-to-know)", ctx(chapter="ships")), "see Limits")

    def test_unknown_anchor_stops(self):
        with self.assertRaises(SystemExit):
            ga.convert_inline("[x](#nowhere)", ctx())

    def test_bold_item_names_get_one_icon_per_page(self):
        c = ctx()
        first = ga.convert_inline("Place a **helm**, then **yards**; the **helm** steers.", c)
        self.assertEqual(first, f"Place a {{item:{NS}:helm}}**helm**, then {{item:{NS}:yard}}**yards**; the **helm** steers.")
        c.new_page()
        self.assertTrue(ga.convert_inline("**Helm**", c).startswith(f"{{item:{NS}:helm}}"))

    def test_plain_words_never_get_icons(self):
        self.assertEqual(ga.convert_inline("the helm and a yard", ctx()), "the helm and a yard")


class BlockTest(unittest.TestCase):

    def test_paragraphs_lists_and_tables(self):
        lines = [
            "First line", "second line.", "",
            "- one", "  continued", "- two", "",
            "1. **Build** it", "2. Sail", "",
            "| Item | Recipe | What it does |", "|---|---|---|",
            "| Pistol, Musket | iron | Shoots. |",
            "---",
        ]
        blocks = ga.split_blocks(lines)
        self.assertEqual(blocks[0], ("para", "First line second line."))
        self.assertEqual(blocks[1], ("bullets", ["one continued", "two"]))
        self.assertEqual(blocks[2], ("ordered", [(1, "**Build** it"), (2, "Sail")]))
        self.assertEqual(blocks[3][0], "table")
        self.assertEqual(len(blocks), 4)
        entries = ga.table_entries(blocks[3][1], ctx())
        self.assertEqual(entries, [f"{{item:{NS}:pistol}}{{item:{NS}:musket}}**Pistol, Musket** (recipe: iron): Shoots."])

    def test_pages_split_at_subsections(self):
        pages = ga.split_pages(["intro", "### A", "a text", "### B", "b text"])
        self.assertEqual(pages, [(None, ["intro"]), ("A", ["a text"]), ("B", ["b text"])])


class ChapterTest(unittest.TestCase):

    GUIDE = "\n".join(
        ["# Title", "## Contents", "1. [x](#1-your-first-ship)"]
        + [f"## {n}. {t}\nText of {n}." for n, t in [
            (0, "The guide book"), (1, "Your first ship"), (2, "Ships"), (3, "Sailing"), (4, "Crew"), (5, "Flags"),
            (6, "Provisions"), (7, "Cargo and trade"), (8, "Law, bounties and the brig"), (10, "All blocks"),
            (11, "All items"), (12, "Commands"), (13, "Configuration"), (14, "Datapacks"),
            (15, "What does not exist yet")]]
        + ["## 9. Weapons and combat", "Intro.", "### Cannons", "Load it. **Aim** it.", "", "Fire it."])

    def build(self):
        saved_ill = ga.ILLUSTRATIONS
        ga.ILLUSTRATIONS = {("combat", "Cannons"): ("cannon.png", ga.FULL)}
        try:
            return ga.build_chapters(self.GUIDE, ITEMS)
        finally:
            ga.ILLUSTRATIONS = saved_ill

    def test_one_chapter_per_section_in_order_with_keys_in_lang(self):
        chapters, lang = self.build()
        self.assertEqual([c["section"] for c in chapters], sorted(c["section"] for c in chapters))
        self.assertEqual(len(chapters), 16)
        self.assertEqual(chapters[0]["id"], f"{NS}:welcome")
        for c in chapters:
            body = c["body"]
            self.assertEqual(body["order"], c["section"] * 10)
            self.assertIn(body["title"], lang)
            for page in body["pages"]:
                for e in page["elements"]:
                    for k in ([e["key"]] if "key" in e else []) + e.get("items", []) + ([e["label"]] if "label" in e else []):
                        self.assertIn(k, lang)
                        self.assertTrue(k.startswith(f"{NS}.almanac.{c['id'].split(':')[1]}."), k)

    def test_cannons_page_has_its_heading_illustration_and_show_me_after_the_first_paragraph(self):
        chapters, lang = self.build()
        combat = next(c for c in chapters if c["id"] == f"{NS}:combat")
        page = combat["body"]["pages"][1]
        types = [e["type"] for e in page["elements"]]
        self.assertEqual(types, ["heading", "illustration", "text", "show_me", "text"])
        self.assertEqual(lang[page["elements"][0]["key"]], "Cannons")
        self.assertEqual(page["elements"][3]["scene"], f"{NS}:cannon_loading")
        self.assertEqual(page["elements"][1]["texture"], f"{NS}:textures/almanac/cannon.png")

    def test_welcome_links_every_other_chapter(self):
        chapters, lang = self.build()
        contents = chapters[0]["body"]["pages"][-1]["elements"][-1]
        links = [lang[k] for k in contents["items"]]
        self.assertEqual(links, [f"[[{c['id']}]]" for c in chapters[1:]])

    def test_a_missing_section_stops(self):
        with self.assertRaises(SystemExit):
            ga.build_chapters(self.GUIDE.replace("## 15. What does not exist yet", "## Notes"), ITEMS)


class PngTest(unittest.TestCase):

    def test_round_trip_and_downscale(self):
        import tempfile
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
