#!/usr/bin/env python3
"""Re-verifies the compat wood table (WOOD1, docs/woods.md) against the mods' real jars and prints a report.

The table lives in common/src/main/java/com/richardsenger/piratesnships/ship/decor/wood/WoodCompat.java: one
`static final Mod X = new Mod("modid", "Name", "slug", "neoforge,fabric");` per mod and one `w(X, "name", MapColor.C)`
row per wood, optionally followed on the same line by overrides (`.stem()`, `.dir()`, `.planksTex("...")`,
`.logTex("...")`, `.topTex("...")`, `.log("...")`, `.strippedLog("...")`, `.named("...")`, `.sound(Sound.X)`,
`.fireproof()`). This script derives every id and texture exactly as WoodCompat.Row does and checks, in the jar of
every loader the mod is listed for:

- the mod id in the jar's metadata (fabric.mod.json or META-INF/neoforge.mods.toml),
- the planks, stripped-log and stripped-log-top textures (assets/<ns>/textures/<path>.png),
- the planks, log and stripped-log blocks (assets/<ns>/blockstates/<id>.json) and the planks item model,
- the map colour: the planks texture's mean colour's nearest vanilla map colour must be the row's.

Jars are read as zip files only (names, metadata text, the planks PNG); nothing in them is run. Layout of the jar
folder: <folder>/<slug>/<loader>/<file>.jar (one jar per folder). Usage:

    python3 tools/check_wood_compat.py <jar folder> [--markdown docs/wood-compat.md]

Exit status 1 when anything does not match.
"""
import argparse
import json
import re
import struct
import sys
import zipfile
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
JAVA = REPO / "common/src/main/java/com/richardsenger/piratesnships/ship/decor/wood/WoodCompat.java"

MOD_RE = re.compile(r'static final Mod (\w+) = new Mod\("([a-z0-9_]+)", "([^"]+)", "([a-z0-9-]+)", "([a-z,]+)"\);')
ROW_RE = re.compile(r'^\s*w\((\w+), "([a-z0-9_]+)", MapColor\.([A-Z_]+)\)(.*?)[,;]?\s*(//.*)?$')
CALL_RE = re.compile(r'\.(\w+)\(([^)]*)\)')

# vanilla 1.21.1 MapColor ids and colours (net.minecraft.world.level.material.MapColor), NONE left out
MAP_COLORS = {
    "GRASS": 8368696, "SAND": 16247203, "WOOL": 13092807, "FIRE": 16711680, "ICE": 10526975, "METAL": 10987431,
    "PLANT": 31744, "SNOW": 16777215, "CLAY": 10791096, "DIRT": 9923917, "STONE": 7368816, "WATER": 4210943,
    "WOOD": 9402184, "QUARTZ": 16776437, "COLOR_ORANGE": 14188339, "COLOR_MAGENTA": 11685080,
    "COLOR_LIGHT_BLUE": 6724056, "COLOR_YELLOW": 15066419, "COLOR_LIGHT_GREEN": 8375321, "COLOR_PINK": 15892389,
    "COLOR_GRAY": 5000268, "COLOR_LIGHT_GRAY": 10066329, "COLOR_CYAN": 5013401, "COLOR_PURPLE": 8339378,
    "COLOR_BLUE": 3361970, "COLOR_BROWN": 6704179, "COLOR_GREEN": 6717235, "COLOR_RED": 10040115,
    "COLOR_BLACK": 1644825, "GOLD": 16445005, "DIAMOND": 6085589, "LAPIS": 4882687, "EMERALD": 55610,
    "PODZOL": 8476209, "NETHER": 7340544, "TERRACOTTA_WHITE": 13742497, "TERRACOTTA_ORANGE": 10441252,
    "TERRACOTTA_MAGENTA": 9787244, "TERRACOTTA_LIGHT_BLUE": 7367818, "TERRACOTTA_YELLOW": 12223780,
    "TERRACOTTA_LIGHT_GREEN": 6780213, "TERRACOTTA_PINK": 10505550, "TERRACOTTA_GRAY": 3746083,
    "TERRACOTTA_LIGHT_GRAY": 8874850, "TERRACOTTA_CYAN": 5725276, "TERRACOTTA_PURPLE": 8014168,
    "TERRACOTTA_BLUE": 4996700, "TERRACOTTA_BROWN": 4993571, "TERRACOTTA_GREEN": 5001770, "TERRACOTTA_RED": 9321518,
    "TERRACOTTA_BLACK": 2430480, "CRIMSON_NYLIUM": 12398641, "CRIMSON_STEM": 9715553, "CRIMSON_HYPHAE": 6035741,
    "WARPED_NYLIUM": 1474182, "WARPED_STEM": 3837580, "WARPED_HYPHAE": 5647422, "WARPED_WART_BLOCK": 1356933,
    "DEEPSLATE": 6579300, "RAW_IRON": 14200723, "GLOW_LICHEN": 8365974,
}


