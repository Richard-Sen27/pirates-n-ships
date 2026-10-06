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

## 11b. Dry dock and beach
The ship only feels the sea where world water touches its own bottom (up to nine probes under its hull; a third of
them must be wet). Water next to it or below it does not count.
1. Dig a dry dock on the shore: a stone pit next to the sea, separated by a one-block wall, deep enough that the
   hull's bottom is below sea level. Build a closed hull on the pit floor, place the helm, assemble. Expected: the
   ship stays exactly where it is, it does not lift, slide or tilt, and nothing floods (`/sable` force display shows
   no buoyancy force). Break the wall so the sea pours in: once the water reaches the hull's bottom, the ship floats up.
2. Build a hull on a cliff edge a few blocks above the sea and assemble: it stays put (no lift from the water below).
3. Assemble a ship that is half on a beach, half in shallow water: if at least a third of its bottom is in water it
   floats with the sea level of that water (the beached part still rests on the sand); with less it is aground and
   behaves like a ship on land. Report whether that feels right.
4. Note your X/Z coordinates in every report. Sable's physics loses precision very far from the world origin (beyond
   about 4,000,000 blocks it gets worse, beyond 8,000,000 ships may sink into blocks); test near spawn.

## 11c. Partial blocks
A slab, stair, trapdoor or door in the hull is drawn dry as a whole cell when its empty part faces only the dry
inside; when the empty part faces the sea, outside air or flood water, the world water in it stays visible.
1. Build a closed hull whose bottom is well below sea level. Make the hold floor from **bottom slabs** (keep the outer
   edge of the floor full blocks), add **stairs** inside the hold leading down from the deck, put a **trapdoor hatch**
   in the deck, and make one cell of the hull bottom a **top slab** (its empty half points down into the sea). Place
   the helm and assemble on the sea.
2. Go into the hold and look at the floor slabs and the stairs from close up and from a distance. Expected: no water
   in the upper half of any floor slab and none in the empty corner of the stairs.
3. Close the hatch from below and look up at it from the hold. Expected: no water drawn below the hatch.
   Known and intended: a hatch with the open sky above is never drawn dry, so if the deck is below sea level you may
   see water in the hatch's own block.
4. Dive under the ship and look at the bottom. Expected: the sea renders normally right under the hull, including in the
   lower half of the top-slab cell (no hole or see-through spot in the sea), as before.
5. Breach the hull below the waterline next to the slab floor and wait for the bottom layer of the hold to flood.
   Expected: the water shows in the slab halves of the flooded part, the same moment the cells above them show water.
6. Config: `partial_blocks = false` in section `dry_hull` brings back the old look (water in slab and stair halves).
- Send back: screenshots of steps 2, 3 and 4, and whether any slab or stair half still flickers between dry and wet.

## 12. Config toggles (optional)
In `saves/<world>/serverconfig/pirates_n_ships-server.toml` (section `dry_hull`): set `enabled = false`, rejoin:
water shows inside and you swim, as in milestone 1. Set `dry_buoyancy = false`: the ship should sit lower.
Set `flood_weight_scale = 3.0` and breach the hull: it should sink faster. `sea_probe_height` (default 24) is how far
up the water touching the hull is followed to find the surface.

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
