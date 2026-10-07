# Playtest: flags (work packages E1c, F6)

The rules (delay, striking, giving flags back, banners, drops, save and reload, the allegiance query) are covered by
JUnit and GameTests. This checklist covers what only the client shows: the look of the flag on the pole from every
side, the feedback messages, the wind direction, and multiplayer sync. Textures are generated placeholders
(`tools/gen_flag_textures.py`), so judge readability, not beauty.

Setup: `./gradlew :neoforge:runClient`, a **survival** world with cheats on (steps 1 to 9), default config
(`hoist_delay_ticks = 60`, i.e. 3 seconds; section `flags` of the server config).
Please send screenshots of steps 3, 7 and 10, and `latest.log` if anything goes wrong.

## Steps

1. **Craft the flags.** Get sticks, white/blue/black wool, red dye, white dye and a bone (`/give`). Open the crafting
   table and the recipe book.
   - **Expected:** shapeless recipes: stick + 2 white wool + red dye = **Merchant Flag**; stick + 2 blue wool + white
     dye = **Navy Flag**; stick + 2 black wool + bone = **Jolly Roger**. Each item has a flat icon (a flag on a stick)
     and an English name.
2. **Hoist each flag.** Place three flagpoles (on top of a 2-high stack of flagpoles is fine). Right-click each with
   one of the three flags and count.
   - **Expected:** the action bar shows "Hoisting the merchant flag..." (etc.), the flag item leaves your hand at once,
     and after about **3 seconds** the cloth appears and the action bar says "The merchant flag is flying".
3. **Look from all sides.** Place the poles in the open (the cloth needs about two blocks of free space on every
   side it may point to). Walk around each pole, look from below, from above and edge-on.
   - **Expected:** a real flag, not a small panel: a thin cloth **one block high** (the full height of the pole
     block it hangs on) and **one and a half blocks long**, starting at the pole with **no gap** (a dark brown hoist
     edge right against the pole) and ending about 1.5 blocks out. On a stack of poles it hangs from the top block
     you hoisted it on. White with a red band (merchant), blue with a white cross shifted toward the pole (navy),
     black with a skull and crossbones (Jolly Roger). **Both sides** show the design; from the other side it is
     mirrored (the hoist edge is still at the pole). Square pixels, not stretched. No black or white box around the
     cloth, no dark shading on the far end. The top, bottom and tip edges are thin lines in the cloth's color. The
     pole still looks like the old flagpole, and its outline box is still only the pole (the cloth has no hitbox:
     you can walk through it). A block placed where the cloth hangs simply overlaps it (known, by design).
4. **Change a flag.** Right-click the merchant pole with the navy flag.
   - **Expected:** after 3 seconds the navy flag flies and the merchant flag is back in your inventory.
5. **Cancel.** Start hoisting a flag, and within the 3 seconds right-click the pole with an empty hand.
   - **Expected:** "Stopped working the flagpole", the flag comes back to your inventory, the pole keeps what it had.
6. **Strike and raise.** Right-click a flying flag with an empty hand, wait 3 seconds; then again.
   - **Expected:** "Striking the colors...", then "Colors struck: you signal surrender" and the cloth disappears
     (the pole keeps the flag: `/pirates flag get <pos>` says "struck"). The second time: "Raising the ..." and the
     same flag flies again.
7. **Patterned banner.** Make a banner with a few patterns on a loom and right-click a pole with it.
   - **Expected:** after 3 seconds a **generic custom cloth** of the same full size appears (tan with a gold edge and a
     red mark). It does not
     show the banner's patterns (known limitation: the block model can't). `/pirates flag get <pos>` says
     "custom flag, flying".
8. **Take down.** Sneak and right-click the banner pole with an empty hand, wait 3 seconds.
   - **Expected:** "Took down the flag", the cloth disappears and the banner is back in your inventory **with its
     patterns** (place it to check).
9. **Break the pole.** Hoist a flag, then break the pole with an axe (survival).
   - **Expected:** the flagpole and the flag drop as items. Break one in the middle of a hoist: both the old and the
     pending flag drop.
