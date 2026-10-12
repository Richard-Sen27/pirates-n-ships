# Playtest: advancements (ADV1)

What the GameTests cannot see: the tab and its art in the advancements screen, the toasts, the hidden entries and the
experience in a real game. NeoForge first, then Fabric (`./gradlew :fabric:runClient`). The tree is in
[`../advancements.md`](../advancements.md).

## Setup
- A new survival world (cheats on for the commands below), default config.

## 1. The tab
1. Join the world and press L.
   - **Expect:** a tab "Pirates 'n' Ships" with the helm as its icon and a spruce plank background; its root
     "Pirates 'n' Ships" is already earned (no toast is needed for it, but one may show).
   - **Expect:** eight branches below the root: a helm (Shipwright), a bandana (Crew), a doubloon (Trade), a cannon
     (Combat), a mooring ring (The world), a bounty proof (The law), a navy hat and a pirate hat (Career) and the
     Almanac. Titles and descriptions read as English text, never as `advancements.pirates_n_ships.…` keys.
2. Hover a few entries: descriptions fit their boxes and are one sentence.

## 2. The first few
1. Use the Captain's Almanac from your first-join gift.
   - **Expect:** a toast "Required Reading" and a few experience points (5).
2. `/give @s pirates_n_ships:spruce_helm`.
   - **Expect:** "Hand on the Wheel" and "Fine Joinery" toasts.
3. Build a small hull in water with the helm and assemble it (use the helm).
   - **Expect:** "Launched!".
4. Add a yard sail and a winch, use the winch.
   - **Expect:** "Canvas Aloft"; with some wind and you aboard, "Following Wind" within a few seconds of making way.
5. `/give @s pirates_n_ships:doubloon` does not count (nothing was earned); sell a trade good at a market instead.
   - **Expect:** "Pieces of Eight".
6. `/pirates career` commands or `/advancement grant @s only pirates_n_ships:career/admiral` show the navy column fill.

## 3. Hidden entries
1. Before anything else, look at the Combat and The world branches.
   - **Expect:** no "Prize Taken", no "Release the Kraken" and no "X Marks the Spot"; nothing of them shows (not even
     a dark frame) until earned.
2. `/advancement grant @s only pirates_n_ships:world/treasure`.
   - **Expect:** "X Marks the Spot" appears with its toast.

## 4. Config
1. Set `advancements.enabled` to false in the server config, use the Almanac in a fresh world.
   - **Expect:** no "Required Reading" toast; a helm in the inventory still earns "Hand on the Wheel" (a vanilla
     criterion).
2. Set `advancements.experience_scale` to 3 and earn a task.
   - **Expect:** 15 experience points instead of 5.

Report: screenshots of the tab (whole tree zoomed out), one toast, and anything that shows a raw key or a missing icon.
