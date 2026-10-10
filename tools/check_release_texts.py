#!/usr/bin/env python3
"""Checks the release texts against the code (docs/releasing.md, "Cutting a release").

Usage:
    check_release_texts.py [file ...]   check the given Markdown files (default: the store page, the README and
                                        the notes of the version being released, see below)
    check_release_texts.py --self-test  check the rules on fixed input

What fails, per file and line:
- a registry id `pirates_n_ships:<path>` that is no item, block, entity or effect in the generated lang
  (common/src/generated/resources/assets/pirates_n_ships/lang/en_us.json);
- a config key in backticks whose first part is one of our config sections (`almanac.give_on_first_join`) and that
  has no `pirates_n_ships.configuration.<key>` entry in that lang;
- an in-game name in italics (`*Sail Winch*`, `_Bilge Pumps_`): in these texts italics are reserved for the names of
  our items, blocks, mobs and effects, so every italic span must be one of their English names (case-insensitive, a
  plural "s" or "es" allowed) or the noun of a wood family (`*Helm*` stands for the Oak Helm, the Spruce Helm, ...;
  WOOD1); write emphasis in bold instead;
- a dependency named with a version (`Sable 2.0.6`, `GeckoLib: 4.9.3`, `| JEI | 19.39.0.372 |`) whose version is not
  the one in gradle.properties (a Maven suffix such as `+mc.1.21.1` may be left out);
- in a release-notes file (`*-notes.md`), a dependency of the release that is not named with its version at all.

The version being released is the environment variable VERSION when set (the release workflow's "Release notes"
step sets it to the tag without its `v`, e.g. 0.1.0 for v0.1.0), otherwise `mod_version` from gradle.properties. Its
notes are docs/release/<version>-notes.md, the file the workflow publishes; they are checked when they exist (a
hand-written notes file is optional after the first release) and the summary says when there are none.

The output is one line per problem and a summary; the exit code is 1 when anything failed.
"""
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / "common/src/generated/resources/assets/pirates_n_ships/lang/en_us.json"
GRADLE_PROPERTIES = ROOT / "gradle.properties"
DEFAULT_FILES = ["docs/release/store-page.md", "README.md"]
NOTES = "docs/release/{version}-notes.md"
MOD_ID = "pirates_n_ships"

# Dependency names as players read them -> the gradle.properties key that holds the version. Longer names first, so
# "Fabric API" is never read as plain "Fabric".
DEPENDENCIES = [
    ("Forge Config API Port", "forgeconfigapiport_version"),
    ("Player Animation Library", "pal_version"),
    ("Fabric Loader", "fabric_loader_version"),
    ("Fabric API", "fabric_version"),
    ("Minecraft", "minecraft_version"),
    ("NeoForge", "neoforge_version"),
    ("GeckoLib", "geckolib_version"),
    ("Flywheel", "flywheel_version"),
    ("Ponder", "ponder_version"),
    ("Sable", "sable_version"),
    ("JEI", "jei_version"),
    ("Java", "java_version"),
]
# Gap allowed between a dependency's name and its version: spaces, table bars, colons, backticks, bold marks, "version".
VERSION_AFTER = r"(?:[\s|:`*]|version|v(?=\d))*(\d+(?:\.\d+)*(?:\+[0-9A-Za-z.]+)?)"
REGISTRY_ID = re.compile(MOD_ID + r":([a-z0-9_/.]+)")
BACKTICK = re.compile(r"`([^`\n]+)`")
CONFIG_KEY = re.compile(r"^[a-z0-9_]+(?:\.[a-z0-9_]+)+$")
FILE_NAME = re.compile(r"\.(?:png|md|json|toml|py|java|jar|nbt|ogg|bbmodel|gradle|yml|txt)$")
# *Name* or _Name_, not **bold**, not inside a word (snake_case) and not a list bullet.
ITALIC = re.compile(r"(?<![*\w])\*(?!\*)([^*\n]+?)(?<!\s)\*(?!\*)|(?<![_\w])_(?!_)([^_\n]+?)(?<!\s)_(?![_\w])")


def load_lang(path=LANG):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def release_version(props, environ=os.environ):
    """The version whose notes are checked: VERSION from the release workflow, else gradle.properties' mod_version."""
    return environ.get("VERSION") or props.get("mod_version", "")


