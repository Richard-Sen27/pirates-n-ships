"""Cut a box out of a saved Minecraft 1.21.1 world as a Sponge v2 schematic (STR2), to render a structure the way a
real world generated it (terrain, beard, coastline) with ``tools/render_generated_structure.py``.

Usage:
    python3 tools/world_region.py WORLD_DIR X0 Z0 X1 Z1 --out FILE.schem [--y0 40] [--y1 100] [--dimension overworld]
    python3 tools/world_region.py WORLD_DIR X0 Z0 X1 Z1 --doors     # STR8: list the blocked doors in the box

``WORLD_DIR`` is a world folder (with ``region/*.mca``; e.g. the dedicated-server smoke test's
``neoforge/build/server-smoke/world`` after ``tools/server_smoke.sh neoforge``, which generates a pirate island, a
village and an outpost round the spots ``/locate`` reports and lists them under "pirates world ports" in
``build/server-smoke/neoforge/commands.txt``). The box is in block coordinates, both corners included; chunks that
were never generated (or not fully) come out as air. Blocks only (no block entities). Reads the Anvil region format
(zlib or gzip chunks, ``sections`` with paletted ``block_states``); standard library only.
"""
from __future__ import annotations

import argparse
import gzip
import struct
import sys
import zlib
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import schem_to_structure as nbt  # noqa: E402  (same folder: the NBT reader and writer)

AIR = "minecraft:air"


def state_string(entry) -> str:
    """A palette entry (``Name`` and ``Properties``) as ``ns:block[k=v,...]``."""
    name = entry.value["Name"].value
    props = entry.value.get("Properties")
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v.value}" for k, v in sorted(props.value.items())) + "]"


class Region:
    """One ``r.X.Z.mca`` file, chunks decoded on demand."""

    def __init__(self, path: Path):
        self.data = path.read_bytes() if path.exists() else b""

    def chunk(self, cx: int, cz: int):
        if not self.data:
            return None
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        (loc,) = struct.unpack(">I", self.data[i:i + 4])
        offset, sectors = loc >> 8, loc & 0xFF
        if offset == 0 or sectors == 0:
            return None
        start = offset * 4096
        (length,) = struct.unpack(">I", self.data[start:start + 4])
        kind = self.data[start + 4]
        raw = self.data[start + 5:start + 4 + length]
        if kind == 2:
            raw = zlib.decompress(raw)
        elif kind == 1:
            raw = gzip.decompress(raw)
        elif kind != 3:
            return None
        return nbt.read_nbt(raw)


def sections(chunk) -> dict[int, tuple[list[str], list[int]]]:
    """Section y -> (palette, 4096 palette indices in y, z, x order) of a chunk, generated far enough to have blocks."""
    out = {}
    status = chunk.value.get("Status")
    if status is not None and status.value.split(":")[-1] not in ("full", "features", "initialize_light", "light",
                                                                    "spawn"):
        return out
    for sec in chunk.value.get("sections", nbt.Tag(nbt.LIST, [])).value:
        states = sec.value.get("block_states")
        if states is None:
            continue
        palette = [state_string(e) for e in states.value["palette"].value]
        data = states.value.get("data")
        if data is None or len(palette) == 1:
            out[sec.value["Y"].value] = (palette, [0] * 4096)
            continue
        bits = max(4, (len(palette) - 1).bit_length())
        per_long = 64 // bits
        mask = (1 << bits) - 1
        idx = []
        for word in data.value:
            w = word & 0xFFFFFFFFFFFFFFFF
            for k in range(per_long):
                if len(idx) == 4096:
                    break
                idx.append((w >> (k * bits)) & mask)
        out[sec.value["Y"].value] = (palette, idx)
    return out


def extract(world: Path, x0: int, z0: int, x1: int, z1: int, y0: int, y1: int, dimension: str = "overworld"):
    region_dir = world / "region" if dimension == "overworld" else world / dimension / "region"
    regions: dict[tuple[int, int], Region] = {}
    width, height, length = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    grid = [AIR] * (width * height * length)
    for cz in range(z0 >> 4, (z1 >> 4) + 1):
        for cx in range(x0 >> 4, (x1 >> 4) + 1):
            key = (cx >> 5, cz >> 5)
            if key not in regions:
                regions[key] = Region(region_dir / f"r.{key[0]}.{key[1]}.mca")
            chunk = regions[key].chunk(cx, cz)
            if chunk is None:
                continue
            for sy, (palette, idx) in sections(chunk).items():
                for ly in range(16):
                    y = sy * 16 + ly
                    if not y0 <= y <= y1:
                        continue
                    for lz in range(16):
                        z = cz * 16 + lz
                        if not z0 <= z <= z1:
                            continue
                        base = ly * 256 + lz * 16
                        for lx in range(16):
                            x = cx * 16 + lx
                            if x0 <= x <= x1:
                                grid[(x - x0) + (z - z0) * width + (y - y0) * width * length] = palette[idx[base + lx]]
    return grid, (width, height, length)


