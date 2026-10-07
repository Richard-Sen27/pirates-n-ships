---
navigation:
  title: "Your first ship"
  parent: index.md
  position: 10
  icon: minecraft:oak_boat
---

# Your first ship

1. **Build a hull** from any blocks, floating in water. Planks, slabs, stairs and glass are watertight. Leave a
   one-block gap to the shore or a dock: anything that touches the hull becomes part of the ship.
   Make the bottom layer from something heavy such as stone, or the boat will tip (see [Limits](ships.md#limits-to-know)).
2. **Place a helm** on deck. Stand behind it and look where the ship should go: the bow is the direction the
   helmsman looks.
3. **Rig a square sail**: a mast of logs or fences with two rows of **yards** across it, one 2 to 8 blocks above the other, and a **sail winch** somewhere on deck. A **capstan** gives you an anchor.
4. **Use the helm.** The connected blocks become a ship: a physics object that floats, with a dry hold.
5. **Use the sail winch** to hoist the sails (furled → half → full). The wind pushes the ship.
6. **Steer at the helm:** click the right third of the wheel for starboard, the left third for port, the middle for
   midships.
7. **Use the capstan** to drop the anchor, and again to raise it.
8. **Sneak-use the helm with an empty hand** to turn the ship back into normal blocks. The ship has to be nearly still
   and level.

For testing you can fix the wind with `/pirates wind set <fromDegrees> <strength>`, and see what pushes the ship with
`/pirates ship forces`.
