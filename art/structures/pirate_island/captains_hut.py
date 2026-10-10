"""Generates the pirate captain's hut (9x5 rooms on stilts, a porch, a dark oak thatch roof) and prints the BuildSpec
as JSON. The steps, the door and the hut connector are on the north (-z) side.

The floor is raised one block on stilts (rows y 0..1; y 0 is the foundation row in the ground, so a block of stilt
shows), the porch runs along the front with a rope rail, and two stairs lead up from the sand at the hut connector.
Inside: a bed, a cartography table with a map tile on it and a stool, the captain's sea chest, a hanging lantern and
a cobweb.

ST4b: palm trunk stilts at the corners, a sill beam of stripped dark oak round the floor, walls of vertical dark and
spruce boards between stripped corner posts, a door framed by dark posts, shuttered windows (sills on the back, where
the porch does not need the room), gables on a tie beam with a king post and a vent, rafter ends under the back eave,
a patched thatch roof, rope rails and a skull on the porch, a lantern post at the steps, salvage stowed between the
stilts, a rug and the captain's black banner inside, and gravel and coarse dirt round the stilts.

STR2: the body moved to ``_captains.build`` (byte-identical output), which the variants share."""
from _captains import build

p = build("pirate_island_captains_hut", "Pirate Captain's Hut", (9, 9, 9))
p.emit("captain's hut on palm stilts with a roped porch, shuttered windows, bed, map table and sea chest under a "
       "patched dark oak thatch roof",
       ["Faces north: the steps, the door and the hut_in connector are on the -z side.",
        "y 0 is the foundation row (stilt feet in the ground); the floor is y 2."])