def parse_table(text):
    """The mods (by Java constant) and the rows of WoodCompat.java, ids and textures derived like WoodCompat.Row."""
    mods = {}
    for m in MOD_RE.finditer(text):
        const, modid, name, slug, loaders = m.groups()
        mods[const] = {"id": modid, "name": name, "slug": slug, "loaders": loaders.split(",")}
    rows = []
    for line in text.splitlines():
        m = ROW_RE.match(line)
        if not m:
            continue
        const, name, color, rest, _ = m.groups()
        mod = mods[const]
        ns = mod["id"]
        calls = {c: a.strip().strip('"') for c, a in CALL_RE.findall(rest)}
        kind = "stem" if "stem" in calls else "log"
        if "dir" in calls:
            planks_tex = f"block/{name}/planks"
            log_tex = f"block/{name}/stripped_{kind}"
        else:
            planks_tex = f"block/{name}_planks"
            log_tex = f"block/stripped_{name}_{kind}"
        planks_tex = calls.get("planksTex", planks_tex)
        log_tex = calls.get("logTex", log_tex)
        top_tex = calls.get("topTex", log_tex + "_top")
        rows.append({
            "mod": mod, "name": name, "id": f"{ns}_{name}", "color": color,
            "display": calls.get("named", " ".join(p.capitalize() for p in name.split("_"))),
            "planks_tex": f"{ns}:{planks_tex}", "log_tex": f"{ns}:{log_tex}", "top_tex": f"{ns}:{top_tex}",
            "planks": f"{ns}:{name}_planks",
            "log": f"{ns}:{calls.get('log', f'{name}_{kind}')}",
            "stripped": f"{ns}:{calls.get('strippedLog', f'stripped_{name}_{kind}')}",
        })
    return mods, rows


def split(rl):
    ns, path = rl.split(":", 1)
    return ns, path


def jar_metadata(z):
    """(mod ids, version) from the jar's loader metadata."""
    names = set(z.namelist())
    if "fabric.mod.json" in names:
        d = json.loads(z.read("fabric.mod.json").decode("utf-8"))
        return {d["id"]}, d.get("version", "?")
    if "META-INF/neoforge.mods.toml" in names:
        toml = z.read("META-INF/neoforge.mods.toml").decode("utf-8")
        ids = set(re.findall(r'^\s*modId\s*=\s*"([^"]+)"', toml, re.M))
        versions = re.findall(r'^\s*version\s*=\s*"([^"]+)"', toml, re.M)
        return ids, versions[0] if versions else "?"
    return set(), "?"


