# Playtest: milestone 4, crew station (spike 4)

Spike 4 (work package D4) puts a crew member at the sail winch. The open question this playtest answers: **does a mob
riding an invisible seat inside a moving ship look right in the client?** The server side is covered by GameTests
(`station/StationGameTests`, 9 tests). Commands need operator rights (cheats on).

Please send back:
- a **short video (20 to 30 s)** of section 3, filmed once from on board and once from the shore;
- screenshots of section 3 step 4 (heeled ship), section 6 (after disassembly) and section 7 (after relog);
- `logs/latest.log` and the chat output.

## 0. Setup
Build the boat of `milestone-3.md` §0 (helm, mast, small square sail `trim=furled`, sail winch on deck) and assemble
it at the helm. Leave at least one free deck block with deck below it next to the winch: the crew member stands on the
first free neighbour of the winch (north, east, south, west, in that order), or on top of the winch if there is none.
Set the wind: `/pirates wind set 270 6` (from the west, astern for a boat with its bow east).

## 1. Get a crew member and the whistle
1. Stand on the deck. `/pirates crew spawn` (or `/summon pirates_n_ships:crew_member ~ ~ ~`).
   - Expected: chat "Crew member spawned". A humanoid with Steve's skin (placeholder) stands there, strolls a bit and
     looks at you.
2. `/give @s pirates_n_ships:captains_whistle`. Expected: the item shows the goat horn texture (placeholder) and the
   name "Captain's Whistle".

## 2. Assign it with the whistle
1. Use the whistle on the crew member. Expected: action bar "Crew Member awaits orders: use the whistle on a station".
2. Use the whistle on the sail winch. Expected: action bar "Crew Member mans the station"; the crew member jumps to the
   free block beside the winch, **standing** (not sitting, not floating, feet on the deck), and stops walking. The
   winch did **not** cycle the sails (the whistle skips the winch's own action).
3. Use the winch with an empty hand. Expected: it still cycles the trim as in milestone 3 (players keep working it).
   Set it back to furled.
4. A second crew member (`/pirates crew spawn`, select it, use the whistle on the same winch). Expected: "Somebody
   already mans this station", the second one stays where it is.

## 3. Orders while sailing (the main check)
1. Sneak and use the whistle in the air while on deck. Expected: action bar "Order: hoist the sails (1 crew carry it
   out)", chat `<Crew Member> Aye, hoisting the sails!`. The sails stay furled for **4 seconds** (default
   `ticks_per_trim_step` 40, two steps), then switch to full at once. The ship starts to move.
2. While the ship sails, watch the crew member from on board for 20 s, then fly or walk to the shore and watch it
   for 20 s. **Look for, specifically:**
   - jitter or flicker of the crew member relative to the deck;
   - the crew member lagging behind the ship (trailing by a fraction of a block when the ship accelerates);
   - the crew member sinking into the deck or floating above it;
   - whether it is visible at all from the shore (the seat is in the plot, the crew member is in the world; both
     must reach your client for it to ride correctly);
   - the name tag or hit box (F3+B) staying on the crew member.
3. Use the hand (no item) on the crew member while sailing. Expected: nothing happens; you can still hit it.
4. If the ship heels or pitches visibly: **expected spike behaviour** is that the crew member stays upright in world
   space and its head stays at the seat point, so its feet swing off the seat by roughly 1.6·tilt in radians blocks
   (GameTest: 1.2 blocks at 43° of pitch). Please screenshot how this looks; it tells us whether we need a custom
   orientation (Sable has `EntitySubLevelUtil.getCustomEntityOrientation`, unused here).
5. Sneak-use the whistle again: "reef the sails", chat "Aye, reefing the sails!", half sail after 2 s. Again: "furl
   the sails", furled after 2 s, the ship slows down.
6. Commands, same effect: `/pirates crew order hoist` (all assigned crew within 64 blocks), then
   `/pirates crew order furl @e[type=pirates_n_ships:crew_member,limit=1,sort=nearest]`.
   Expected: "Order hoist the sails: 1 of 1 crew carry it out". Ordering hoist when the sails are already full:
   chat "The sails are already set, captain (hoist the sails)".

## 4. Release
1. Use the whistle on the seated crew member. Expected: "Crew Member leaves the station"; it stands on the deck where
   it was (not teleported far away, not falling into the water), and starts to stroll again.
2. `/pirates crew release @e[type=pirates_n_ships:crew_member]` on an assigned one does the same.
3. Reassign it to the winch for the next steps (whistle on crew, whistle on winch, or
   `/pirates crew assign @e[type=pirates_n_ships:crew_member,limit=1,sort=nearest] <winch x y z>` with the winch's
   coordinates as F3 shows them).

## 5. Break the winch
1. With the crew member seated, break the winch. Expected: the crew member gets off at once and stands on the deck;
   about half a second later it is released (strolls). No invisible seat left: `/kill @e[type=pirates_n_ships:station_seat]`
   reports "No entity was found".
2. Place the winch again and reassign.

## 6. Disassemble with crew on board
1. Furl, let the ship stop, use the helm to disassemble with the crew member seated.
   - Expected: the crew member stands on the deck block beside the winch in the now static boat, not inside a block,
     not in the water. It is released (strolls). `/kill @e[type=pirates_n_ships:station_seat]` finds nothing.
   - Known spike limit: the assignment is not kept (the reassembled ship gets a new id).

## 7. Relog and unload
1. Reassemble, reassign, hoist and sail a little. Save and quit, reopen the world.
   - Expected: the crew member is back at the winch, seated, at the right place on the ship. An order in progress at
     save time is lost (known spike limit); give it again.
2. Walk or fly about 200 blocks away (out of the ship's chunk range), wait 10 s, come back.
   - Expected: the crew member is still at the winch. Send a screenshot either way: **if it is gone or stands
     in the water, that is the risk noted in the report** (the seat is saved in a plot chunk, the crew member in a
     world chunk).

## 8. Death and config
1. Kill the seated crew member (sword). Expected: it falls off and dies normally, no seat left over.
2. `crew_stations.enabled = false` in the server config (`serverconfig/pirates_n_ships-server.toml`), reload the
   world: an assigned crew member is released at once, the whistle answers "Crew stations are disabled on this server".
3. `crew_stations.ticks_per_trim_step = 100`: hoisting from furled takes 10 s.
