# Playtest: sound pipeline and sea music (work package G1)

The selection logic, the `sounds.json` builder and the resource consistency are covered by JUnit tests and a GameTest.
Only a client shows what the music does in a real world, whether the volume scaling works and how the sounds feel.

Setup: `./gradlew :neoforge:runClient`, a creative world, subtitles on (Options → Music & Sounds → Show Subtitles).
To hear music quickly, open the client config (Mods → Pirates 'n' Ships → Config → client config → Audio) and set
`min_gap_seconds = 0` and `max_gap_seconds = 5`, or run `/stopsound @s music` to end the current track.
Please send `latest.log` if anything differs.

## Steps

1. **At sea.** `/locate biome minecraft:ocean`, teleport there, stand on the shore or swim.
   - **Expected:** after the current vanilla track ends plus the gap, "There Be Pirates – The Quest" or "There Be
     Pirates – Lost in the Deep" plays. The same on a beach.
2. **Aboard.** Assemble a ship or step onto one, or sit in a station seat.
   - **Expected:** the next track is a shanty ("Leave Her Johnny" or "The Ghost Of Gallows Reef"). Jumping on deck must
     not switch back to sea music (3 s grace). Aboard a ship on a river or lake also counts as aboard.
3. **Elsewhere** (plains, forest).
   - **Expected:** only vanilla music, though a running track of ours finishes first.
4. **`shanties_aboard = false`.**
   - **Expected:** aboard you get the sea pool.
5. **`music_enabled = false`.**
   - **Expected:** never our music, anywhere.
6. **`music_volume = 0.3`.**
   - **Expected:** the next of our tracks starts clearly quieter than vanilla music at the same slider. Moving the music
     slider during one of our tracks brings it back to the plain slider volume (known limit, see progress.md).
7. **Anchor.** Drop and raise the anchor at the capstan.
   - **Expected:** chain, splash and thud sound as before, and the subtitles now read "Anchor chain rattles",
     "Anchor splashes" and "Anchor lands" instead of vanilla's.
8. **Gun and sword sounds.** `/playsound pirates_n_ships:combat.pistol_shot master @s`, then likewise
   `combat.pistol_empty`, `combat.cannon_shot`, `combat.cannon_volley`, `combat.melee.swing` (repeat it to hear the
   four variants), `combat.melee.parry`, `combat.melee.hit_armor`, `combat.melee.hit_heavy` (three variants),
   `combat.melee.unsheathe`, `combat.melee.disarm`, `combat.melee.weapon_break`.
   - **Expected:** each plays with its subtitle at roughly similar loudness. Tell us if the gun sounds are too quiet
     next to vanilla sounds (they measured 2 to 3 LU below the target).
9. **Log.** `latest.log` has no "Unable to play unknown soundEvent" warnings for `pirates_n_ships:*`.

## Also worth a look
- Our music replaces vanilla's underwater and creative-mode music in sea biomes. Say whether that feels right.
- Is the 2 to 5 minute gap between tracks (default) too long or too short?


Addendum (A1): board a ship and let it roll: occasional quiet wooden creaks from different spots in the lower hull, more often when it rolls harder, at least 2 s apart, never a loop, no door or chest sounds any more; several creaks in a row should not sound identical (two variants, pitch 0.5–0.8); subtitles show "Ship creaks"; a ship lying still is silent. Judge the level against the waves and music: too faint means raising `hull_creaking.maxVolume`.
