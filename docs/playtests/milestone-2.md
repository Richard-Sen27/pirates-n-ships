# Playtest: milestone 2, dry hull (spike 2)

Build: the branch of work package D2. Start with `./gradlew :neoforge:runClient`, create a **Creative** world
(default world type, cheats on) and fly to an ocean. Open chat with `T` for every command. Turn on coordinates with
`F3`. Keep `logs/latest.log` for the end.

What spike 2 does: the dry cells of every closed compartment become Sable water occlusion regions on the server and on
your client. Below deck you should see no water, not swim, have no underwater fog and be able to breathe. A broken hull
block below the waterline floods the compartment at a rate, and the dry region shrinks from below as it fills.
**Not in this spike:** no water surface is drawn inside a partly flooded hold. You see the flooded part as open sea
(the occlusion simply stops there), and the dry part above as dry.

Known and harmless: `Failed to apply tag physics properties. Unknown block: create:flywheel` on every start, and
`Reference map 'pirates_n_ships.refmap.json' ... could not be read` in dev.

## 0. Setup
Same as `docs/playtests/milestone-1.md` §0: open ocean at least 6 blocks deep, hover with your **feet at Y=63**, face
**east**.

## 1. Build a **decked** test hull
Run milestone 1 §1 (the boat recipe), then close the top so the hold is a closed box:
1. `/fill ~1 ~ ~-2 ~7 ~ ~2 minecraft:oak_planks` (deck one block above the top rim; adjust the Y offset if your
   milestone-1 boat rim ends at a different height: the deck must sit directly on the rim).
2. Replace one deck block near the stern with a hatch: `/setblock ~2 ~ ~ minecraft:oak_trapdoor[half=top]`.
3. Put the helm on the deck (if milestone 1's helm is now inside the hold, place a new one on deck and use that).
Expected: the hold is completely enclosed by planks, with the closed trapdoor as the only opening.

## 2. Assemble in the sea
Right-click the helm. Expected: "Ship assembled: N blocks". The ship floats; it may rise a little compared with
milestone 1 (the dry air inside now lifts it). Note how high the waterline sits on the hull side (count blocks).

## 3. Below deck, from inside
Open the hatch, drop into the hold, close the hatch above you.
- No water visible inside the hold, in any direction. Expected: dry.
- You walk, you do not swim. Sprint-swimming is impossible. No underwater fog or blue overlay.
- The air bubble bar does not appear, or refills. Stay 30 seconds: no drowning damage.
- No underwater ambient particles, no bubble sounds.
- Look at the hull walls from inside: you see the planks, not water in front of them.

## 4. From outside
1. Stand on the deck and look down over the side: the sea around the hull looks normal, the hull is not "inside" a
   dry bubble outside its walls.
2. Dive into the sea next to the ship and look at the hull from under water: the hull walls look normal (planks), there
   is no see-through hole into the hold, no flickering at the hull edges.
3. Swim under the ship and look up at the bottom.

## 5. Items and the hatch
1. In the hold, drop an item (`Q`). Expected: it lies on the floor, it does not float up or drift.
2. Climb out through the hatch and back in, several times. Expected: no water pours in (the hatch is above the
   waterline), you never switch to swimming at the hatch.
3. With the hatch **open**, wait 30 seconds. Expected: nothing floods.

## 6. Breach below the waterline
1. In the hold, break one plank of the side wall at the lowest hold layer (below the waterline).
2. Watch for 30-60 seconds. Expected in this spike: after about half a second the hole is recognised as a breach.
   The lowest layer of the hold then stops being dry: you see sea water there and swim when you stand in it. Layer by
   layer the dry part shrinks upward. There is no animated water surface. The ship slowly sits lower in the water.
3. Note how long it takes until the hold is completely wet, and whether the ship sinks.

## 7. Patch it
1. Place a plank back in the hole. Expected: the breach is closed. The water that came in stays (there are no pumps
   yet): the flooded layers stay wet.
2. Optional: break and patch a wall block **above** the waterline. Expected: nothing floods.

## 8. Heel the ship
Stand at one side of the deck, then walk inside the hold to one side. If the ship heels noticeably (more than about
5°), the hull is re-analysed. Expected: still dry inside, no flicker of water when the ship heels and rights itself.
Optional: `/sable` force display (if you know how to switch it on) shows a force group **Hull Buoyancy**.

## 9. Save, quit, rejoin
After step 6 or 7 (some water inside), save and quit to the title screen, rejoin. Expected: the hold is dry where it
was dry before, wet where it was flooded. A breach that was open is still a breach (keeps flooding).

## 10. A second player (optional, LAN)
Open to LAN, join with a second client. The second player should see the hold dry as well as soon as the ship is in
view, also after flying away (out of view distance) and coming back.

## 11. Shader pack (optional)
With Iris or Oculus and a shader pack: repeat steps 3 and 4. Report whether water renders inside the hold.

## 12. Config toggles (optional)
In `saves/<world>/serverconfig/pirates_n_ships-server.toml` (section `dry_hull`): set `enabled = false`, rejoin:
water shows inside and you swim, as in milestone 1. Set `dry_buoyancy = false`: the ship should sit lower.
Set `flood_weight_scale = 3.0` and breach the hull: it should sink faster.

## What to send back
- Screenshots: step 3 (looking around the dry hold, with F3), step 4.2 (hull from under water), step 6 after 30 s
  (partly flooded hold), step 9 after rejoin.
- The waterline height on the hull in step 2, with and without `dry_buoyancy` (step 12).
- How long step 6 took to flood completely, and whether the ship sank.
- `logs/latest.log`, especially lines with `Hull runtime`, `Hull analysis` or `pirates_n_ships` errors.

## Tuning questions
1. Does the dry ship float at a sensible height (deck clearly above the water)? Too high (bobbing like a cork) means
   `dry_buoyancy_scale` is too big; too low means too small.
2. Does a flooded ship sink at a sensible speed, or sink at all? Should a fully flooded wooden ship sink (spec: yes)?
3. Does the ship oscillate (bob up and down without settling) after assembly or while flooding?
4. Is the flooding through one broken block too fast or too slow (`flooding.inflow_rate`)?
