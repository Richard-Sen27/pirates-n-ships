# Playtest: sea chest (work package S1)

Contents in every state, the worn restrictions, the floating chest's physics and the toggle are covered by 21 JUnit
tests and 11 GameTests with measured physics. What only a client shows: the feel of the sink pull, the sprint block,
the bobbing, and dedicated-server movement.

Setup: `./gradlew :neoforge:runClient`, survival, a dock at the sea, a ship. The block is a placeholder cube until
its Blockbench pass.

## Steps
1. **Contents in every state.** Fill the chest and break it with an axe: one item drops and its tooltip lists the
   items. Place it again: contents are back. Pick-block in creative gives a copy with contents; creative-break drops
   one item with contents. Put it on, take it off, place it: intact. Rename it in an anvil: the name is the screen
   title in every state.
2. **Nesting.** A sea chest cannot go into a sea chest (shift-click or drag); a shulker box can.
3. **Wearing.** Use in the air or drop it into the chest armour slot. Space does not jump, walking is visibly slower,
   Ctrl or double-tap W does not sprint (at most a one-tick flicker, no FOV pumping; try toggle-sprint too). In deep
   water you sink without input, cannot sprint-swim, and holding space only keeps you in place; stay under to confirm
   drowning. Say whether the pull feels right. Take it off: all normal again.
4. **Floating chest.** Right-click the seabed through water, or the water surface from a dock: a chest floats at the
   waterline, rocking gently; placed deep it rises. `/pirates wind set 270 6`: it drifts east at about a third of the
   wind speed; a current pushes it too. Use opens the 6-row screen; sneak-use puts it in your inventory with
   contents; a hit drops it as an item. Relog: still there with contents. Say if its front faces the wrong way.
5. **On a ship.** Placing on an assembled deck gives a block, not a floating chest; open it while sailing.
6. **Toggle.** `sea_chest.enabled = false`: the item cannot be worn, placing on water places a block, a worn chest
   stops restricting within a tick.
