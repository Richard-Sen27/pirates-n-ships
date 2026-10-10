# Store page: Modrinth and CurseForge

**Maintainer notes (do not paste this part).** One description for both platforms: paste everything below the
line "PASTE FROM HERE" into the Modrinth description and the CurseForge description (CurseForge: switch the editor
to Markdown first). Written to survive CurseForge's renderer, which differs from Modrinth's:

- No tables, no HTML (`<details>`, `<img width>`, `<br>`, comments) and no task lists: CurseForge drops or mangles
  them. Lists, headings, bold, italics and links work on both.
- Links are absolute (`https://github.com/Richard-Sen27/pirates-n-ships/...`); relative links break on both stores.
- Images: none in the text. Screenshots go into each platform's gallery (the list is in the README, "Screenshots").
- Italics are reserved for in-game names (`*Sail Winch*`), because `tools/check_release_texts.py` checks every italic
  span against the mod's item, block and mob names; use bold for emphasis.
- Every feature line was checked against `docs/progress.md` on 2026-10-10. Run
  `python3 tools/check_release_texts.py` after any edit.
- The status paragraph names the version: 0.1.0 is the first public release (the human, 2026-10-10), released
  before the playtest rounds; 1.0.0 follows them and the wood variants. Update it with each release.

PASTE FROM HERE

---

# Pirates 'n' Ships

So you mean to go to sea. Lay a hull of planks, set a *Helm* on her deck and she becomes a real ship: she floats,
heels in a gust, keeps her hold dry below the waterline, and when a cannonball holes her she floods compartment by
compartment and goes down unless her crew pump and patch. Sail her by the wind, sign on a crew and whistle your
orders, trade in harbours, run from the navy or serve in it, cross blades with pirates in a duel of timing, and
meet sharks, waterspouts and the kraken out on the deep water. Every captain before you learned it the hard way;
you get the *Captain's Almanac*.

## Ships and sailing

- **Ships built from any blocks.** No blueprints and no fixed types: build a hull your way, use the *Helm*, and
  everything joined to it sails as one ship (physics by Sable). Sneak-use the helm to turn her back into blocks.
- **Dry hulls.** No water inside the hull and no swimming below deck, down to slabs, stairs and trapdoors.
- **Flooding and sinking.** Breaches let the sea in, water spreads between compartments, the ship lists and sinks.
  Man the *Bilge Pump* and nail on a *Hull Patch*. A hull shot in two drifts apart as two bodies, and the
  *Shipwright's Toolkit* joins the pieces again.
- **Sailing by the wind.** A world-wide wind that follows the weather. Square sails between two *Yards*, triangular
  sails on a *Rope* between *Cleats*, set furled, half or full at the *Sail Winch*. Dye the canvas or hang a banner
  on a large sail.
- **Steering and handling.** Turn the wheel at the helm with the mouse; rudder, keel, heel in a gust, and ships
  that float level as built. Drop the anchor at the *Capstan*, tie up at a *Mooring Ring*, or row with *Oars*
  and an *Oak Oar Bench* when the wind dies.
- **A sea that moves.** Visible waves that rock and heave your ship and spill into low hatches, wind streaks in the
  air, a wake at the bow and stern.
- **The ship around you.** A compass, wind, speed and rudder panel, a flood strip for every compartment, and a ship
  screen at the helm for ship and crew.
- **Make her yours.** A *Name Board* across the hull with her name readable from afar, figureheads, flags on tall
  flagpoles, a *Crow's Nest*, *Ratlines* and rope nets up the mast, railings, bulwarks and gun ports, a *Ship's Bell*,
  lanterns, stern windows, *Hammocks* and a *Sea Cot* to sleep aboard. Cargo weighs her down and trims her.
- **Ships to order.** No hand for building? A seafarer village's shipwright builds you a sloop for doubloons.

## Crew

- **Hire hands** at the *Harbor Master's Desk* and give orders with the *Captain's Whistle*: hoist, reef and furl,
  pump, load, fire, fire at will, drop or weigh anchor, row, board, and release a hand from its station.
- **They work the ship themselves.** Free hands take open jobs on their own, walk to their stations, keep a course
  at the helm, load the guns from the powder and shot nearby, and a lookout in the crow's nest calls out ships, land
  and sharks with their bearing.