def png_mean(data):
    """Mean RGB of the opaque pixels of an 8-bit (or palette) non-interlaced PNG."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, palette, trns = 8, b"", None, None
    width = height = depth = ctype = 0
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            if interlace:
                raise ValueError("interlaced PNG")
        elif kind == b"PLTE":
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif kind == b"tRNS":
            trns = chunk
        elif kind == b"IDAT":
            idat += chunk
        elif kind == b"IEND":
            break
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    if depth != 8 and not (ctype == 3 and depth in (1, 2, 4)):
        raise ValueError(f"bit depth {depth} of colour type {ctype}")
    bits = channels * depth
    stride = (width * bits + 7) // 8
    bpp = max(1, bits // 8)
    raw = zlib.decompress(idat)
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(height):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    total, n = [0, 0, 0], 0
    for line in rows:
        for x in range(width):
            if ctype == 3:
                byte = line[(x * depth) // 8]
                shift = 8 - depth - (x * depth) % 8
                idx = (byte >> shift) & ((1 << depth) - 1)
                rgb, alpha = palette[idx], (trns[idx] if trns and idx < len(trns) else 255)
            else:
                px = line[x * channels:(x + 1) * channels]
                rgb = (px[0],) * 3 if ctype in (0, 4) else tuple(px[:3])
                alpha = px[-1] if ctype in (4, 6) else 255
            if alpha < 128:
                continue
            for k in range(3):
                total[k] += rgb[k]
            n += 1
    return tuple(round(t / max(1, n)) for t in total)


def nearest_map_color(rgb):
    def dist(c):
        r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
        return (r - rgb[0]) ** 2 + (g - rgb[1]) ** 2 + (b - rgb[2]) ** 2
    return min(MAP_COLORS, key=lambda k: dist(MAP_COLORS[k]))


def check(folder, rows, mods):
    problems, report = [], []
    for const, mod in mods.items():
        entry = {"mod": mod, "jars": [], "woods": [r for r in rows if r["mod"] is mod]}
        report.append(entry)
        for loader in mod["loaders"]:
            jars = sorted((folder / mod["slug"] / loader).glob("*.jar"))
            if len(jars) != 1:
                problems.append(f"{mod['name']} ({loader}): expected one jar in {folder / mod['slug'] / loader}, found {len(jars)}")
                continue
            z = zipfile.ZipFile(jars[0])
            names = set(z.namelist())
            ids, version = jar_metadata(z)
            if version.startswith("$"):
                version = jars[0].stem
            entry["jars"].append((loader, jars[0].name, version))
            if mod["id"] not in ids:
                problems.append(f"{mod['name']} ({loader}): mod id {mod['id']} not in the jar's metadata {sorted(ids)}")
            for r in entry["woods"]:
                for key in ("planks_tex", "log_tex", "top_tex"):
                    ns, path = split(r[key])
                    if f"assets/{ns}/textures/{path}.png" not in names:
                        problems.append(f"{r['id']} ({loader}): texture {r[key]} missing")
                for key in ("planks", "log", "stripped"):
                    ns, path = split(r[key])
                    if f"assets/{ns}/blockstates/{path}.json" not in names:
                        problems.append(f"{r['id']} ({loader}): block {r[key]} missing")
                ns, path = split(r["planks"])
                if f"assets/{ns}/models/item/{path}.json" not in names:
                    problems.append(f"{r['id']} ({loader}): item model of {r['planks']} missing")
                ns, path = split(r["planks_tex"])
                tex = f"assets/{ns}/textures/{path}.png"
                if tex in names:
                    try:
                        suggested = nearest_map_color(png_mean(z.read(tex)))
                        r.setdefault("suggested", {})[loader] = suggested
                        if suggested != r["color"]:
                            problems.append(f"{r['id']} ({loader}): map colour {r['color']}, planks look {suggested}")
                    except ValueError as e:
                        problems.append(f"{r['id']} ({loader}): planks PNG unreadable ({e})")
    ids = [r["id"] for r in rows]
    for dup in sorted({i for i in ids if ids.count(i) > 1}):
        problems.append(f"wood id {dup} twice")
    return problems, report


def markdown(report, rows):
    out = ["# Compat woods: verified against the mods' jars", "",
           "Generated by `python3 tools/check_wood_compat.py <jar folder> --markdown docs/wood-compat.md` from the "
           "table in `WoodCompat.java` (WOOD1, see docs/woods.md). Every row was checked in the jar of every loader "
           "listed: the mod id, the planks, stripped-log and stripped-log-top textures, the planks, log and "
           "stripped-log blocks, the planks item, and the map colour (the planks texture's nearest vanilla map colour).",
           "", f"{len(rows)} woods from {len(report)} mods.", "",
           "| Mod | Mod id | Loader | Jar | Version |", "|---|---|---|---|---|"]
    for e in report:
        for loader, jar, version in e["jars"]:
            out.append(f"| {e['mod']['name']} | `{e['mod']['id']}` | {loader} | `{jar}` | {version} |")
    for e in report:
        out += ["", f"## {e['mod']['name']} (`{e['mod']['id']}`, {', '.join(e['mod']['loaders'])})", "",
                "| Wood id | Name | Planks | Planks texture | Stripped log texture | Log, stripped log | Map colour |",
                "|---|---|---|---|---|---|---|"]
        for r in e["woods"]:
            out.append(f"| `{r['id']}` | {r['display']} | `{r['planks']}` | `{r['planks_tex']}` | `{r['log_tex']}` "
                       f"(top `{r['top_tex'].split(':', 1)[1]}`) | `{r['log']}`, `{r['stripped']}` | {r['color']} |")
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("folder", type=Path)
    ap.add_argument("--markdown", type=Path)
    ap.add_argument("--java", type=Path, default=JAVA)
    args = ap.parse_args()
    mods, rows = parse_table(args.java.read_text(encoding="utf-8"))
    if not rows:
        print("no rows parsed from " + str(args.java))
        return 1
    problems, report = check(args.folder, rows, mods)
    for e in report:
        jars = ", ".join(f"{l} {v}" for l, _, v in e["jars"])
        print(f"{e['mod']['name']}: {len(e['woods'])} woods ({jars})")
    print(f"{len(rows)} woods, {len(problems)} problems")
    for p in problems:
        print("  " + p)
    if args.markdown and not problems:
        args.markdown.write_text(markdown(report, rows), encoding="utf-8")
        print("wrote " + str(args.markdown))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
