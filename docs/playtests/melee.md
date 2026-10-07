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


## G12: animations (placeholders)
1. **Third person** (F5 or a second client): slash = arm rises high to the right, sweeps down across the body, droops
   back; thrust = arm draws back, punches forward, pulls back; guard = sword raised across the chest, off hand forward,
   held, lowering on release; parry = quick upward-outward flick; stagger = torso and head lean back, arms out;
   riposte = low side flourish instead of the high wind-up. Each animation ends exactly when its phase does; compare a
   fast and a slow weapon.
2. **Rotation signs are unverified:** if arms swing backwards or the sword points the wrong way, report which
   animation; the thrust and guard item rotations are the most uncertain.
3. **First person** (`melee_animations.first_person = AUTO`, no camera mods): the animated right arm and sword replace
   the vanilla hand during combat phases and the vanilla hand returns at idle. With `OFF`: vanilla hand and swing,
   third person still animates. With First Person Model or Real Camera installed, AUTO behaves like OFF.
4. **Left-handed** (main hand left): mirrored onto the left arm in both views; check what another client sees.
5. **`melee_animations.enabled = false`:** only the vanilla swing.
6. **Startup:** without PAL installed, NeoForge shows the missing-dependency screen (expected: PAL is a required client
   mod). A dedicated server starts with or without it.
7. **At sea on a rolling ship:** first-person arms line up with the camera; third-person animations look right on a
   moving deck.


## F9: the Blockbench animations (replace the G12 placeholder checks)
1. **Third person, saber, standing still.** Slash: the arm rises to the right with the blade upright, sweeps
   horizontally across to the left, settles; nothing swings backward. Thrust: the arm pulls back low with the blade
   level, punches straight forward with the torso leaning in, pulls back. Guard: blade raised diagonally across the
   chest, off hand forward; releasing lowers it smoothly. Parry tap: a quick upward-outward flick. Staggered: lean back
   with arms out, then rest. Riposte after a parry: a low twirl instead of the high wind-up.
2. **Phase transitions.** Compare the rapier (fastest) with the cutlass (slowest): no visible jump between wind-up,
   active and recovery, or into idle. Expect a one-frame jump only on a slash riposte.
3. **Sword orientation (most uncertain).** From the side in F5: at the thrust draw and lunge the blade is close to
   level and points at the target; in the guard it points up-left in front of the body, not into the head. Report any
   animation whose blade points backward or down.
4. **Shoulders and lean.** During the slash twist and the thrust lean, arms and head stay attached to the torso and
   the hips stay over the legs.
5. **First person** (`first_person = AUTO`, no camera mods): the slash arc crosses the view right to left, the thrust
   pushes forward along the view with the blade level, the guard blade sits diagonally in the lower left; say whether
   the slash wind-up arm leaves the frame.
6. **Left-handed:** every animation mirrored onto the left arm, including the twist direction and the guard leaning
   right; check on a second client too.
7. **Sneaking while attacking (known risk):** the upper body probably pops to standing height on crouched legs. Report
   how bad it looks.
8. **Walking while attacking:** the legs keep walking; the left arm stops its walk swing while animated.


## P7: sword sounds (turn subtitles on)
1. **Swing:** slash at the air with a cutlass: one whoosh as the blade comes through, not on the click ("Sword
   swings"); a thrust whooshes audibly higher.
2. **Hit, flesh:** slash a zombie or a pirate: a thud at the target ("Sword hits").
3. **Hit, armour:** hit a mob or player wearing a chestplate: a metallic ring ("Armour rings"); with only a helmet it
   thuds.
4. **Parry:** parry a pirate's hit: a loud clash at you ("Blades clash"), no hit thud; parrying a zombie's punch
   clashes too.
5. **Guard:** hold guard against a pirate: a quieter, lower clash per blocked blow; when the guard breaks, a low thud
   follows.
6. **Stagger:** thrust into a pirate during its recovery: the hit thud plus a deeper, quieter thud (say if it sounds
   muddy).
7. **Feint:** with `melee.npc_skill_multiplier` 5, an aborted wind-up gives a faint high whoosh and no full swing.
8. **Drawing:** scroll from an empty slot onto a cutlass: one draw sound ("Sword drawn"); straight on to a rapier
   quickly: no second sound; an iron sword: none; logging in with a sword in hand: none; a second player hears
   your draw.
9. **With a pirate:** its swings and your guard and parry clashes come from the right positions.
10. **Toggle:** `melee.sounds.enabled = false` silences everything; `volume = 0.3` makes it quieter.


## P8: one sound per attack
1. **Miss:** slash into the air: one whoosh at the end of the swing, nothing during the wind-up; a thrust into the air
   whooshes once, audibly lower.
2. **Hit:** slash an unarmoured zombie or villager: one slice, no whoosh before it; subtitles show only "Sword hits".
3. **Thrust:** one heavier slice.
4. **Blade on blade:** hit a pirate while it winds up: one clang ("Blades clash"), not a slice.
5. **Parry:** one loud clang; over many parries the six variants vary without sounding like different weapons (say
   if one is noticeably weak).
6. **Guard:** a quieter, lower clang per blocked blow.
7. **Armour:** an iron chestplate rings.
8. **Pirate fight:** never two sword sounds for one attack, no bone cracks anywhere, feints silent.
9. **Drawing** a sword sounds as before.


Addendum (A2): slashing into the air plays one short blade whoosh that varies from swing to swing, no vanilla sweep, subtitle "Sword misses"; thrusts into the air are clearly lower; the miss should sit at the level of a hit on a mob (say if too quiet); listen for clicks at the start or end of a whoosh and whether the longest, slowest one feels out of place.
