# Playtest: world simulation (milestone 20)

One section per work package (`docs/plans/world-simulation.md`). The rules are covered by JUnit tests and GameTests;
these steps check them in a real world. Please send `latest.log` if anything differs.

## WS1: faction state

The faction state (aggression and wealth of Navy, Pirates and Merchants, tension between each pair) is saved per world
in `data/pirates_n_ships_factions.dat`. Nothing in the world reports events yet except the command below; patrols,
convoys and raids report theirs from WS2 to WS5.

Setup: `./gradlew :neoforge:runClient`, a new creative world with cheats, operator.

1. **Start state.** `/pirates world factions`
   - **Expected:** three lines:
     `Navy: aggression 0.30, wealth 10000, tension with Pirates 0.20, with Merchants 0.00`,
     `Pirates: aggression 0.30, wealth 2000, tension with Navy 0.20, with Merchants 0.00`,
     `Merchants: aggression 0.30, wealth 10000, tension with Navy 0.00, with Pirates 0.00`.
2. **An event.** `/pirates world factions report pirate_killed_by_navy` (tab completion lists every event).
   - **Expected:** "Reported pirate_killed_by_navy", then the three lines with Navy–Pirates tension 0.25, Pirates'
     aggression 0.32 and Navy's 0.31. `report convoy_delivered` raises the merchants' wealth by 100 and the navy's by 20.
3. **Decay over a day.** Report `pirate_killed_by_navy` four more times (tension 0.45), then `/time add 24000`.
   - **Expected:** within a second `/pirates world factions` shows Navy–Pirates tension 0.40 and every aggression 0.05
     closer to 0.30. Sleeping through a night does the same once. `/time add 72000` decays three days at once.
4. **Survives a restart.** Note the values, save and quit, reopen the world, run the command.
   - **Expected:** the same values; no extra decay from reloading.
5. **Toggle.** Config `world_simulation.factions.enabled = false` (or `world_simulation.enabled = false`).
   - **Expected:** `report` answers "... changed nothing", `/time add 24000` leaves the values as they are.
6. **Reset.** `/pirates world factions reset` puts the start state back.
7. **A deed moves the factions (WS1b).** `/pirates world factions reset`, then in survival kill a navy soldier
   (`/summon pirates_n_ships:navy_soldier`), and sell plundered cargo at a pirate fence (or plunder a merchant ship).
   - **Expected:** after the kill, Navy–Pirates tension 0.28 and Navy's aggression 0.35 (the first hit counts as
     attack_navy, the death as kill_navy); after the sale, the pirates' wealth up by 50. With your navy reputation at 0
     or above (`/pirates rep`, set it with `/pirates rep set`), killing a pirate raises
     Navy–Pirates tension by 0.05; at −1 or below it leaves the factions alone. With `reputation.enabled = false` or
     `world_simulation.factions.enabled = false` no deed changes the values.
8. **Later (once WS4b exists):** let a navy patrol kill a pirate; the Navy–Pirates tension rises by 0.05 per kill.
