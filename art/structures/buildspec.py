"""Shared helpers for the structure piece generators (art/structures/<group>/<piece>.py).

A piece is a sparse block map ({(x, y, z): palette key}) plus jigsaw markers. ``Piece.spec()`` turns it into a
BuildSpec for the minecraft-schematic-lab tool (the format of art/schematics/starter_sloop.py): runs of equal blocks
along x become ``box`` operations, every jigsaw becomes a ``block_entity`` operation with ``minecraft:jigsaw`` NBT.
Conventions (art/README.md, "Structures (ST1)"): y 0 is the foundation row, pieces face north (-z), x grows east.
"""
from __future__ import annotations

import json

MOD = "pirates_n_ships"
EMPTY_POOL = "minecraft:empty"

# name of a connector -> (target, pool it pulls from, joint); the *_in side never spawns anything
CONNECTORS = {
    "street_out": ("street_in", f"{MOD}:village/streets", "aligned"),
    "street_in": ("street_out", EMPTY_POOL, "aligned"),
    "building_out": ("building_in", f"{MOD}:village/buildings", "rollable"),
    "building_in": ("building_out", EMPTY_POOL, "rollable"),
    "pier_out": ("pier_in", f"{MOD}:village/pier", "aligned"),
    "pier_in": ("pier_out", EMPTY_POOL, "aligned"),
}

FACINGS = ("north", "south", "east", "west")


def stairs(block: str, facing: str, half: str = "bottom") -> str:
    return f"{block}[facing={facing},half={half}]"