- **Keep them fed and paid.** Wages, rations from the *Pantry*, water from the *Water Barrel*, rum, morale, a
  *Hammock* each; scurvy after weeks without fresh food or *Limes*; desertion at port, and mutiny where the server
  allows it, if you starve them.

## Combat

- **Swordplay that is about timing.** The *Rapier*, *Cutlass* and *Saber* slash, thrust, guard, parry and riposte
  on a stamina bar, animated in first and third person. Pirates and navy officers fight the same way, telegraph their
  blows and feint.
- **Firearms.** The *Pistol* and the *Musket*: load, aim, fire, and mind the rain.
- **Cannons.** A two-block *Cannon* on a carriage: powder, then shot, then elevate and fire. *Cannonballs* hole
  hulls, *Chain Shot* tears sails and rigging, *Grapeshot* sweeps a deck. A *Swivel Gun* mounts on the rail.
- **Grapple and board.** Throw the *Grappling Hook* or fire it from a musket, haul the other ship alongside, slide
  across on the rope, or lay a *Boarding Plank* between the decks and order "Board!". Boarding nets along the rail
  hold the boarders back.

## The world

- **Places on the coast and the sea floor.** Seafarer villages with harbours and a harbour master, pirate islands
  with buried treasure, navy outposts with their garrison, and wrecks on the bottom of the sea.
- **A living sea.** The navy, the pirates and the merchants keep their own state: convoys sail the sea lanes and move
  prices, navy patrols hunt the wanted, pirates raid the settlements, and their ships appear around you as real
  ships you can fight, plunder or capture.
- **Trade.** *Doubloons*, trade goods, markets with prices that move with supply and demand, delivery contracts,
  harbour dues, and fences who take plunder. Ship your goods in a *Cargo Crate* or *Cargo Barrel*.
- **Treasure.** Buy a *Treasure Map* from a fence or find one in a wreck, follow the X and dig.
- **Charts.** Every coast you sail past is drawn on your own *Chart*, with markers, and onto *Map Tiles* that join
  into boards for everyone to read.
- **The deep.** *Sharks* that hunt swimmers, the *Kraken* that grips hulls and breaks masts, waterspouts in
  thunderstorms and drifting whirlpools.
- **Seafarers.** *Pirates*, *Sailors*, *Navy Soldiers* with muskets, *Navy Officers* with sabers, and named
  pirate captains with a price on their head who accept a duel.
- **Life at sea.** Cold water chills a swimmer, rum warms (*Warm*), swimming makes you hungry; carry your *Sea Chest*
  on your back, or float it and row it with a *Paddle*.
- **Quests** at the harbour desks: hunts, deliveries, treasure and escorts.

## The law

- **Crime and bounties.** A criminal score, navy and player bounties, the *Bounty Proof* and the *Notice Board*.
- **Prisoners.** Take a beaten foe in *Shackles*, lock them behind *Brig Bars* and a *Brig Door* with its *Brig Key*,
  turn them in, ransom them, or press-gang a sailor into your crew.
- **Colours.** Fly the *Navy Flag*, the *Merchant Flag* or the *Jolly Roger*; strike your colours to surrender; fly
  false colours and hope nobody sees through them.
- **Careers.** Climb the navy ranks from Midshipman to Admiral, or the pirate ladder from Buccaneer to Pirate Lord,
  or sail under a letter of marque. Reputation with the navy, the pirates and the villagers changes prices, temper
  and who fires on you.

## The Captain's Almanac

- Every new player gets a *Captain's Almanac* on first join: a leather-bound tome to read in hand or lay open on a
  table, with an index, chapter bookmarks, illustrations, crafting recipes on the page, and **Show me** buttons that
  act each mechanic out on a little model ship (Ponder scenes, also on the Ponder key over the item).
- With JEI installed, every block and item gets an information page and the markets get their own JEI pages.

Every gameplay feature has a server config switch, and every strength and rate is a config value.

## Status

