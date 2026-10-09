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


## FL2: flags in the world
Setup: a small hull in open water with a flagpole on deck and a cannon, assembled by you (you are its owner). A navy soldier and a pirate on a dock or a second ship 10 to 20 blocks away (spawn eggs or `/pirates mob spawn`). Survival mode (creative players are never targets). `/pirates law score get @s` and `/pirates law last @s` read your record, `/pirates law hostile @s` says whether the navy attacks you.
1. **Jolly Roger near the navy.** Hoist the Jolly Roger (about 3 s). Expected: within about 2 s of the flag going up the soldier aims at you (musket), with a clean record. `/pirates law last @s` shows "Sailing under the Jolly Roger" against your ship, +10 points; stay in range for a minute: no second charge (repeat window 300 s). A crew member standing on deck is shot at too.
2. **Jolly Roger near a pirate.** Same flag, pirate in reach: it does not come for you. Hit it once: it fights back. Swap to the merchant flag: it attacks on sight again.
3. **Strike mid-fight.** While the soldier is shooting at you, strike the colours (empty hand at the pole, 3 s). Expected: it stops targeting you within a second, even with a bounty (`/pirates law score set @s 100` first). Raise the colours again: it resumes. A pirate duelist likewise breaks off when the colours come down.
4. **False colours.** `/pirates law score set @s 20` (suspect), hoist the navy flag, sail within about 16 blocks of the soldier. Expected: after a while (on average about 15 to 20 s within 16 blocks at default settings, random; faster with a higher score) the chat says "The navy has seen through your colours" in red, `/pirates law last @s` shows "Flying false colors" (+40), and the soldier attacks you although the navy flag still flies (the +40 also makes you wanted; to see the blown cover alone, set the score back to 20 and check that the soldier still attacks). For 5 minutes (`blown_cover_ticks` 6000) the navy keeps treating the ship as hostile whatever it flies. With a clean record (score 0) the navy flag is never seen through. Further away (40+ blocks) it takes much longer.
5. **Firing on a struck ship.** Second ship (owned by someone else, or assembled by another player) strikes its colours. Fire a cannon at it so the ball hits its hull. Expected: "Attacking a ship that struck its colors" (+40) in `/pirates law last @s`. Let your crew fire instead (whistle "Fire!") while you are online: the charge goes to you as the owner.
6. **Firing on a merchant ship.** The second ship flies a merchant flag or a banner: a hit is "Attacking a neutral ship" (+15). A Jolly Roger or navy flag, or no flag at all: no crime. Hitting your own ships: no crime.
7. **Wrecks.** Shoot the second ship in two: the piece without the helm flies nothing (it no longer counts as surrendered or neutral).
8. **Config off.** `law.flags.enabled = false` (server config, `/reload` or restart): the Jolly Roger draws no navy fire and no charge, a struck ship gets no protection, false colours are never detected, hits on struck or merchant ships are no crime.


## VIS1a: tall poles and the hoist
Setup: survival, a few flagpoles and the three flags, a banner; open ground with room downwind. Default config (`flags.stacked_poles` on, `flags.max_pole_height` 6, `hoist_delay_ticks` 60; client `flag_visuals.hoist_animation` on).
1. **Parts.** Place one flagpole: the single pole (cleat at the bottom, finial on top, two thin halyard lines from the cleat to the truck). Stack a second: the lower block keeps only the cleat, the upper only the truck and finial. Stack up to six: the blocks between are bare pole with the two halyard lines. Expected: no gap, step or seam where the blocks meet, from any angle and up close; the halyard lines run straight through all blocks on the cleat's diagonal. A seventh flagpole on top is refused with "A flagpole can be at most 6 blocks tall" in the action bar and stays in your hand.
2. **Use any block.** On the six-block pole, use the bottom block with the navy flag. Expected: "Hoisting the navy flag..." as on a lone pole, and the cloth climbs from the foot (its lower edge a little below the foot block) to the top over 3 s, then flies at the top as before (downwind, rippling). Empty hand on a middle block: "Striking the colors...", the cloth runs down to the foot over 3 s and disappears there. Empty hand again: it climbs back up. Sneak-use with an empty hand: it runs down and the flag comes back to your inventory.
3. **Hoisting a banner.** Hoist a red banner on the tall pole while the navy flag flies: the navy cloth vanishes at once and a red cloth climbs the pole; the navy flag comes back to you when the banner reaches the top.
4. **Cancel.** Start a hoist and use the pole again with an empty hand halfway: the climbing cloth snaps back to what flew before (the old flag at the top, or nothing), the flag item comes back.
5. **Grow a flying pole.** On a two-block pole flying the Jolly Roger, place a third flagpole on top. Expected: the flag jumps up to the new top at once, nothing drops, the old top now shows bare pole. Do it during a hoist: the climbing cloth continues on the taller pole and the hoist finishes at the new top on time.
6. **Break.** Break the top block of a flying pole: the flag drops, the block below shows the truck and finial and flies nothing. Break a middle block of a flying pole: the upper part keeps flying, the lower part shows its own finial; both work as poles.
7. **Low on a tall pole.** Stand close to the foot of a six-block pole and look only at the foot (the top out of view) while someone strikes the flag: the cloth appears at the foot at the end and is not cut off or missing while it passes the lower blocks. Lighting: under a roof over the lower half, the cloth gets darker when it passes into the shade.
8. **On a ship.** A ship with a four-block pole flying the Jolly Roger: assemble, sail and turn; the flag flies at the top and streams downwind. Strike while sailing: the cloth runs down along the pole and stays with the moving ship (no lag, no offset from the pole). Disassemble: the pole stands with the flag at the top. `/pirates law hostile @s` near the navy behaves as with a lone pole.
9. **Toggles.** Client `flag_visuals.hoist_animation = false`: flags appear and vanish at the top at the end of the delay, as before. Server `flags.stacked_poles = false`: every block of a stack is its own pole again (use the bottom block: the flag flies at the bottom block), the parts still look stacked.
10. **Old worlds and structures.** A world with flagpoles from before this version: lone poles look the same; old stacks (pirate camp, navy fort) show the stacked parts within a tick of loading and their flag at the top.


