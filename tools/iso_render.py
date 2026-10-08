"""Shape-aware isometric renderer for structure schematics, for the four-corner review of a piece.

Usage:
    python3 tools/iso_render.py art/schematics/structures/village/tavern.schem --out DIR [--scale 20] [--cut Y]

Writes ``DIR/<piece>_{se,ne,nw,sw}.png``: the piece seen from each corner (the label is the corner the viewer stands
at). Unlike the schematic lab's preview (whole cubes coloured by block name, a colour check only), it draws slabs,
stairs, open and closed trapdoors, doors, ladders, glass panes, fences, walls, lanterns, beds, carpets and pressure
plates as their real shapes, in rough Minecraft colours, so relief (sills, shutters, rafter ends, plinths) shows.
Everything else is drawn as a full cube; jigsaws as small magenta cubes. ``--cut Y`` leaves out rows above Y (to look
inside). Reads Sponge v2/v3 schematics through ``tools/schem_to_structure.py``; standard library only (the PNG is
written by hand, no Pillow needed).
"""
from __future__ import annotations

import argparse
import re
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from schem_to_structure import read_nbt, read_schematic  # noqa: E402

COLOURS = {
    "calcite": "dfe0dc", "cobblestone": "7f7f7f", "mossy_cobblestone": "64764f", "stone_bricks": "7a7979",
    "stone_brick": "7a7979", "mossy_stone_brick": "6b7860", "mossy_stone_bricks": "6b7860",
    "cracked_stone_bricks": "6f6e6d", "chiseled_stone_bricks": "8a8988", "polished_andesite": "8a8c8c",
    "spruce": "735531", "oak": "a2834f", "dark_oak": "4a3018", "stripped_spruce_log": "8a6a40",
    "stripped_oak_log": "c4a46a", "stripped_dark_oak_log": "4a3420", "spruce_log": "3a2614", "oak_log": "6b5530",
    "dark_oak_log": "3c2a14", "bricks": "a05a48", "brick": "a05a48", "glass_pane": "b8dcec", "glass": "b8dcec",
    "gravel": "837f7e", "dirt_path": "947a41", "coarse_dirt": "77553b", "dirt": "86603e", "grass_block": "5d8a3a",
    "sand": "dbcfa0", "smooth_sandstone": "e0d6aa", "sandstone": "d8cc98", "lantern": "f0b040", "torch": "ffd060",
    "barrel": "8a6538", "chest": "a36f23", "crafting_table": "a0703f", "bookshelf": "6e5434", "furnace": "5e5e5e",
    "smoker": "5a5050", "lectern": "b08a54", "smithing_table": "3a3a44", "cartography_table": "6a4a30",
    "grindstone": "8a8a8a", "cauldron": "3c3c3c", "anvil": "444444", "flower_pot": "7c4535", "ladder": "7c6236",
    "red_carpet": "a12722", "red_bed": "a12722", "blue_bed": "2d48a0", "white_wool": "eeeeee", "gray_wool": "8a8a8a",
    "brown_wool": "6e4a2a", "blue_wool": "2d48a0", "hay_block": "c8a02a", "cobweb": "e8e8e8", "chain": "333344",
    "iron_bars": "6a6a6a", "andesite": "888888", "water": "3f76e4", "jigsaw": "ff00ff",
    "harbor_desk": "5b3a20", "notice_board": "c9a86a", "cargo_crate": "b08a50", "cargo_barrel": "6e4a28",
    "mooring_ring": "404040", "cleat": "505050", "sea_chest": "6a4020", "map_tile": "d8c8a0", "flagpole": "8a6a40",
    "cannon": "303030", "brig_bars": "5a5a5a", "brig_door": "5a5a5a",
}
KEYS = sorted(COLOURS, key=len, reverse=True)
TURN = {"north": "east", "east": "south", "south": "west", "west": "north"}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
SKIP = {"minecraft:air", "minecraft:cave_air", "minecraft:structure_void"}


def colour(state: str) -> tuple[int, int, int]:
    name = state.split(":", 1)[-1].split("[", 1)[0]
    hexa = COLOURS.get(name) or next((COLOURS[k] for k in KEYS if name.startswith(k)), None) \
        or next((COLOURS[k] for k in KEYS if k in name), None)
    if hexa is None:
        hexa = f"{sum(map(ord, name)) * 2654435761 % 0xFFFFFF:06x}"
    return tuple(int(hexa[i:i + 2], 16) for i in (0, 2, 4))


