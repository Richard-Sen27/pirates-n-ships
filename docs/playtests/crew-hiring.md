# Playtest: hiring crew at the harbor desk (work package CRW1)

The server side (today's candidates per port, the eligibility of sailors, pirates and navy ratings, the moored-ship
check, the bunk cap, the fee, the recruit on the deck with its name, start morale and hirer, dismissal rights, the
whistle's sneak-use, the desk session check and `crew.hiring.enabled`) is covered by 7 GameTests, and the rules
(candidates per day, the eligibility matrix, the cap, dismissal rights, the deck height) by JUnit. This file checks what
only a real world shows: the Crew tab's look, where the recruit lands on a real ship at a real quay, and the whistle's
feel. Please send screenshots and `latest.log` if anything differs.

Setup: a survival world with cheats, default config. A ship of your own (assembled by you, so you are its owner) with
at least two hammocks in it. `/give @s pirates_n_ships:doubloon 200`, `/give @s pirates_n_ships:captains_whistle`.
`/pirates rep set @s villagers 0`, `/pirates rep set @s pirates 0`.

Defaults: 3 candidates per port and day, fees sailor 10, pirate 20, navy rating 15, wage 2 a day, ship radius 32 around
the port's area, one crew member per bunk (hammocks × `crew.max_crew_multiplier`), pirates from infamy Buccaneer.

## 1. The Crew tab
1. Sail to a seafarer village and tie up at its quay. Walk to the harbor master's desk and use it.
2. Expected: a fifth tab "Crew" next to Goods, Contracts, Orders and Quests; all five tabs fit in one row without
   overlapping the frame. Shrink the window (or raise the GUI scale) until the screen is narrow: the tabs get narrower
   and still fit (labels may be cut). Screenshot both.
3. Open the Crew tab. Expected: the heading "Looking for a berth", a line "Your ship: <name or unnamed>, crew 0 / N"
   (N = your bunks), then three sailors with a fishing-rod icon, each "Sailor, fee 10, wage 2 a day" and a Hire
   button.
4. Close and reopen the desk: the same three names.

## 2. Hiring
1. Click Hire on the first sailor. Expected: status "<Name> signed on and is aboard your ship" in green, the doubloons
   in the header drop by 10, the sailor leaves the list, the ship line shows crew 1 / N.
2. Look at your ship. Expected: a crew member named <Name> stands on the main deck (not in the hold, not on the mast
   top or a cabin roof), on the side nearest the desk, facing toward you. Screenshot.
3. Use the whistle on it (no sneaking). Expected: its crew line in chat, morale 70.
4. Hire until the ship line reads crew N / N. Expected: the Hire buttons turn grey with the tooltip "No free hammock
   aboard".
5. Spend your doubloons down below 10 (or drop them in a chest): the Hire buttons are grey, "Not enough doubloons".

## 3. No ship at the port
1. Sail your ship well away (more than 32 blocks beyond the village's edge) and walk back to the desk (or use a desk of
   another village).
2. Expected: the tab says "No ship of yours is moored here" in red; Hire buttons grey.

## 4. Pirate islands and navy outposts
1. At a pirate island's desk (pirate reputation 0, no infamy): the tab shows your ship line and
   "Pirates sign on only with a friend of the pirates or a captain of some infamy"; no candidates.
2. `/pirates career infamy @s buccaneer` (or `/pirates rep set @s pirates 60`) and reopen: three pirates with a sword
   icon, fee 20.
3. At a navy outpost without a rank: "Navy ratings sign on only with an enlisted officer". After enlisting as
   Midshipman (or `/pirates career navy @s midshipman`): three navy ratings with a shield icon, fee 15.

## 5. Dismissal with the whistle
1. Sneak and use the whistle on one of your hired crew. Expected: action bar "<Name> is dismissed"; it turns into a
   plain sailor with the same name where it stood, and it no longer counts in the ship line at the desk.
2. Ask a second player (or another account) to sneak-use a whistle on one of your crew. Expected: "Only the captain or
   the one who hired <Name> can dismiss them", nothing changes.
3. Plain use (not sneaking) still selects or releases as before.

## 6. Commands and toggle
1. `/pirates crew hire pirate` while standing at a village with your ship moored: a pirate joins for free (operators
   skip the fee and the standing check).
2. `/pirates crew dismiss @e[type=pirates_n_ships:crew_member,limit=1,sort=nearest]`: it becomes a sailor.
3. Set `crew.hiring.enabled = false` in the server config and reopen a desk: no Crew tab; sneak-using the whistle on
   crew behaves like plain use.
