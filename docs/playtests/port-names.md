# Playtest: port names (work package NAME3)

The names themselves (determinism, kinds, uniqueness over 1,000 ports, clean word lists), the command lookup and the
registry's naming of new and old ports are covered by JUnit (`PortNamesTest`, `PortLookupTest`, `PortRegistryNamesTest`);
the server side by GameTests (`PortNameGameTests`, the greeting in `HarborMasterGameTests`, the desk title in
`HarborDeskGameTests`, quest texts in `QuestGameTests`, contracts in `TradeGameTests`, lane replies in
`LaneVoyageGameTests`, an old registry in `SaveCompatGameTests`, and the generated ports of `WorldGameTests`,
`NavyOutpostGameTests` and `PirateIslandGameTests`). The client side (the synced names on screens, a dedicated server
with a remote client, the config toggle on a running server) needs a client.

## NAME3

### Setup
A new world, creative. `/pirates world ports` lists every known port as "Name (id) (kind, climate) …"; fly to a
seafarer village, a navy outpost and a pirate island (`/locate structure pirates_n_ships:seafarer_village`,
`…:navy_outpost`, `…:pirate_island`; `/pirates world ports` again after each one generated).

### Steps
1. **Names by kind:** `/pirates world ports`: villages read like "Port Marlow", "Saltmarsh Quay", "Greyhaven Harbour",
   "Falmouth", "St. Brendan's" or "Kettle Cove"; outposts "Fort Anson", "Fort St. Anne" or "Gullsend Battery"; islands
   "Bone Cay", "Dead Man's Reach", "Rum Row" or "Kidd's Folly". No two lines share a name.
2. **Harbor master:** right-click a village's harbor master: chat reads `Harbor Master: "Welcome to <name>, captain.
   Goods, contracts or a new ship?"`. At an outpost: `"You stand in <name>, captain. …"`; at an island `"This is
   <name>. …"`.
3. **Desk title:** the market screen's header shows the port's name (not "Village 1192 1530").
4. **Contracts and quests:** the Contracts tab's destinations and the Quests tab's titles ("Deliver 8 × Sugar to
   <name>") and details ("(from <name>)") use names.
5. **Commands by name:** `/pirates world port "<a village's name>"` prints that port; `/pirates world port
   <its id>` and `/pirates world port village_<x>_<z>` print the same. Tab completion after `/pirates world port `
   offers the ids and the quoted names. Type `/pirates world port "Port"` while two names start with "Port":
   "Several ports fit, which one? …" lists them.
6. **Voyages and lanes:** `/pirates world voyages lane "<village>" "<island>"` answers "Lane <village> -> <island>: …"
   or "No lane from <village> to <island> …: the sea around <village> … never reaches <island>"; `/pirates world
   voyages` lines read "<from> -> <to>".
7. **Restart:** quit and reopen the world: every port keeps its name (`/pirates world ports`).
8. **Old world:** open a world made before this build (one with villages already registered): `/pirates world ports`
   shows names for the old ports too; after a restart they are the same names.
9. **Config off:** set `world.ports.names = false` in the server config (or the in-game settings), then talk to a
   harbor master: the greeting names the readable id ("Village 1192 1530"); `/pirates world ports` shows ids only.
   Rejoin: the desk title and the quest texts show the readable id too. Set it back to true and rejoin: names again.
10. **Dedicated server (optional):** on a dedicated server with a second client, the desk title and quest texts on the
    remote client show the names, too (they come from the sync on join).

### Expected problems
- A port registered with `/pirates trade port <name> …` has no registry entry, so it keeps its readable id.
- The world chart's port labels are CHART2's work (it reads `Port.name()`).
