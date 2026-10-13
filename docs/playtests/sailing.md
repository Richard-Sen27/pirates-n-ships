# Playtest: sailing

## SAIL5: the boom and the triangular-sail scene

A **boom** in a triangular sail's clew place swings with its sheet (9 steps of 15°, up to 60° each way), and the sail
draws by the boom's angle to the wind: centred close-hauled (45° off the wind at the closest), 45° out on a beam reach,
60° on a broad reach and a run; aback (out on the windward side) it gives nothing. The crew sheets the booms of an
assembled ship to leeward on their own (`sailing.boom_auto_trim`), a boom that is aback swings across by itself (a gybe,
with a bang and a small heel, when the wind crossed the stern), a hand on the boom sheets it a step, sneak-use centres
it. Covered headlessly: JUnit (`BoomRulesTest`, the rig test `BoomArmRigTest`, `HandMadeModelsTest`, the Ponder tests)
and `BoomGameTests` (thrust on both tacks, none aback, the jib unchanged, auto-trim and the gybe, sheeting by hand).
What only a client shows: the arm and cloth drawn at the sheet, the sound, the feel at sea, the Ponder scene.

1. **Rig a mainsail on a test ship:** a fence or log mast, a head cleat high on the mast's aft face, a boom (any wood;
   "Spruce Boom" in the creative tab) on the same face lower down, planks over the stern and a tack cleat on them at or
   below the boom's height, a rope from the head to the tack. Expected: the boom's arm reaches from the mast out to above
   the tack, the cloth hangs from the head to the arm's end (not to the tack), a thin sheet rope runs from the arm's end
   to the tack, and the stay still runs head to tack. Look from all sides: no gap between the gooseneck and the arm, the
   arm level, its end exactly where the cloth's corner is.
2. **Sheeting by hand (on land or with `/pirates config` auto-trim off):** click the boom's port and starboard sides.
   Expected: each click swings it 15° toward that side (the arm and the cloth's foot move smoothly, about a second per
   60°), the action bar says "Boom sheeted to +15°" and so on, it stops at ±60°, sneak-use centres it ("… to 0°"). The
   cloth bellies to the side the boom is out on.
3. **Auto-trim at sea:** assemble, set full sail, `/pirates wind set` to put the wind on the beam. Expected: within a
   few seconds the boom swings out 45° to leeward a step at a time and the ship sails; with the wind on the quarter it
   goes all the way out; head to the wind it comes back to the centre and the cloth luffs.
4. **Tack and gybe:** steer through the wind (or move the wind across the bow). Expected: the boom swings quietly across.
   Then bring the wind from one quarter across the stern to the other. Expected: the boom slams across at once with a
   wooden bang, and the ship heels a little to the new leeward side and rights herself. Say whether the bang and the
   heel feel right (`sailing.gybe_force`, 0.5).
5. **Points of sail:** with one boomed sail, sail close-hauled (45°), a beam reach and a dead run. Expected: she moves
   on all three, fastest on the reach, and still well downwind (a jib alone is slow downwind).
6. **The jib is unchanged:** the starter and armed sloops' jibs (clew cleats, no boom) sail and look as before.
7. **Ponder:** hold W over a cleat or a boom, or press "Show me: triangular sails and the boom" in the Almanac's Sailing
   chapter. Expected: about 36 s; the mast, the three cleats, the rope clicks, the stay and the cloth (10 blocks), the
   reach, the boom replacing the clew cleat with its arm to the tack, white wind arrows from the west with the boom
   stepping out to starboard, the wind from the east with the boom across to port, the starboard quarter with the boom
   out to 60°, the gybe to starboard, the hand and sneak clicks. No text box off-screen at GUI scale 3; nothing hides
   the boom from the default camera. Say whether the cloth reads well in each phase.
8. **Old world / disassembly:** disassemble a ship with a boomed sail with a quarter turn and assemble it again.
   Expected: the boom, its arm and the cloth come back right.

## SAIL6: the rope of a boomed sail

The triangle of a boomed sail is the stay rope from the head cleat to the boom's outer end, the boom itself, and the mast
side from the gooseneck up to the head with no rope. The boom's end is tied to the tack cleat on deck by the sheet rope.
Only the drawn geometry changed: the stay still links the head cleat to the tack cleat, and the drive is unchanged.
Covered headlessly: `BoomArmRigTest` (stay end, cloth corners and sheet for every facing and step). What only a client
shows:

1. **A boomed mainsail from both sides:** rig as in SAIL5 step 1. Expected: the rope from the head ends at the boom's tip
   (not at the tack cleat), no rope runs along the mast between the head and the boom, the cloth fills head, gooseneck and
   tip, and a separate sheet rope runs from the tip down to the tack cleat. No rope passes through the cloth's face.
2. **The rope follows the swing:** sheet the boom through its steps by hand. Expected: the stay's lower end and the sheet's
   upper end stay on the boom's tip all the way, with no jump at the end of the ease.
3. **A jib is unchanged:** a clew-cleat sail still has its rope head to tack.
4. **Ponder:** the boom phase shows the stay ending at the boom's tip and the sheet to the tack; the text line reads right.
