"""Convert Sponge schematics (.schem, WorldEdit) into vanilla structure templates for ship templates.

Usage:
    python3 tools/schem_to_structure.py                      # every art/schematics/*.schem
    python3 tools/schem_to_structure.py art/schematics/x.schem [more.schem ...]
    python3 tools/schem_to_structure.py --out DIR in.schem   # write somewhere else (tests)

Each ``<name>.schem`` becomes ``common/src/main/resources/data/pirates_n_ships/structure/ships/<name>.nbt``, which
the game loads as the structure ``pirates_n_ships:ships/<name>`` (see ``ship/template`` and art/README.md, "Ship
templates").

Rules:
- Sponge schematic version 2 (``Palette`` + ``BlockData`` at the root) and version 3 (everything inside a
  ``Schematic`` compound, blocks in ``Blocks`` with ``Palette``/``Data``/``BlockEntities``) are read. Block data is a
  varint array indexed ``x + z * Width + y * Width * Length``.
- Air (``minecraft:air``, ``cave_air``, ``void_air``) and ``structure_void`` are dropped, so placing the template
  never carves a box of air out of the sea; the ship's own hold is drained when it is placed.
- Block entities are kept: their data becomes the block's ``nbt`` (with ``id``; the position keys are dropped).
  Version 2 keeps the data at the top level of each block entity, version 3 under ``Data``.
- Entities are not copied (``entities`` is empty); the script says how many it skipped.
- ``Offset`` / ``WEOffset`` only matter to WorldEdit pastes and are ignored: template coordinates start at the
  schematic's minimum corner.
- ``DataVersion`` is the schematic's own (so the game's data fixers upgrade old block states), but never newer than
  1.21.1 (3955); a missing one counts as 3955.
- Output is deterministic: the palette is sorted by block state string, blocks by y, z, x, and the gzip header has no
  timestamp or file name. Re-running on the same input writes the same bytes.
- Every ``pirates_n_ships:helm`` is reported with its template position: that is where a ship template's ``helm``
  points (the definition finds it by itself when it leaves ``helm`` out).

Only the standard library is used (own NBT reader and writer).
"""
from __future__ import annotations

import argparse
import gzip
import io
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_IN = ROOT / "art" / "schematics"
DEFAULT_OUT = ROOT / "common" / "src" / "main" / "resources" / "data" / "pirates_n_ships" / "structure" / "ships"
DATA_VERSION_1_21_1 = 3955
DROPPED = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"}
HELM = "pirates_n_ships:helm"

# NBT tag ids
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    """A typed NBT value, used where the type is not clear from the Python value (lists, numbers)."""

    def __init__(self, kind: int, value, element: int = END):
        self.kind = kind
        self.value = value
        self.element = element  # element type of a LIST

    def __repr__(self):
        return f"Tag({self.kind}, {self.value!r})"


# ---------------------------------------------------------------- reading

def _read(buf: io.BytesIO, kind: int):
    if kind == BYTE:
        return Tag(BYTE, struct.unpack(">b", buf.read(1))[0])
    if kind == SHORT:
        return Tag(SHORT, struct.unpack(">h", buf.read(2))[0])
    if kind == INT:
        return Tag(INT, struct.unpack(">i", buf.read(4))[0])
    if kind == LONG:
        return Tag(LONG, struct.unpack(">q", buf.read(8))[0])
    if kind == FLOAT:
        return Tag(FLOAT, struct.unpack(">f", buf.read(4))[0])
    if kind == DOUBLE:
        return Tag(DOUBLE, struct.unpack(">d", buf.read(8))[0])
    if kind == BYTE_ARRAY:
        (n,) = struct.unpack(">i", buf.read(4))
        return Tag(BYTE_ARRAY, buf.read(n))
    if kind == STRING:
        (n,) = struct.unpack(">H", buf.read(2))
        return Tag(STRING, buf.read(n).decode("utf-8"))
    if kind == LIST:
        element = buf.read(1)[0]
        (n,) = struct.unpack(">i", buf.read(4))
        return Tag(LIST, [_read(buf, element) for _ in range(n)], element)
    if kind == COMPOUND:
        out = {}
        while True:
            child = buf.read(1)[0]
            if child == END:
                return Tag(COMPOUND, out)
            name = _read(buf, STRING).value
            out[name] = _read(buf, child)
    if kind == INT_ARRAY:
        (n,) = struct.unpack(">i", buf.read(4))
        return Tag(INT_ARRAY, list(struct.unpack(f">{n}i", buf.read(4 * n))))
    if kind == LONG_ARRAY:
        (n,) = struct.unpack(">i", buf.read(4))
        return Tag(LONG_ARRAY, list(struct.unpack(f">{n}q", buf.read(8 * n))))
    raise ValueError(f"unknown NBT tag type {kind}")