def props(state: str) -> dict[str, str]:
    m = re.search(r"\[(.*)\]", state)
    return dict(kv.split("=", 1) for kv in m.group(1).split(",")) if m else {}


def turned(state: str, k: int) -> str:
    """The state turned clockwise (seen from above) k times: facings, jigsaw orientations and log axes."""
    p = props(state)
    if k == 0 or not p:
        return state
    for _ in range(k):
        if p.get("facing") in TURN:
            p["facing"] = TURN[p["facing"]]
        if "orientation" in p:
            front, up = p["orientation"].split("_", 1)
            if front in TURN:
                p["orientation"] = f"{TURN[front]}_{up}"
        if p.get("axis") in ("x", "z"):
            p["axis"] = "z" if p["axis"] == "x" else "x"
    return state.split("[", 1)[0] + "[" + ",".join(f"{a}={b}" for a, b in p.items()) + "]"


def _half(facing):
    return {"north": (0, 16, 0, 8), "south": (0, 16, 8, 16), "east": (8, 16, 0, 16), "west": (0, 8, 0, 16)}[facing]


def _plate(side, t):
    return {"north": (0, 16, 0, t), "south": (0, 16, 16 - t, 16), "east": (16 - t, 16, 0, 16),
            "west": (0, t, 0, 16)}[side]


def shape(state: str, pane_along: str) -> list[tuple[int, int, int, int, int, int]]:
    """Boxes (x0, x1, y0, y1, z0, z1) in sixteenths of a block."""
    name = state.split(":", 1)[-1].split("[", 1)[0]
    p = props(state)
    if name.endswith("_slab"):
        return {"bottom": [(0, 16, 0, 8, 0, 16)], "top": [(0, 16, 8, 16, 0, 16)]}.get(p.get("type"), [(0, 16, 0, 16, 0, 16)])
    if name.endswith("_stairs"):
        x0, x1, z0, z1 = _half(p.get("facing", "north"))
        if p.get("half") == "top":
            return [(0, 16, 8, 16, 0, 16), (x0, x1, 0, 8, z0, z1)]
        return [(0, 16, 0, 8, 0, 16), (x0, x1, 8, 16, z0, z1)]
    if name.endswith("_trapdoor"):
        if p.get("open") == "true":
            x0, x1, z0, z1 = _plate(OPPOSITE[p.get("facing", "north")], 3)
            return [(x0, x1, 0, 16, z0, z1)]
        return [(0, 16, 13, 16, 0, 16)] if p.get("half") == "top" else [(0, 16, 0, 3, 0, 16)]
    if name.endswith("_door") or name == "ladder" or name.endswith("wall_banner"):
        x0, x1, z0, z1 = _plate(OPPOSITE[p.get("facing", "north")], 3 if name.endswith("_door") else 2)
        return [(x0, x1, 0, 16, z0, z1)]
    if name.endswith("_fence") or name in ("iron_bars", "chain"):
        return [(6, 10, 0, 16, 6, 10)]
    if name.endswith("_fence_gate"):
        return [(0, 16, 5, 15, 7, 9)] if p.get("facing") in ("north", "south") else [(7, 9, 5, 15, 0, 16)]
    if name.endswith("_wall"):
        return [(4, 12, 0, 16, 4, 12)]
    if name.endswith("glass_pane"):
        return [(0, 16, 0, 16, 7, 9)] if pane_along == "x" else [(7, 9, 0, 16, 0, 16)]
    if name == "lantern":
        return [(5, 11, 1, 10, 5, 11)] if p.get("hanging") == "true" else [(5, 11, 0, 9, 5, 11)]
    if name.endswith("_bed"):
        return [(0, 16, 0, 9, 0, 16)]
    if name.endswith("_carpet") or name.endswith("pressure_plate"):
        return [(1, 15, 0, 1, 1, 15)]
    if name == "flower_pot" or name.startswith("potted"):
        return [(5, 11, 0, 6, 5, 11)]
    if name in ("mooring_ring", "cleat", "torch", "candle", "tripwire_hook"):
        return [(5, 11, 0, 4, 5, 11)]
    if name == "lectern":
        return [(0, 16, 0, 14, 0, 16)]
    if name == "jigsaw":
        return [(5, 11, 5, 11, 5, 11)]
    if name in ("cobweb", "fern", "short_grass", "dead_bush", "lily_pad"):
        return [(3, 13, 0, 10, 3, 13)]
    return [(0, 16, 0, 16, 0, 16)]