def parse_state(state: str) -> tuple[str, dict]:
    """``ns:block[k=v,...]`` as (name, properties)."""
    name, _, rest = state.partition("[")
    return name, dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if "=" in kv)


def blocked_doors(grid: list[str], size: tuple[int, int, int], origin: tuple[int, int, int]) -> tuple[list[str], int]:
    """STR8: the doors (lower halves) of a cut-out region whose approach is blocked, by the rule
    ``tools/check_structures.py`` applies to the templates (both sides, the door's two rows, a bottom slab a step).
    Returns the problems (world coordinates) and the number of doors."""
    import check_structures  # noqa: E402  (same folder)
    width, height, length = size

    def at(x, y, z):
        if not (0 <= x < width and 0 <= y < height and 0 <= z < length):
            return None, {}
        return parse_state(grid[x + z * width + y * width * length])

    problems = []
    doors = 0
    for y in range(height):
        for z in range(length):
            for x in range(width):
                name, props = at(x, y, z)
                if not name.endswith("_door") or props.get("half") != "lower" or "facing" not in props:
                    continue
                doors += 1
                dx, dz = check_structures.DOOR_SIDES[props["facing"]]
                for sign in (1, -1):
                    cx, cz = x + dx * sign, z + dz * sign
                    rows = (y, y + 1)
                    if check_structures.is_step(*at(cx, y, cz)):
                        rows = (y + 1, y + 2)
                    blocked = [(r, at(cx, r, cz)[0]) for r in rows if not check_structures.passable(*at(cx, r, cz))]
                    if blocked:
                        what = ", ".join(f"{n} at y {r + origin[1]}" for r, n in blocked)
                        problems.append(f"{name} at {x + origin[0]} {y + origin[1]} {z + origin[2]} (facing {props['facing']}): "
                                        f"the cell {cx + origin[0]} {cz + origin[2]} is blocked ({what})")
    return problems, doors


def to_schem(grid: list[str], size: tuple[int, int, int], origin: tuple[int, int, int]) -> bytes:
    palette: dict[str, int] = {}
    data = bytearray()
    for state in grid:
        i = palette.setdefault(state, len(palette))
        while True:
            b = i & 0x7F
            i >>= 7
            data.append(b | (0x80 if i else 0))
            if not i:
                break
    width, height, length = size
    root = nbt.compound(
        Version=nbt.Tag(nbt.INT, 2),
        DataVersion=nbt.Tag(nbt.INT, nbt.DATA_VERSION_1_21_1),
        Width=nbt.Tag(nbt.SHORT, width),
        Height=nbt.Tag(nbt.SHORT, height),
        Length=nbt.Tag(nbt.SHORT, length),
        Offset=nbt.Tag(nbt.INT_ARRAY, list(origin)),
        PaletteMax=nbt.Tag(nbt.INT, len(palette)),
        Palette=nbt.Tag(nbt.COMPOUND, {k: nbt.Tag(nbt.INT, v) for k, v in palette.items()}),
        BlockData=nbt.Tag(nbt.BYTE_ARRAY, bytes(data)),
    )
    return nbt.write_nbt(root)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("world", type=Path)
    parser.add_argument("box", type=int, nargs=4, metavar=("X0", "Z0", "X1", "Z1"))
    parser.add_argument("--y0", type=int, default=40)
    parser.add_argument("--y1", type=int, default=100)
    parser.add_argument("--dimension", default="overworld", help="overworld, or a folder like DIM-1")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--doors", action="store_true",
                        help="STR8: list every door in the box whose approach (either side, its two rows) is blocked; exit 1 if any")
    args = parser.parse_args(argv)
    if args.out is None and not args.doors:
        parser.error("--out or --doors is required")
    x0, z0, x1, z1 = args.box
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    grid, size = extract(args.world, x0, z0, x1, z1, args.y0, args.y1, args.dimension)
    if args.doors:
        problems, doors = blocked_doors(grid, size, (x0, args.y0, z0))
        for p in problems:
            print(p)
        print(f"{doors} doors, {len(problems)} blocked sides")
        if args.out is None:
            return 1 if problems else 0
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(to_schem(grid, size, (x0, args.y0, z0)))
    filled = sum(1 for s in grid if s != AIR)
    print(f"{args.out}: {size[0]}x{size[1]}x{size[2]}, {filled} blocks")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