def read_nbt(data: bytes) -> Tag:
    """Reads a (gzipped or plain) NBT file; returns the root compound."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    buf = io.BytesIO(data)
    kind = buf.read(1)[0]
    if kind != COMPOUND:
        raise ValueError("root tag is not a compound")
    _read(buf, STRING)  # root name
    return _read(buf, COMPOUND)


# ---------------------------------------------------------------- writing

def _write(out: io.BytesIO, tag: Tag):
    k, v = tag.kind, tag.value
    if k == BYTE:
        out.write(struct.pack(">b", v))
    elif k == SHORT:
        out.write(struct.pack(">h", v))
    elif k == INT:
        out.write(struct.pack(">i", v))
    elif k == LONG:
        out.write(struct.pack(">q", v))
    elif k == FLOAT:
        out.write(struct.pack(">f", v))
    elif k == DOUBLE:
        out.write(struct.pack(">d", v))
    elif k == BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)) + bytes(v))
    elif k == STRING:
        raw = v.encode("utf-8")
        out.write(struct.pack(">H", len(raw)) + raw)
    elif k == LIST:
        element = tag.element if v else (tag.element or END)
        if v:
            element = v[0].kind
        out.write(bytes([element]) + struct.pack(">i", len(v)))
        for item in v:
            _write(out, item)
    elif k == COMPOUND:
        for name, child in v.items():
            out.write(bytes([child.kind]))
            _write(out, Tag(STRING, name))
            _write(out, child)
        out.write(bytes([END]))
    elif k == INT_ARRAY:
        out.write(struct.pack(f">i{len(v)}i", len(v), *v))
    elif k == LONG_ARRAY:
        out.write(struct.pack(f">i{len(v)}q", len(v), *v))
    else:
        raise ValueError(f"unknown NBT tag type {k}")


def write_nbt(root: Tag) -> bytes:
    """Gzipped NBT with an empty root name, no timestamp and no file name in the gzip header."""
    out = io.BytesIO()
    out.write(bytes([COMPOUND]))
    _write(out, Tag(STRING, ""))
    _write(out, root)
    raw = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
        gz.write(out.getvalue())
    return raw.getvalue()


def compound(**children: Tag) -> Tag:
    return Tag(COMPOUND, dict(children))


def int_list(*values: int) -> Tag:
    return Tag(LIST, [Tag(INT, v) for v in values], INT)


# ---------------------------------------------------------------- schematic model

def decode_varints(data: bytes, count: int) -> list[int]:
    """Sponge block data: unsigned LEB128 varints, one per block."""
    out = []
    value = shift = 0
    for byte in data:
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
            if shift > 35:
                raise ValueError("varint too long")
            continue
        out.append(value)
        value = shift = 0
    if shift:
        raise ValueError("block data ends inside a varint")
    if len(out) != count:
        raise ValueError(f"block data has {len(out)} entries, expected {count}")
    return out


def parse_state(text: str) -> tuple[str, dict[str, str]]:
    """``minecraft:oak_stairs[facing=north,half=bottom]`` -> name, properties."""
    if "[" not in text:
        return text, {}
    name, rest = text.split("[", 1)
    if not rest.endswith("]"):
        raise ValueError(f"bad block state {text!r}")
    props = {}
    for pair in filter(None, rest[:-1].split(",")):
        key, value = pair.split("=", 1)
        props[key.strip()] = value.strip()
    return name, props


def canonical_state(text: str) -> str:
    name, props = parse_state(text)
    if ":" not in name:
        name = "minecraft:" + name
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"


def _num(tag: Tag | None, default: int = 0) -> int:
    return default if tag is None else int(tag.value)


def read_schematic(root: Tag) -> dict:
    """Normalises a v2 or v3 schematic: size, data version, {(x,y,z): state}, {(x,y,z): block entity nbt}, entities."""
    tags = root.value
    if "Schematic" in tags and tags["Schematic"].kind == COMPOUND:
        tags = tags["Schematic"].value  # v3 wraps everything
    version = _num(tags.get("Version"), 1)
    width, height, length = _num(tags["Width"]), _num(tags["Height"]), _num(tags["Length"])
    if version >= 3:
        blocks = tags["Blocks"].value
        palette_tag, data_tag = blocks["Palette"], blocks["Data"]
        entities_tag = blocks.get("BlockEntities")
    else:
        palette_tag, data_tag = tags["Palette"], tags["BlockData"]
        entities_tag = tags.get("BlockEntities") or tags.get("TileEntities")
    by_id = {}
    for state, index in palette_tag.value.items():
        by_id[int(index.value)] = canonical_state(state)
    ids = decode_varints(bytes(b & 0xFF for b in data_tag.value), width * height * length)
    states = {}
    for i, palette_id in enumerate(ids):
        if palette_id not in by_id:
            raise ValueError(f"block data uses palette id {palette_id}, which the palette does not define")
        x = i % width
        z = (i // width) % length
        y = i // (width * length)
        states[(x, y, z)] = by_id[palette_id]
    block_entities = {}
    for entry in (entities_tag.value if entities_tag else []):
        e = entry.value
        pos = tuple(e["Pos"].value)
        data = dict(e["Data"].value) if version >= 3 and "Data" in e else {
            k: v for k, v in e.items() if k not in ("Pos", "Id", "Data")}
        for key in ("x", "y", "z", "Pos", "Id"):
            data.pop(key, None)
        if "Id" in e:
            data["id"] = Tag(STRING, e["Id"].value)
        block_entities[pos] = Tag(COMPOUND, dict(sorted(data.items())))
    entities = tags.get("Entities")
    return {
        "version": version,
        "size": (width, height, length),
        "data_version": _num(tags.get("DataVersion"), DATA_VERSION_1_21_1),
        "states": states,
        "block_entities": block_entities,
        "entities": len(entities.value) if entities else 0,
    }


def to_structure(schem: dict) -> tuple[Tag, list[tuple[tuple[int, int, int], str]]]:
    """The vanilla structure template compound, plus every helm (position, state)."""
    kept = {pos: state for pos, state in schem["states"].items() if parse_state(state)[0] not in DROPPED}
    palette = sorted(set(kept.values()))
    index = {state: i for i, state in enumerate(palette)}
    palette_tags = []
    for state in palette:
        name, props = parse_state(state)
        entry = {"Name": Tag(STRING, name)}
        if props:
            entry["Properties"] = Tag(COMPOUND, {k: Tag(STRING, props[k]) for k in sorted(props)})
        palette_tags.append(Tag(COMPOUND, entry))
    blocks = []
    helms = []
    for (x, y, z) in sorted(kept, key=lambda p: (p[1], p[2], p[0])):
        state = kept[(x, y, z)]
        entry = {"pos": int_list(x, y, z), "state": Tag(INT, index[state])}
        nbt = schem["block_entities"].get((x, y, z))
        if nbt is not None:
            entry["nbt"] = nbt
        blocks.append(Tag(COMPOUND, entry))
        if parse_state(state)[0] == HELM:
            helms.append(((x, y, z), state))
    data_version = min(schem["data_version"], DATA_VERSION_1_21_1)
    root = compound(
        size=int_list(*schem["size"]),
        entities=Tag(LIST, [], COMPOUND),
        blocks=Tag(LIST, blocks, COMPOUND),
        palette=Tag(LIST, palette_tags, COMPOUND),
        DataVersion=Tag(INT, data_version),
    )
    return root, helms


def convert(source: Path, out_dir: Path) -> Path:
    schem = read_schematic(read_nbt(source.read_bytes()))
    root, helms = to_structure(schem)
    out_dir.mkdir(parents=True, exist_ok=True)
    target = out_dir / (source.stem + ".nbt")
    target.write_bytes(write_nbt(root))
    w, h, l = schem["size"]
    print(f"{source.name}: Sponge v{schem['version']}, {w}x{h}x{l}, {len(root.value['blocks'].value)} blocks, "
          f"{len(root.value['palette'].value)} states, {len(schem['block_entities'])} block entities -> {target}")
    if schem["data_version"] > DATA_VERSION_1_21_1:
        print(f"  warning: DataVersion {schem['data_version']} is newer than 1.21.1, written as {DATA_VERSION_1_21_1}")
    if schem["entities"]:
        print(f"  note: {schem['entities']} entities skipped (templates hold blocks only)")
    if not helms:
        print("  note: no helm: give the template definition a helm position or treat it as hull only")
    for pos, state in helms:
        print(f"  helm at {list(pos)} ({state})")
    if len(helms) > 1:
        print("  warning: more than one helm, the definition should name the one to assemble from")
    return target


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("inputs", nargs="*", type=Path, help="schematics (default: art/schematics/*.schem)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="output directory")
    args = parser.parse_args(argv)
    inputs = args.inputs or sorted(DEFAULT_IN.glob("*.schem"))
    if not inputs:
        print(f"no schematics in {DEFAULT_IN}", file=sys.stderr)
        return 1
    for source in inputs:
        convert(source, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
