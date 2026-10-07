# Playtest: ropes on cleats (RP1)

Cleats are general rope anchors like the mooring ring (docs/design.md §5.2 "Ropes on cleats"). The rules (stay vs.
line vs. refusal, sag shape, tie points, breaking, rotation, the grapple catching and tying off on cleats, the config
toggles) are covered by JUnit tests and GameTests. A client is needed for how the ropes look and feel.

Setup: creative, a stack of rope, cleats and mooring rings, a small assembled ship (or two) in water. Defaults:
`sailing.sails.rope_lines = true`, `rope_sag = 0.08`, `stay_max_length = 16`, `grapple.cleats_enabled = true`.

## Rope lines
1. **Railing line:** put 4 cleats on top of a railing (fence or slab row) 3 blocks apart. Rope on cleat 1 ("Rope tied
   here…", the rope glints), then cleat 2: "Rope line rigged", one rope used, a thin rope with a slight sag between
   the two horn bars. Continue 2→3 and 3→4 (start each with a click on the cleat you end at): a sagging chain along
   the railing; the middle cleats hold two ropes each. The rope meets each cleat at its horn bar, not at the block
   center.
2. **Over the deck:** a cleat on a mast wall about 3 blocks up, a cleat on the deck on the far side: a sloped line that
   sags less than a level one of the same length. Say whether the curve reads as a hanging rope.
3. **Cleat to ring:** a mooring ring on the deck and a cleat on the rail: the rope ties into the ring's middle. A ring to
   a ring works too.
4. **Sag at 4, 8 and 16 blocks:** level lines of 4, 8 and 16 blocks: about 0.3, 0.6 and 1.3 blocks of sag in the
   middle. Say whether 0.08 looks right or should be tighter/looser (`rope_sag`, 0 = taut).
5. **On a moving ship:** sail or push the ship and turn it: the lines stay on their anchors without lag or jitter; they
   sag toward the ship's own down (they tilt with the hull when it heels).
6. **Refusals (action bar):** a cleat on land and one on the ship: "The rope must stay on one ship…", no rope used; 17
   or more blocks apart: "Too far…"; the same two anchors twice: "These two are already roped together"; a fifth rope
   on one anchor: "An anchor holds at most 4 ropes".
7. **Line to sail:** a cleat high on a mast wall and a cleat on the bowsprit lower down: "Stay rigged. A cleat straight
   below its upper end makes the sail", drawn as a sagging line. Place a cleat on the deck straight below the mast
   cleat: the rope snaps straight and the furled sail appears; clicking the head cleat hoists it. Break the deck cleat:
   the sail goes and the rope sags again.
8. **Breaking:** break a middle railing cleat in survival: two ropes drop and both neighbouring lines vanish; the other
   lines stay. In creative nothing drops.
9. **Disassembly:** with lines on deck, disassemble the ship after turning it about a quarter: the lines are still drawn
   between the same anchors on land; reassemble: still there.
10. **Distance:** the lines stay drawn from about 100 blocks away and do not pop out when the anchor block leaves the
    screen edge.

## Grapple on cleats
11. **Hook a cleat:** from ship A, throw (or shoot) the hook to pass close over a cleat on ship B's deck: it snaps onto the
    cleat's horn and hauls like on a ring; it holds past 24 blocks (twice the rope, as rings do).
12. **Tie off on a cleat:** with the hook latched on B, use a cleat on your own ship A with an empty hand: "Rope tied off
    here", a knot sound, the rope now runs from the cleat's horn; walk away: the hauling goes on. The click does not
    also change a sail's trim.
13. **Slide down it:** tie off on a cleat high on A's mast, the hook in B's lower rail; look at the rope near the cleat
    and use it: you hang below it and slide to B (GR2), the rope stays fixed at the cleat.
14. **Rope in hand:** with a rope item in hand, a click on a cleat or ring always handles the rope (tie / rig), never the
    trim or the grapple.

## Config off
15. `sailing.sails.rope_lines = false`: existing lines are no longer drawn (stays with a sail still are); a flat rope
    between two cleats gives "Too flat…"; a ring ignores the rope item; from a cleat to a ring: "Rope lines are off…";
    stays still rig. Breaking a cleat of an old (hidden) line still drops its rope.
16. `grapple.cleats_enabled = false`: hooks fly past cleats, using a cleat with a hook out cycles the trim instead of
    tying off; rings work as before.