def png(width: int, height: int, rgb: bytearray) -> bytes:
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes(rgb[r * width * 3:(r + 1) * width * 3]) for r in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def render(states: dict, size: tuple[int, int, int], k: int, scale: int, cut: int | None) -> bytes:
    """The piece turned clockwise k times, seen from the south-east (so k = 0, 1, 2, 3 shows the SE, NE, NW, SW
    corners of the original), painter's order, flat shading per face."""
    sx, _, sz = size
    blocks = {}
    for (x, y, z), state in states.items():
        if state.split("[", 1)[0] in SKIP or (cut is not None and y > cut):
            continue
        X, Z, depth = x, z, sz
        for i in range(k):
            X, Z = depth - 1 - Z, X
            depth = sx if i % 2 == 0 else sz
        blocks[(X, y, Z)] = turned(state, k)
    hw, hh, vh = scale * 0.866, scale * 0.5, scale
    boxes = []
    for (x, y, z), state in blocks.items():
        along = "x" if (x - 1, y, z) in blocks or (x + 1, y, z) in blocks else "z"
        rgb = colour(state)
        for x0, x1, y0, y1, z0, z1 in shape(state, along):
            boxes.append((x + x0 / 16, x + x1 / 16, y + y0 / 16, y + y1 / 16, z + z0 / 16, z + z1 / 16, rgb))
    if not boxes:
        return png(1, 1, bytearray(3))

    def proj(x, y, z):
        return (x - z) * hw, (x + z) * hh - y * vh

    corners = [proj(b[i], b[j], b[m]) for b in boxes for i in (0, 1) for j in (2, 3) for m in (4, 5)]
    minx, miny = min(c[0] for c in corners) - 10, min(c[1] for c in corners) - 10
    width = int(max(c[0] for c in corners) + 10 - minx) + 1
    height = int(max(c[1] for c in corners) + 10 - miny) + 1
    img = bytearray(bytes((24, 28, 34)) * (width * height))

    def fill(points, rgb):
        pts = [(px - minx, py - miny) for px, py in points]
        ys = [p[1] for p in pts]
        for row in range(max(0, int(min(ys))), min(height, int(max(ys)) + 1)):
            yc = row + 0.5
            xs = sorted(ax + (yc - ay) * (bx - ax) / (by - ay)
                        for (ax, ay), (bx, by) in zip(pts, pts[1:] + pts[:1]) if (ay <= yc < by) or (by <= yc < ay))
            if len(xs) >= 2:
                a, b = max(0, int(round(xs[0]))), min(width, int(round(xs[-1])))
                if b > a:
                    img[(row * width + a) * 3:(row * width + b) * 3] = bytes(rgb) * (b - a)

    boxes.sort(key=lambda b: (b[1] + b[5] + b[3], b[0] + b[4] + b[2]))
    for x0, x1, y0, y1, z0, z1, rgb in boxes:
        shade = lambda f: tuple(min(255, int(c * f)) for c in rgb)  # noqa: E731
        fill([proj(x0, y0, z1), proj(x1, y0, z1), proj(x1, y1, z1), proj(x0, y1, z1)], shade(0.8))   # south
        fill([proj(x1, y0, z0), proj(x1, y0, z1), proj(x1, y1, z1), proj(x1, y1, z0)], shade(0.62))  # east
        fill([proj(x0, y1, z0), proj(x1, y1, z0), proj(x1, y1, z1), proj(x0, y1, z1)], shade(1.0))   # top
    return png(width, height, img)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("schem", type=Path, nargs="+", help="Sponge schematic(s) (.schem)")
    parser.add_argument("--out", type=Path, required=True, help="directory for <piece>_{se,ne,nw,sw}.png")
    parser.add_argument("--scale", type=int, default=20, help="pixels per block edge (default 20)")
    parser.add_argument("--cut", type=int, default=None, help="leave out rows above this y (look inside)")
    args = parser.parse_args(argv)
    args.out.mkdir(parents=True, exist_ok=True)
    for source in args.schem:
        schem = read_schematic(read_nbt(source.read_bytes()))
        for k, corner in enumerate(("se", "ne", "nw", "sw")):
            target = args.out / f"{source.stem}_{corner}.png"
            target.write_bytes(render(schem["states"], schem["size"], k, args.scale, args.cut))
            print(target)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