def default_files(props, environ=os.environ, root=ROOT):
    """The store page, the README and, when it exists, the notes file the workflow publishes for this version."""
    notes = NOTES.format(version=release_version(props, environ))
    return DEFAULT_FILES + [notes] if (root / notes).exists() else list(DEFAULT_FILES)


def load_properties(path=GRADLE_PROPERTIES):
    props = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


class Facts:
    """What the texts are checked against, taken from the lang file and gradle.properties."""

    def __init__(self, lang, props):
        prefix = re.compile(r"^(item|block|entity|effect)\." + MOD_ID + r"\.([a-z0-9_/.]+)$")
        self.registry_ids = set()
        self.names = set()
        for key, value in lang.items():
            match = prefix.match(key)
            if match:
                self.registry_ids.add(match[2])
                self.names.add(value.casefold())
        config_prefix = MOD_ID + ".configuration."
        self.config_keys = {k[len(config_prefix):] for k in lang if k.startswith(config_prefix)
                            and not k.endswith(".tooltip")}
        self.config_sections = {k for k in self.config_keys if "." not in k}
        self.family_nouns = family_nouns(self.names)
        self.props = props

    def is_name(self, text):
        text = text.strip().casefold()
        text = re.sub(r"^(the|a|an) ", "", text)
        candidates = {text, text.removesuffix("s"), text.removesuffix("es")}
        return any(c in self.names or c in self.family_nouns for c in candidates)


FAMILY_MIN_WOODS = 5


def family_nouns(names):
    """The nouns of the wood families (WOOD1): "helm" is a name when "oak helm", "spruce helm", ... exist. A suffix
    after a space counts when at least FAMILY_MIN_WOODS distinct prefixes carry it, so a one-off like "jolly roger
    flag" does not make "flag" a name."""
    prefixes = {}
    for name in names:
        words = name.split(" ")
        for cut in range(1, len(words)):
            prefixes.setdefault(" ".join(words[cut:]), set()).add(" ".join(words[:cut]))
    return {noun for noun, heads in prefixes.items() if len(heads) >= FAMILY_MIN_WOODS}


def check_text(name, text, facts):
    """Problems found in one file, as "name:line: message" strings."""
    problems = []
    is_notes = name.endswith("-notes.md")
    seen_dependencies = set()
    for number, line in enumerate(text.splitlines(), 1):
        where = f"{name}:{number}"
        for match in REGISTRY_ID.finditer(line):
            path = match[1].rstrip(".")
            if path not in facts.registry_ids:
                problems.append(f"{where}: {MOD_ID}:{path} is no item, block, entity or effect in the generated lang")
        for match in BACKTICK.finditer(line):
            token = match[1].strip()
            if CONFIG_KEY.match(token) and not FILE_NAME.search(token) \
                    and token.split(".")[0] in facts.config_sections \
                    and token not in facts.config_keys:
                problems.append(f"{where}: config key `{token}` does not exist (no {MOD_ID}.configuration.{token})")
        for match in ITALIC.finditer(BACKTICK.sub(" ", line)):
            span = match[1] or match[2]
            if not facts.is_name(span):
                problems.append(f"{where}: *{span}* is in italics but is no item, block, mob or effect name "
                                f"(italics are for in-game names; use bold for emphasis)")
        rest = line
        for dependency, key in DEPENDENCIES:
            pattern = re.compile(r"(?<![\w-])" + re.escape(dependency) + r"(?![\w-])" + VERSION_AFTER)
            for match in pattern.finditer(rest):
                seen_dependencies.add(dependency)
                stated, expected = match[1], facts.props.get(key, "")
                if stated not in {expected, expected.split("+")[0]}:
                    problems.append(f"{where}: {dependency} {stated} differs from gradle.properties "
                                    f"{key}={expected}")
            # Blank out what this dependency matched so a shorter name ("Fabric" in "Fabric API") cannot re-match it.
            rest = re.sub(r"(?<![\w-])" + re.escape(dependency) + r"(?![\w-])", " " * len(dependency), rest)
    if is_notes:
        for dependency, _ in DEPENDENCIES:
            if dependency not in seen_dependencies:
                problems.append(f"{name}: the release notes do not name {dependency} with its version")
    return problems


