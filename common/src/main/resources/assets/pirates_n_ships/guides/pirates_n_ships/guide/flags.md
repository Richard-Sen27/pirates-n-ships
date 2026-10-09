---
navigation:
  title: "Flags"
  parent: index.md
  position: 50
  icon: pirates_n_ships:jolly_roger_flag
---

# Flags

The **flagpole** flies a flag that shows a ship's allegiance.

The flag cloth is one block high and one and a half blocks long and hangs downwind from the top of the pole, so a
pole needs free space downwind.

**Tall poles.** Stack flagpoles on each other to build one tall pole, up to 6 blocks (server config
`flags.max_pole_height`; a taller pole is refused). The top block flies the flag: the pole shows its iron cleat at the
foot and its gilded finial at the top. You can use any block of the pole, the flag is always worked at the top.
Placing another flagpole on a pole that flies a flag takes the flag up with it, nothing drops; breaking the top block
drops the flag, breaking a block in the middle splits the pole in two (the upper part keeps the flag). With
`flags.stacked_poles` off every block is a pole of its own.

**The flag runs along the pole.** While you hoist or raise a flag it climbs from the foot of the pole to the top, and
while you strike or take it down it runs down again, over the 3 seconds the work takes. Client config
`flag_visuals.hoist_animation` turns this off (the flag then appears and vanishes at once).

| Flag | Meaning for the law rules |
|---|---|
| none or Merchant Flag | Neutral to everyone. |
| <ItemLink id="pirates_n_ships:navy_flag" /> | Navy and merchants friendly, pirates hostile. A false flag if the captain has a bounty or too little navy standing. |
| <ItemLink id="pirates_n_ships:jolly_roger_flag" /> | Pirates friendly, navy hostile, merchants may surrender. Being seen under it is a crime. |
| any vanilla banner | A custom flag: neutral. The cloth shows the banner's colour and patterns. |

At the pole:
- **Use it with a flag item:** hoists that flag after 3 seconds and gives back the old one.
- **Use it with an empty hand:** strikes the colors (the flag is lowered but kept), or raises them again.

**What the flag does.** The flag your ship flies is the highest flag on its poles. Under the Jolly Roger the navy attacks
everyone aboard on sight and charges you for being seen; pirates leave you alone. A navy flag lets you pass the navy,
unless you are a suspect or worse: then every navy soldier within range may see through your colours. If one does, you
are charged heavily and the navy hunts your ship for five minutes, whatever you fly. Striking your colours surrenders:
navy and pirates stop attacking. Firing on a ship that struck its colours, or on one flying a merchant flag or banner,
is a crime; if your crew fires, the charge goes to you as the ship's owner. Server config `law.flags`.
- **Sneak-use with an empty hand:** takes the flag down.
- Breaking the pole drops the flag.

The flag streams downwind at the exact wind angle. Nothing reacts to flags in the world yet: there are no navy or pirate ships.

Hoist any banner on a flagpole to fly a custom flag: the cloth takes the banner's base colour and shows every pattern
of the banner, as if the banner were hung sideways from the pole (its top edge at the pole, its length along the
cloth); the back of the flag shows the design mirrored, like a real flag. Client config `flag_visuals.banner_upright`
stands the design upright instead (its top at the top of the cloth, stretched to the cloth's length).
