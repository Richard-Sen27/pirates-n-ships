# Playtest: datapack definitions, tags and the config screen (work packages A2, C6, C1, C2, C4)

Headless tests cover loading, parsing and the rules. This checklist covers what needs a real client or a dedicated
server: definitions being synced to clients, a broken datapack file not breaking a reload, and the config screen.
None of this is a gate for other work. Please send `latest.log` (client and server) if anything differs.

## Steps

1. Start the dev client (`./gradlew :neoforge:runClient`) and create a single-player world.
   - **Expected:** no `Skipping … definition` errors in the log, and no network or decoder errors when joining.
2. Run `/reload` in that world.
   - **Expected:** no errors, and the world keeps running.
3. Hold a piece of bread and press F3+H (advanced tooltips), or run `/execute if items entity @s weapon.mainhand #pirates_n_ships:provisions/preserved`.
   - **Expected:** bread is in the tag `pirates_n_ships:provisions/preserved`. No tag loading errors in the log.
4. Open Mods → Pirates 'n' Ships → Config while in the world.
   - **Expected:** the server config lists these sections, each entry with a readable title and a tooltip and no raw
     translation keys: Core, Law, Flags Brig, Wind, Sailing, Provisions, Flooding, Assembly. (More sections appear as
     further packages are merged.)
5. Add a datapack to the world with the file `data/test/pirates_n_ships/test_marker/bad.json` containing `{"weight": 5}`,
   then run `/reload`.
   - **Expected:** the server log shows `Skipping test_marker definition test:bad: …` naming the missing `label` field.
     The reload completes, and you stay connected.
6. Optional, dedicated server: start one with the mod (`./gradlew :neoforge:runServer`, accept the EULA) and join it with the dev client.
   - **Expected:** login works. No disconnect with "Unknown definition type" and no decoder error.

## Known gap
There is no command yet that shows what the client received, so "the client has the definitions" can't be observed
directly. The first feature that uses a synced definition on the client (weapon tooltips or the market screen) will show it.
