# Sounds

Every sound file of the mod comes from `manifest.json` here and is converted by `tools/convert_sounds.py`
(docs/design.md §16, "Sound pipeline"). The converted `.ogg` files live in
`common/src/main/resources/assets/pirates_n_ships/sounds/` and are committed; the source files are not.

## Adding a sound

1. Drop the source file (mp3, wav, ...) into `raw_sound/` at the repository root. The folder is git-ignored.
2. Add an entry to `manifest.json`:
   - `source`: the file name in `raw_sound/`
   - `target`: the path under `assets/pirates_n_ships/sounds/`, lower case, ending in `.ogg` (e.g. `combat/musket_shot.ogg`)
   - `kind`: `music` (stays stereo, normalised to about -18 LUFS) or `effect` (made mono so Minecraft can play it
     positionally, about -16 LUFS)
   - `title`, `author`, `url` (the page you downloaded it from) and `license`
   Only use files whose license allows use in a mod (Pixabay Content License, CC0, CC-BY with credit, our own recordings).
3. Run `python3 tools/convert_sounds.py` (or `python3 tools/convert_sounds.py <other raw folder>`). It needs `ffmpeg`;
   if that ffmpeg has no libvorbis encoder (Homebrew's ffmpeg 8 doesn't), also `oggenc` (`brew install vorbis-tools`).
   Targets newer than their source are skipped; `--force` converts everything again. The script also rewrites
   `docs/credits.md`.
4. Use the file in a sound event: `ModRegistry.sound("my.event")` and a `data.sounds(...)` entry in the module's
   `gatherData` (name `pirates_n_ships:combat/musket_shot`, `.stream(true)` for music), then run `./gradlew :neoforge:runData`.
   The JUnit test `SoundResourcesTest` fails if a generated entry points at a file that does not exist.
5. Commit the `.ogg`, `manifest.json`, `docs/credits.md` and the generated `sounds.json`.

Very short effects (under about half a second, like `pistol_empty`) are too short for a loudness measurement, so
loudnorm leaves their level roughly as it was.