**Version 0.1.0 is the first public release.** It ships before its in-game playtest rounds, so expect rough edges
and please report what you find on the
[issue tracker](https://github.com/Richard-Sen27/pirates-n-ships/issues). Wood variants of every wooden block (all
vanilla woods and those of known wood mods) are in progress for the next release; 1.0.0 follows once the playtest
rounds are done.

## Requirements

- Minecraft 1.21.1, Java 21.
- **NeoForge** 21.1.228 or newer, **or Fabric** with Fabric Loader 0.19.3 or newer, Fabric API (built against
  0.116.12+1.21.1) and Forge Config API Port 21.1.3 or newer (Mod Menu recommended for the config screen).
- **Sable** 2.0.6 or newer (below 3.0): the ship physics.
- **GeckoLib** 4.9.3 or newer: the animated mobs.
- **Player Animation Library** 1.1.6 or newer: the sword and gun animations (on NeoForge only needed on the client).
- Bundled, nothing to install: Ponder 1.0.87 and Flywheel 1.0.6 (the Almanac's Show me scenes).
- Optional: JEI 19.39.0.372 or newer.
- Install the mod and its dependencies on the server and on every client.

## How to start

1. **Read the Almanac.** You get one the first time you join a world; use it to read. Lost it? Craft a new one from
   a book, two leather and two copper ingots.
2. **Build your first ship.** Lay a wooden hull in the water with a one-block gap to the shore, place a *Helm*,
   raise a mast with two rows of *Yards* across it, add a *Sail Winch* and a *Capstan*. Use the helm to launch her,
   use the winch to set sail, hold right-click on the helm to steer. The Almanac's "Your first ship" chapter walks
   you through it, with a Show me scene.
3. **Find a harbour.** Seafarer villages along the coast have a harbour master: hire crew, trade, take contracts and
   quests, or order a ship.

## Known limits

- Most of the work aboard has no sound of its own yet (sails, pumping, the wheel, flooding); the sea music, creaks,
  swords, guns and cannons do.
- With a sword in hand the right mouse button is the guard and parry: swap to another item to open doors or use the
  helm.
- Feints are for the NPC duelists only; players cannot feint, and there is no directional attack and parry mode.
- Triangular sails are not torn by chain shot (only their rope is cut), and the *Swivel Gun* fires neither chain shot
  nor grapeshot.
- Railings, bulwarks and oar benches come in every vanilla wood and in the woods of eleven wood mods when installed; the other wooden blocks follow.
- Not built yet: a ship's logbook, hull paint, a wanted poster of your own.
- On Fabric the config screen needs Mod Menu (otherwise edit the config files).
- JEI is held at 19.39.0.372 until the mod moves to a newer NeoForge.
- Big fleets and many NPC ships cost server time; give a busy server room.

## Licence

PolyForm Noncommercial 1.0.0: free to use, modify and share for any noncommercial purpose. The full text is in the
[LICENSE](https://github.com/Richard-Sen27/pirates-n-ships/blob/main/LICENSE). Music and sound effects are used
under their own licence (below). Source, issues and the full guide:
[github.com/Richard-Sen27/pirates-n-ships](https://github.com/Richard-Sen27/pirates-n-ships).

## Credits

- **Music** (Pixabay Content License): "Leave Her Johnny" by Alexander Nakarada; "The Ghost Of Gallows Reef" by
  kaazoom; "There Be Pirates - The Quest" and "There Be Pirates - Lost in the Deep" by Pixabay user 44363205.
- **Sound effects** (Pixabay Content License): sword sounds by DRAGON-STUDIO, sword swipes, the empty gun and the
  artillery by freesound_community, the pistol shot by mrfriends, the cannon shot by magiazzz, and a hull creak
  recording from Pixabay. The full list with sources:
  [docs/credits.md](https://github.com/Richard-Sen27/pirates-n-ships/blob/main/docs/credits.md).
- **Libraries:** Sable by ryanhcode (ship physics), GeckoLib, the Player Animation Library by ZigyTheBird, Ponder
  by the Creators of Create and Flywheel by the Engine Room (both MIT, bundled), JEI by mezz, Forge Config API Port
  by Fuzs, NeoForge, Fabric, Parchment mappings, and the MultiLoader-Template by jaredlll08.
- Made by Richard Senger.
