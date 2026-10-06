#!/usr/bin/env python3
"""Extracts vanilla block textures into art/vanilla/ (ignored by version control) for loading into Blockbench.

The block models in art/models/*.bbmodel reuse vanilla textures (minecraft:block/...). The saved projects embed them,
so they reopen as they are. To add a vanilla texture to a new model, extract it here first and load it in Blockbench
with namespace "minecraft" and folder "block", so the exported model references minecraft:block/<name>.

Run (from the repository root): python3 tools/extract_vanilla_textures.py [jar] [name ...]
Without names it extracts every vanilla texture the committed projects use; names are block texture names such as
spruce_planks. Without a jar it uses the one ModDevGradle downloaded (~/.gradle/caches/neoformruntime/artifacts/).
"""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "art/models"
OUT = ROOT / "art/vanilla"


def find_jar():
    for arg in sys.argv[1:]:
        if arg.endswith(".jar"):
            return Path(arg)
    jars = sorted((Path.home() / ".gradle/caches/neoformruntime/artifacts").glob("minecraft_1.21.1_client.jar"))
    if not jars:
        sys.exit("client jar not found, pass its path as the first argument")
    return jars[0]


def wanted():
    extra = [a for a in sys.argv[1:] if not a.endswith(".jar")]
    if extra:
        return [f"block/{n}" for n in extra]
    names = set()
    for project in MODELS.glob("*.bbmodel"):
        for tex in json.loads(project.read_text()).get("textures", []):
            if tex.get("namespace") == "minecraft":
                names.add(f"{tex.get('folder') or 'block'}/{tex['name'].removesuffix('.png')}")
    return sorted(names)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(find_jar()) as jar:
        for name in wanted():
            data = jar.read(f"assets/minecraft/textures/{name}.png")
            (OUT / f"{Path(name).name}.png").write_bytes(data)
            print(f"art/vanilla/{Path(name).name}.png")


if __name__ == "__main__":
    main()