## FLG2: banner flags with colour and patterns
Setup: survival with cheats, a loom, banners and dyes; a lone flagpole and a four-block pole in the open with room
downwind (`/pirates wind set 270 8`: the cloth points east); default config (client `flag_visuals.hoist_animation`
on, `flag_visuals.banner_upright` off). Please send screenshots of steps 1, 2 and 6.
1. **White banner with a red cross.** On a loom put a red **Cross** (the straight `+` cross; the `x` is the
   "Saltire") on a white banner, then hoist it on the lone pole. Expected: the cloth is white (a very light grey weave) with
   a red cross whose arms run along the cloth and up and down it. Compare with the banner itself (place a copy):
   the flag shows the banner **hung sideways from the pole**: the banner's top edge is at the pole, its bottom edge
   at the far end. To see that, add a black **Chief** (top band) to a second white banner: on the flag the black band
   is the strip next to the pole, from the bottom to the top of the cloth.
2. **Both faces.** Walk around the pole. Expected: the design is on both faces, mirrored on the back like a real flag
   (an asymmetric pattern shows it, such as a **Chief Dexter Canton**, the square in the banner's top-left corner: on
   the front it sits at the pole, at the bottom of the cloth; on the back it is also at the pole and at the bottom,
   and since the pole is then on your right, the design reads mirrored). Seen from the front (the pole on
   your left) the design reads as the banner turned a quarter turn anticlockwise, not mirrored. The heading tape at
   the pole and the frayed far end stay bare cloth in the banner's base colour, the notches of the fray still show
   the sky through them. The thin top, bottom and tip edges are the base colour. No flicker or stripes where the
   pattern meets the cloth, up close and from 16 to 32 blocks (a faint shimmer of the patterns beyond about 60
   blocks is a known risk, please note the distance if you see it).
3. **Ripple and light.** Watch the cloth for ten seconds in calm (`/pirates wind set 270 1`) and in a strong wind
   (`/pirates wind set 270 12`). Expected: the patterns ripple exactly with the cloth (no pattern sliding over the
   cloth, no gap between them), the folds darken and lighten the patterns as they do the cloth. Build a roof over
   the pole (or test at night with a torch nearby): the patterns get as dark as the cloth, they do not glow.
4. **Many layers.** A banner with six patterns from the loom (for example base colour blue, then a white Cross, a red
   Saltire, a yellow Border, a black Flower, a white Gradient, a gray Bricks). Expected: all six show on the flag,
   later layers over earlier ones in the same order as on the banner block.
5. **Plain dyed banner.** Hoist a plain red banner (no patterns), then a plain black and a plain blue one. Expected:
   the cloth is red, black and blue (with the weave visible), on both faces and the edges. **Please check this one
   first** and tell me whether the colour shows: the code path from the banner to the client's cloth is covered by a
   new GameTest and gave the right colour, so if this step shows a white cloth, send `latest.log` and the banner's
   `/data get entity @s SelectedItem` output.
6. **Hoisting on a tall pole.** On the four-block pole hoist the cross banner (step 1), strike it, raise it and take it
   down. Expected: the whole design (colour and patterns) climbs and runs down the pole with the cloth, rippling,
   never left behind at the top or drawn without its patterns; the light changes as it passes a roofed or shaded
   part of the pole. Hoisting a banner over a flying one: the old design vanishes at once and the new one climbs.
7. **On a ship.** Assemble a small ship with a pole flying the cross banner, sail and turn. Expected: the design stays
   on the cloth, downwind, with the ship.
8. **Upright option.** Client config `flag_visuals.banner_upright = true` (config screen or the client toml; no
   restart needed, it applies on the next frame). Expected: the design stands upright: the Chief band of step 1 now
   runs along the top of the cloth from the pole to the fly, the banner's left edge is at the pole, the design looks
   stretched along the fly and squashed in height. Back to `false`: sideways again.
9. **Other flags.** Merchant, navy and Jolly Roger look unchanged (no banner layers on them).
10. **Performance.** Twenty patterned banners (six layers each) flying in view: no noticeable FPS drop compared with
    twenty plain ones.