def self_test():
    lang = {
        "item.pirates_n_ships.sail_winch": "Sail Winch",
        "block.pirates_n_ships.bilge_pump": "Bilge Pump",
        "entity.pirates_n_ships.kraken": "Kraken",
        **{f"block.pirates_n_ships.{w}_helm": f"{w.replace('_', ' ').title()} Helm"
           for w in ("oak", "spruce", "birch", "jungle", "dark_oak")},
        "item.pirates_n_ships.jolly_roger_flag": "Jolly Roger Flag",
        "pirates_n_ships.configuration.almanac": "Almanac",
        "pirates_n_ships.configuration.almanac.give_on_first_join": "Gift",
        "pirates_n_ships.configuration.almanac.give_on_first_join.tooltip": "…",
    }
    props = {key: "1.0.0" for _, key in DEPENDENCIES}
    props.update(sable_version="2.0.6", pal_version="1.1.6+mc.1.21.1", fabric_version="0.116.12+1.21.1",
                 minecraft_version="1.21.1", java_version="21")
    facts = Facts(lang, props)
    good = ("Use the *Sail Winch* and two *Bilge Pumps*; beware the _Kraken_. A *Helm* or two *Helms*, a *Dark Oak Helm*.\n"
            "`pirates_n_ships:sail_winch`, `almanac.give_on_first_join`, `en_us.json`, `/pirates wind set`.\n"
            "Sable 2.0.6 | Player Animation Library | 1.1.6 | Fabric API 0.116.12+1.21.1, Minecraft 1.21.1, Java 21\n"
            "snake_case_word and **bold** and a * bullet, `boarding.png`, `net.foo.*` or `net.bar.*`.\n")
    assert check_text("store.md", good, facts) == [], check_text("store.md", good, facts)
    bad = ("A *Sea Serpent* and `pirates_n_ships:sea_serpent` and `almanac.give_on_join`; an *Oak* and a *Flag*.\n"
           "Sable 2.0.5, Fabric API 0.115.0, GeckoLib: 4.9.2\n")
    problems = check_text("store.md", bad, facts)
    expected = ["sea_serpent is no item", "*Sea Serpent* is in italics", "*Oak* is in italics", "*Flag* is in italics",
                "`almanac.give_on_join` does not exist",
                "Sable 2.0.5 differs", "Fabric API 0.115.0 differs", "GeckoLib 4.9.2 differs"]
    for fragment in expected:
        assert any(fragment in p for p in problems), f"missed {fragment!r}: {problems}"
    assert len(problems) == len(expected), problems
    notes = check_text("0.1.0-notes.md", "Sable 2.0.6 only\n", facts)
    assert any("do not name GeckoLib" in p for p in notes) and not any("name Sable" in p for p in notes), notes
    assert release_version({"mod_version": "0.1.0"}, {}) == "0.1.0"
    assert release_version({"mod_version": "0.1.0"}, {"VERSION": "0.2.0-beta.1"}) == "0.2.0-beta.1"
    assert NOTES.format(version="0.1.0") == "docs/release/0.1.0-notes.md"
    print("check_release_texts self-test passed")


def main(argv):
    if "--self-test" in argv:
        self_test()
        return 0
    unknown = [a for a in argv if a.startswith("--")]
    if unknown:
        sys.exit(f"unknown option(s): {' '.join(unknown)}\n{__doc__}")
    props = load_properties()
    files = argv or default_files(props)
    if not argv and len(files) == len(DEFAULT_FILES):
        print(f"no release notes for {release_version(props)} ({NOTES.format(version=release_version(props))})")
    facts = Facts(load_lang(), props)
    problems = []
    for name in files:
        path = Path(name) if Path(name).is_absolute() else ROOT / name
        if not path.exists():
            problems.append(f"{name}: file not found")
            continue
        problems += check_text(name, path.read_text(encoding="utf-8"), facts)
    for problem in problems:
        print(problem)
    print(f"{len(files)} file(s) ({', '.join(files)}) checked against {len(facts.registry_ids)} registry ids, "
          f"{len(facts.config_keys)} config keys and gradle.properties: "
          + (f"{len(problems)} problem(s)" if problems else "all good"))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
