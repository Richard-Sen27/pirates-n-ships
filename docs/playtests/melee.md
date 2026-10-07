# Playtest: swordplay input and stamina HUD (work package G6, milestone 7)

The rules, the server-side resolution and the payload handling are covered by JUnit tests and GameTests. What only a
client shows: the feel of the inputs, the HUD, vanilla being untouched, and two players seeing each other.

Setup: `./gradlew :neoforge:runClient`, survival, a cutlass, rapier or saber, some zombies and a second player if you
can (LAN). No animations exist yet (milestone 8); the hand swings vanilla-style. Please send `latest.log` if anything
differs.

## Steps
1. **Slash:** quick left click on a mob. One hand swing; the mob takes damage after a short wind-up (the slash starts on
   release, up to a quarter second after the press). Clicking a block never breaks it.
2. **Thrust:** hold left click about half a second, release. Swing on release, more damage and longer reach (the rapier
   about 3.6 blocks). Holding left click on a block never breaks it.
3. **Guard:** hold right click while a zombie or the other player hits you from the front. Damage is reduced, stamina
   drains while held and per blocked hit, the bar goes dark from the right.
4. **Parry:** a quick right tap just before a hit lands. No damage, the attacker staggers, white markers blink at both
   ends of the bar for about a second.
5. **Riposte:** a left click during the markers does bonus damage.
6. **Failed parry:** a tap with no incoming hit costs stamina and shows the grey lockout block; tapping again at once
   gives a red flash and "Can't parry again so soon".
7. **Stamina:** spam attacks until the bar is low and orange, then slash: red flash and "Too exhausted". After about a
   second idle the bar refills.
8. **Stagger:** when you are parried, the bar turns violet with a shrinking line above it and inputs flash
   "Staggered".
9. **Vanilla unaffected:** with an iron sword or an empty hand, both buttons behave as vanilla (break blocks, open
   chests, attack with cooldown), and there is no HUD.
10. **System off:** `melee.skill_based_combat = false`: mod swords behave like vanilla swords, no HUD.
11. **Screens:** while guarding, open the inventory; on closing, the guard is down (no stamina drain).
12. **Config:** change `melee_hud` scale and offsets and the `melee_input` thresholds in the config screen; each takes
    effect.
13. **Two players:** B attacks A while A parries. Both see the hand swings, A's HUD is right, B's refusal flashes
    appear only on B's screen.
14. **On a moving ship:** repeat 1, 3 and 4 while sailing. Hits register, no rubber-banding from the cancelled input.

## Also worth a look
- Does the parry window feel fair with the parry firing on release (up to 3 ticks after the press)?
- Is losing right click for doors, chests and the helm while holding a sword acceptable, or should use pass through
  when you look at something usable?
