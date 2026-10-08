# Playtest: the ship screen at the helm (work package HGUI1)

The server side (sneak-use by the owner opens the screen with every hand aboard; release, assignment, dismissal and
renaming act; an order posts jobs like the whistle's; a stranger is refused; walking away closes it; the Disassemble
button disassembles; `ship_screen.enabled` off brings back the old sneak-use) is covered by 7 GameTests
(`ShipScreenGameTests`), the rules (rights, hull summary, anchor, allegiance, desertion warning, name cleaning, button
enablement) and the payloads by JUnit. This file checks what only a real client shows: the drawing, the feel of the
buttons, the live refresh and the closing. Please send screenshots and `latest.log` if anything differs.

Setup: a survival world with cheats, default config, GUI scale 2 or 3. A ship you assembled yourself (you own it) with
a sail and its sail winch, a capstan with an anchor, a bilge pump, at least one cannon, two hammocks and a pantry with
some food and a water barrel. Two crew members: `/pirates crew spawn` twice on the deck (or hire them at a desk).
`/give @s pirates_n_ships:captains_whistle`. A second player (or a second client) helps for step 7.

## 1. Opening
1. Stand at the helm with an empty main hand, sneak and right-click it.
2. Expected: a wooden screen like the market's opens (no pause, the world keeps moving behind it). The header plaque
   shows the ship's name in brass (or "Unnamed ship") on the left and the flag's meaning on the right ("no flag",
   "Jolly Roger: friend of the pirates", ...). Three tabs: Ship, Crew, Stations. Screenshot.
3. The ship is NOT disassembled.
4. Sneak-use the helm while holding an item (a block): expected, you steer as before (no screen, no disassembly).
5. Plain use (no sneak) still takes the wheel.

## 2. Ship tab
1. Expected rows, label on the left in faded ink, value on the right: Flag, Captain (your name; with a career title
   "Capt. Steve" style in front if you have one), Hull ("N compartments, dry" on a dry hull), Load ("light, 0.0 kn"
   or blocks/s per your HUD setting), Anchor ("stowed"), Sails ("1: 0 full, 0 reefed, 1 furled"), Crew ("2 aboard, 2
   bunks"), Supplies ("food 3.5, water 2.0, rum plenty days" or similar), Last pay ("no payday yet" on a new ship).
   Nothing overlaps the frame or the Disassemble button at the bottom right. Screenshot.
2. The Name field (on the wood above the parchment) holds the name without your title. Type "Sea Wolf" and press
   Enter (or the Rename button). Expected: green status "Ship named "Sea Wolf"" (with your title in front if you have
   one), the header changes, the nameplate over the ship changes, the HUD shows the new name.
3. Clear the field: the Rename button greys out. Type the current name: greyed too. Hover Rename: tooltip about the
   title.
4. Hoist the sails with the whistle, drop the anchor at sea, let it hold: within a second or two the Sails and Anchor
   rows update without reopening ("down, the ship still moves", then "anchored").
5. Break a hull block below the waterline (on a test ship): Hull turns red ("... 1 flooding, 1 breaches, 0 pumps
   working"); start the pump: "1 pumps working".

## 3. Crew tab
1. Expected: two rows of brass order buttons on the wood (Hoist sails ... Release crew), each with a tooltip; below on
   parchment "Crew aboard (2)" and one row per hand: the name, "morale 70" on the right in green, a second line
   "off duty" or "<station name>: hoist the sails", "hired by you" for hands you hired; Release and Dismiss buttons.
   Screenshot.
2. Click "Hoist sails" with both hands free. Expected: the same action line as the whistle ("Hoist the sails: 0 at
   their stations, 1 jobs posted" or similar) in the footer in green; within a second a hand walks to the winch and
   hoists; the row then shows "Sail Winch: hoist the sails" while it works.
3. Release on that hand: it steps off the winch, the row says "off duty". Release on a free hand is grey (tooltip).
4. "Release crew" frees everyone and ends "Fire at will".
5. Dismiss a hand: "<name> is dismissed", the row vanishes, a plain sailor stands where it was.
6. With `crew.hiring.enabled=false`: Dismiss is grey with the tooltip "Dismissal is turned off together with hiring".
7. Set a hand's morale low (`/pirates crew morale` if available, or starve the crew for two dawns): the morale turns
   amber, then red with "will desert at dawn".

## 4. Stations tab
1. Expected: "Stations aboard (N)" and one row per station (Cannon, Bilge Pump, Capstan, Helm, Sail Winch...): "manned
   by <name> · <order>" in navy ink, or "unmanned" in faded ink, "· open job: hoist the sails" while a job waits.
   Screenshot.
2. Click Man on a free station. Expected: the list turns into "Send whom to the Sail Winch?" with Cancel, and one row
   per free hand with a Send button. Send one: it goes there and stays (it is pinned like a whistle assignment); the
   list is back with the station manned.
3. Cancel returns to the list. Man is grey when no hand is free ("No free hand to send").
4. Sit at a station yourself (if a station lets players man it): its Release is grey ("A player mans it").

## 5. Closing
1. Walk away from the helm: at about 8 blocks the screen closes and the action bar says "You stepped away from the
   helm".
2. Escape and the inventory key (E, unless you are typing in the name field) close it.
3. Disassemble: click it once (it turns "Sure? Click again"), click elsewhere (it resets), then twice: the ship
   disassembles (or refuses with the old message, e.g. moving or tilted, shown in the footer in red) and the screen
   closes with the chat line "Ship disassembled: N blocks placed back".

## 6. Strangers
1. A second player who does not own the ship sneak-uses its helm. Expected: no screen, action bar "This is not your
   ship: only her captain manages her at the helm", the ship stays assembled.
2. A ship without an owner (e.g. after a mutiny): anyone opens the screen; the Captain row reads "none: anyone may
   command her".

## 7. Switched off
1. `ship_screen.enabled=false` in the server config. Sneak-use the helm with an empty hand. Expected: the ship
   disassembles at once, as before HGUI1, and no screen opens.
2. Switch it off while a screen is open: the screen closes within a tick with "The ship screen is turned off on this
   server".