10. **Wind, four directions.** Stand a few blocks away from a pole with a flying flag, with free space around it.
    Run `/pirates wind set 0 8`, wait about 10 seconds (flags check the wind every
    `wind_update_interval_ticks = 200` ticks), and note where the cloth points; then `/pirates wind set 90 8`,
    `/pirates wind set 180 8` and `/pirates wind set 270 8`, waiting each time. End with `/pirates wind clear`.
    - **Expected:** the cloth always points **downwind**, away from where the wind comes from: wind from 0 (north)
      → cloth points **south**; from 90 (east) → **west**; from 180 (south) → **north**; from 270 (west) → **east**
      (F3 shows which way you face). In all four directions it looks the same: one block high, 1.5 blocks long, no
      gap at the pole, the design readable on both sides, nothing stretched, missing or flickering. Also check from
      a distance (8 to 16 blocks) that the far end of the cloth does not vanish when the pole's own chunk section is
      just off screen. All flags in one area point the same way. A freshly hoisted flag points downwind at once.
      There is no fluttering animation in this version.
    - **Struck:** strike one of the flags (empty hand) while the wind is set: the cloth disappears completely
      (nothing left at the pole or in the air), and comes back pointing downwind when raised.
11. **Relog.** Hoist a flag, strike another, leave the world and load it again.
    - **Expected:** the same flags fly, the struck one is still struck (`/pirates flag get`), nothing dropped.
12. **Commands.** `/pirates flag set <pos> jolly_roger`, `/pirates flag strike <pos>`, `/pirates flag raise <pos>`,
    `/pirates flag set <pos> none`, `/pirates flag get <pos>` on a non-pole block.
    - **Expected:** instant changes with a chat confirmation; `set` drops the old flag at the pole; the last one says
      "That block is not a flagpole".
13. **Optional, second player** (LAN or a dedicated server): the second player stands next to the poles while the
    first hoists, strikes and takes down flags.
    - **Expected:** the second player sees the same flag, at the same time, pointing the same way.


## G8: banner colours and nameplates
1. **Banner colours.** Hoist a red, a blue, a black and a white banner on four poles. Flying: the cloth shows the
   banner's colour on both faces and the edges (a white banner reads as a very light grey, fold shading darkens by
   up to about a tenth). Struck (empty hand) and taken down (sneak): no cloth. Raised again: the colour is back. A new
   banner hoisted over an old one: the old colour stays until the hoist completes. Check all four wind facings. Swap a
   red banner directly for a blue one: the colour changes without touching any block nearby (this tests the re-mesh).
   Also on an assembled ship. Navy, merchant and jolly roger flags look unchanged.
2. **Nameplate.** Build a hull with a nameplate on its outer side facing out, assemble it, use a name tag "Black Gull"
   on the helm. Within about a second "Black Gull" appears centred on the panel in dark brown, upright, left to right
   from in front, readable from 3 to 5 blocks. Plates on all four sides read correctly, also while the ship turns and
   rolls. Rename to "Sea Wolf": every plate changes within a second. A name of about 40 characters shrinks, a very long
   one is cut with "...". A plate placed on an already named ship shows the name within a second. Disassembly blanks
   the plates; reassembling and naming brings the name back. `ship_identity.nameplate_shows_name = false` blanks them.
3. **Performance.** 30 or more nameplates and 30 or more banner flags on one ship: no noticeable FPS or TPS drop.


Addendum (P1 finding, fixed in P4): on a ship that has turned since assembly the flag pointed the wrong way (180° after a half turn), because its wind facing ignored the ship's orientation. After P4 the flag must stream downwind on a turned ship too. When judging the sails, use `/pirates wind get`, not the flag.


Addendum (P4): with the wind fixed from the north, a flag on land points south and stays. A ship's flag points south before turning; after turning the ship 90° it still points south in the world (sideways relative to the ship) within about a second; after 180° likewise (the opposite side of the ship). During a slow turn the flag jumps 90° at each 45° boundary (four-way facings). After disassembling a turned ship the flag on the ground points downwind within one land interval, up to 10 s.


## FL1: the exact angle
1. **Land, `/pirates wind set 225 8`:** the cloth points north-east, not snapped; `/pirates wind set 200 8`: it swings smoothly over about half a second to about 20° east of north; 170 then 190: it swings the short way across north. Around the pole the design reads on both sides, mirrored on the back, the hoist touches the pole, the pixels are square.
2. **Ship turning slowly** with a fixed wind: the flag keeps pointing downwind in the world the whole time, no 90° jumps, no jitter at the end of each second; in F5 it sweeps smoothly around the ship's frame.
3. **Banners:** red, blue, black and white show their base colour on both faces and the edges; swapping a banner changes the colour after the hoist.
4. **Hoisting:** about 3 s, then the cloth appears already downwind; striking removes it; raising brings it back without a swing from an old angle.
5. **Culling:** from 8–16 blocks the far end of the cloth stays visible when the pole's block is just off screen. An old pole starts at its old facing and turns at the next check.