class Piece:
    def __init__(self, piece_id: str, name: str, size: tuple[int, int, int], palette: dict[str, str]):
        self.id = piece_id
        self.name = name
        self.size = size
        self.palette = dict(palette)
        self.blocks: dict[tuple[int, int, int], str] = {}
        self.jigsaws: dict[tuple[int, int, int], dict] = {}

    # ------------------------------------------------------------ blocks

    def _check(self, x, y, z):
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            raise ValueError(f"{self.id}: block at {(x, y, z)} is outside the size {self.size}")

    def key(self, state: str) -> str:
        """Palette key for a literal block state (added to the palette once)."""
        for k, v in self.palette.items():
            if v == state:
                return k
        k = state.split(":", 1)[1].replace("[", "_").replace("]", "").replace("=", "_").replace(",", "_")
        self.palette[k] = state
        return k

    def put(self, x, y, z, key: str):
        """Sets a block by palette key or literal block state (``ns:block[...]``)."""
        self._check(x, y, z)
        if key not in self.palette:
            if ":" not in key:
                raise KeyError(f"{self.id}: unknown palette key {key!r}")
            key = self.key(key)
        self.blocks[(x, y, z)] = key
        self.jigsaws.pop((x, y, z), None)

    def fill(self, x0, y0, z0, x1, y1, z1, key: str):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.put(x, y, z, key)

    def ring(self, x0, z0, x1, z1, y0, y1, key: str):
        """The outline of a rectangle (walls), rows y0..y1."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, z0, key)
                self.put(x, y, z1, key)
            for z in range(z0, z1 + 1):
                self.put(x0, y, z, key)
                self.put(x1, y, z, key)

    def inside(self, x, y, z) -> bool:
        """Whether (x, y, z) lies inside the piece's box."""
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def put_inside(self, x, y, z, key: str) -> bool:
        """``put`` that skips (and returns False for) positions outside the box, for fittings near the box faces."""
        if not self.inside(x, y, z):
            return False
        self.put(x, y, z, key)
        return True

    def clear(self, x, y, z):
        self.blocks.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    # ------------------------------------------------------------ roofs

    def roof_ridge_x(self, x0, x1, z_lo, z_hi, y_base, block, ridge, gable, plate=None, overhang=1):
        """A gable roof whose ridge runs along x over the walls z_lo..z_hi: stair courses rising from y_base (the
        first course hangs ``overhang`` blocks past the walls), the gable ends at x0 and x1 filled with ``gable``,
        the long walls capped with ``plate`` at y_base."""
        i = 0
        while True:
            y = y_base + i
            zn, zs = z_lo - overhang + i, z_hi + overhang - i
            if zn > zs:
                break
            for x in range(x0, x1 + 1):
                if zn == zs:
                    self.put(x, y, zn, ridge)
                else:
                    self.put(x, y, zn, stairs(block, "south"))
                    self.put(x, y, zs, stairs(block, "north"))
            for z in range(max(zn + 1, z_lo), min(zs - 1, z_hi) + 1):
                self.put(x0, y, z, gable)
                self.put(x1, y, z, gable)
            i += 1
        if plate:
            for x in range(x0, x1 + 1):
                self.put(x, y_base, z_lo, plate)
                self.put(x, y_base, z_hi, plate)

    def roof_ridge_z(self, z0, z1, x_lo, x_hi, y_base, block, ridge, gable=None, overhang=0, gable_ends=(True, True)):
        """As roof_ridge_x with the ridge along z (the slopes face west and east, the gables are at z0 and z1)."""
        i = 0
        while True:
            y = y_base + i
            xw, xe = x_lo - overhang + i, x_hi + overhang - i
            if xw > xe:
                break
            for z in range(z0, z1 + 1):
                if xw == xe:
                    self.put(xw, y, z, ridge)
                else:
                    self.put(xw, y, z, stairs(block, "east"))
                    self.put(xe, y, z, stairs(block, "west"))
            if gable:
                for x in range(max(xw + 1, x_lo), min(xe - 1, x_hi) + 1):
                    if gable_ends[0]:
                        self.put(x, y, z0, gable)
                    if gable_ends[1]:
                        self.put(x, y, z1, gable)
            i += 1

    # ------------------------------------------------------------ jigsaws

    def connector(self, x, y, z, name: str, facing: str, final_state: str):
        """A village connector; ``facing`` is where the jigsaw points (out of the piece)."""
        target, pool, joint = CONNECTORS[name]
        self._jigsaw(x, y, z, facing, f"{MOD}:{name}", f"{MOD}:{target}", pool, final_state, joint)

    def berth(self, x, y, z, bow: str):
        """A berth marker (README, "Berth markers"): points along the berth's bow direction, becomes water."""
        self._jigsaw(x, y, z, bow, f"{MOD}:berth", EMPTY_POOL, EMPTY_POOL, "minecraft:water", "aligned")

    def _jigsaw(self, x, y, z, facing, name, target, pool, final_state, joint):
        self._check(x, y, z)
        if facing not in FACINGS:
            raise ValueError(f"jigsaws here are horizontal, got {facing}")
        self.blocks.pop((x, y, z), None)
        self.jigsaws[(x, y, z)] = {
            "block": f"minecraft:jigsaw[orientation={facing}_up]",
            "data": {
                "name": name,
                "target": target,
                "pool": pool,
                "final_state": final_state,
                "joint": joint,
                "placement_priority": 0,
                "selection_priority": 0,
            },
        }

    # ------------------------------------------------------------ output

    def spec(self, style: str, notes: list[str]) -> dict:
        ops = []
        rows = sorted({(y, z) for (_, y, z) in self.blocks})
        for (y, z) in rows:
            xs = sorted(x for (x, yy, zz) in self.blocks if yy == y and zz == z)
            start = prev = xs[0]
            for x in xs[1:] + [None]:
                if x is not None and x == prev + 1 and self.blocks[(x, y, z)] == self.blocks[(start, y, z)]:
                    prev = x
                    continue
                ops.append({"type": "box", "from": [start, y, z], "to": [prev, y, z],
                            "block": self.blocks[(start, y, z)]})
                if x is not None:
                    start = prev = x
        for pos in sorted(self.jigsaws, key=lambda p: (p[1], p[2], p[0])):
            j = self.jigsaws[pos]
            ops.append({"type": "block_entity", "pos": list(pos), "block": j["block"], "data": j["data"]})
        used = set(self.blocks.values())
        palette = {k: v for k, v in sorted(self.palette.items()) if k in used}
        sx, sy, sz = self.size
        return {
            "id": f"pns_{self.id}",
            "name": self.name,
            "minecraftVersion": "1.21.1",
            "size": {"x": sx, "y": sy, "z": sz},
            "palette": palette,
            "operations": ops,
            "metadata": {"style": style, "notes": notes},
        }

    def emit(self, style: str, notes: list[str]):
        print(json.dumps(self.spec(style, notes)))
