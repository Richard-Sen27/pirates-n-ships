# Playtest: flags (work package E1c)

The rules (delay, striking, giving flags back, banners, drops, save and reload, the allegiance query) are covered by
JUnit and GameTests. This checklist covers what only the client shows: the look of the flag on the pole from every
side, the feedback messages, the wind direction, and multiplayer sync. Textures are generated placeholders
(`tools/gen_flag_textures.py`), so judge readability, not beauty.

Setup: `./gradlew :neoforge:runClient`, a **survival** world with cheats on (steps 1 to 9), default config
(`hoist_delay_ticks = 60`, i.e. 3 seconds; section `flags` of the server config).
Please send screenshots of steps 3 and 7, and `latest.log` if anything goes wrong.

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
3. **Look from all sides.** Walk around each pole, look from below and from above.
   - **Expected:** a 2-pixel-thick cloth in the top half of the block, sticking out from the pole to one side,
     with the hoist edge (brown) at the pole. White with a red stripe (merchant), blue with a white cross (navy),
     black with a skull and crossbones (Jolly Roger). From the other side the design is mirrored. The parts of the
     block around the cloth are transparent (no black or white box). The pole still looks like the old flagpole.
     Edge-on, the cloth is nearly invisible (its thin edges are transparent on purpose).
4. **Change a flag.** Right-click the merchant pole with the navy flag.
   - **Expected:** after 3 seconds the navy flag flies and the merchant flag is back in your inventory.
5. **Cancel.** Start hoisting a flag, and within the 3 seconds right-click the pole with an empty hand.
   - **Expected:** "Stopped working the flagpole", the flag comes back to your inventory, the pole keeps what it had.
6. **Strike and raise.** Right-click a flying flag with an empty hand, wait 3 seconds; then again.
   - **Expected:** "Striking the colors...", then "Colors struck: you signal surrender" and the cloth disappears
     (the pole keeps the flag: `/pirates flag get <pos>` says "struck"). The second time: "Raising the ..." and the
     same flag flies again.
7. **Patterned banner.** Make a banner with a few patterns on a loom and right-click a pole with it.
   - **Expected:** after 3 seconds a **generic custom cloth** appears (tan with a gold edge and a red mark). It does not
     show the banner's patterns (known limitation: the block model can't). `/pirates flag get <pos>` says
     "custom flag, flying".
8. **Take down.** Sneak and right-click the banner pole with an empty hand, wait 3 seconds.
   - **Expected:** "Took down the flag", the cloth disappears and the banner is back in your inventory **with its
     patterns** (place it to check).
9. **Break the pole.** Hoist a flag, then break the pole with an axe (survival).
   - **Expected:** the flagpole and the flag drop as items. Break one in the middle of a hoist: both the old and the
     pending flag drop.
10. **Wind.** Note which way the flags point. Run `/weather thunder`, then `/time add 6000` a few times, waiting
    about 10 seconds after each (flags check the wind every `wind_update_interval_ticks = 200` ticks).
    - **Expected:** all flags in one area point the same way, **downwind**, in 90° steps: if the wind HUD/indicator
      (or `/pirates wind` if available) says the wind blows toward the south-east, the cloth points south or east.
      When the wind turns past a 45° boundary, the flags swing to the new side within about 10 seconds. A freshly
      hoisted flag points downwind at once. There is no fluttering animation in this version.
11. **Relog.** Hoist a flag, strike another, leave the world and load it again.
    - **Expected:** the same flags fly, the struck one is still struck (`/pirates flag get`), nothing dropped.
12. **Commands.** `/pirates flag set <pos> jolly_roger`, `/pirates flag strike <pos>`, `/pirates flag raise <pos>`,
    `/pirates flag set <pos> none`, `/pirates flag get <pos>` on a non-pole block.
    - **Expected:** instant changes with a chat confirmation; `set` drops the old flag at the pole; the last one says
      "That block is not a flagpole".
13. **Optional, second player** (LAN or a dedicated server): the second player stands next to the poles while the
    first hoists, strikes and takes down flags.
    - **Expected:** the second player sees the same flag, at the same time, pointing the same way.
