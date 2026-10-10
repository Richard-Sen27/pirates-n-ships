#!/usr/bin/env python3
"""The rope railing's rope texture, block/rope_line (RAIL6, art/README.md "Railing rework (RAIL6)").

The rope railing's lines are 1.5 px thick, so a face shows a strip of the texture 16 texels long and 1.5 tall. The
rigging's block/rope (bold two-colour stripes) breaks into blotches at that height and stripped birch reads as a pale
wooden rail. This texture is a twist drawn for such a strip: every row is the same 6-texel strand (rounded: flank,
crown, flank, shade, groove, shade) shifted two texels per row, so any 1.5-texel band shows rounded strands laid at a
slant along the line, and an end (a 1.5 x 1.5 patch) shows the strands' cut. Chosen over a 4-texel ramp, a 4-texel
rounded strand and a one-texel slant by renders in spruce and dark oak (art/README.md, RAIL6).

Writes common/src/main/resources/assets/pirates_n_ships/textures/block/rope_line.png (standard library only).
Run from anywhere: python3 tools/gen_rope_line_texture.py
"""
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/pirates_n_ships/textures/block/rope_line.png"

# one strand and its groove across 6 texels (the rigging rope's hues): lit flank, crown, lit flank, shade, groove, shade
STRAND = [(0xC8, 0xA4, 0x6E), (0xDE, 0xC2, 0x92), (0xC8, 0xA4, 0x6E), (0x9A, 0x76, 0x46), (0x62, 0x47, 0x29), (0x9A, 0x76, 0x46)]
SLANT = 2  # texels the strands move along per row: a 1.5-texel band shows a 3-texel slant, the lay of the twist


def pixel(x, y):
    return STRAND[(x + SLANT * y) % len(STRAND)]


def png(width, height, rgb):
    raw = b"".join(b"\x00" + b"".join(bytes(rgb(x, y)) for x in range(width)) for y in range(height))

    def chunk(kind, data):
        c = kind + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    OUT.write_bytes(png(16, 16, pixel))
    print(OUT)
