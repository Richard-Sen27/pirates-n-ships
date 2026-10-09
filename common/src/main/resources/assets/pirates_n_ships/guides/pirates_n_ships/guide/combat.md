---
navigation:
  title: "Weapons and combat"
  parent: index.md
  position: 90
  icon: pirates_n_ships:cutlass
item_ids:
  - pirates_n_ships:cannon
  - pirates_n_ships:grappling_hook
  - pirates_n_ships:swivel_gun
---

# Weapons and combat

| Item | Today |
|---|---|
| <ItemLink id="pirates_n_ships:rapier" /> | A sword: 5 damage, attack speed 2.0 |
| <ItemLink id="pirates_n_ships:cutlass" /> | A sword: 7 damage, attack speed 1.2 |
| <ItemLink id="pirates_n_ships:saber" /> | A sword: 6 damage, attack speed 1.6 |
| <ItemLink id="pirates_n_ships:pistol" />, <ItemLink id="pirates_n_ships:musket" /> | Hold right mouse to aim, left mouse fires (aimed or from the hip); see Firearms. |
| <ItemLink id="pirates_n_ships:lead_shot" />, <ItemLink id="pirates_n_ships:cannonball" /> | No function yet |
| <ItemLink id="pirates_n_ships:grappling_hook" /> | No function yet |

The swords work like vanilla swords for now. The skill-based fighting system (slash, thrust, guard, parry, riposte,
stamina) is implemented on the server with per-weapon values in `data/pirates_n_ships/pirates_n_ships/weapon/`, but
nothing lets a player use it yet: the input and animation layers are missing.

## Firearms
The pistol and the musket are single-shot flintlocks. Hold right-click with one lead shot and one gunpowder in your
inventory to load (3 seconds for the pistol, 5 for the musket, with the bow pose; letting go early cancels and costs
nothing; creative mode needs no ammo). The tooltip shows "Loaded" or "Unloaded". **Left-click fires, right-click
aims.** With a loaded gun, hold right-click to aim: after a second of steady aiming the shot is tighter and the musket
zooms in a little. Press left-click to fire, either while aiming or straight from the hip (no right-click: the full,
wider spread of the gun). Letting go of right-click never fires, it only lowers the gun, which stays loaded. A gun in
your main hand never swings, hits or breaks blocks with left-click, and left-click does nothing while you load. To
lower an aimed gun, you can also press sneak: the gun goes down still loaded and stays down while you keep sneaking
(server option `firearms.aim.lower_on_sneak`). The server option `firearms.fire_on_attack` (on by default) switches
back to the old scheme when turned off: hold right-click to aim and release to fire (a quick click fires at once), and
left-click is an ordinary attack again. While you load, a white
bar under the gun's slot fills up; a loaded gun shows a full gold bar in the hotbar and inventory, its hammer cocked back, and its tooltip
says "Loaded" or "Not loaded". You see yourself aim and reload (the Player Animation Library drives it, client option
`firearm_animations.enabled`). Firing: a lead ball flies out with smoke and a small kick, the gun is unloaded again and needs half a second before it
can be used. The pistol hits hard but scatters; the musket flies flatter and tighter. Standing in the rain, a quarter of
the shots misfire with a click and the charge stays in. Without ammo the gun only clicks. Shooting someone counts as an
attack for the law, and the death message names you. Everything is in the `firearms` server config (and the misfire
chance in `combat`).

## Cannons
Craft a cannon (2 iron ingots, 1 iron block, 2 logs, 1 planks) and place it on a deck or on land; the muzzle points
the way you face. **Load** it by using it with gunpowder, then with a cannonball (powder first, always). **Aim** by
sneaking and using it with an empty hand: the upper half of the block raises the barrel, the lower half lowers it,
from −5° to +20° in 5° steps (the action bar shows the angle). **Fire** by using it with an empty hand. The barrel
needs 5 seconds to cool before new powder goes in. A ball hits for 20 damage and smashes the wooden block it hits; a
hole below a ship's waterline lets the sea in, so pump and patch. From a moving ship the shot carries the ship's speed,
and each shot pushes your ship back a little; a hit pushes the other ship. Balls that hit water splash and sink. A crew
member at a cannon fires it on the whistle order **Fire!** (or `/pirates crew order fire`) once you have loaded it:
"Aye, firing!", and the gun goes off half a second later; crew at unloaded guns answer "The gun is not loaded,
captain!". Server options: section `cannons` (on/off, damage, speed,
gravity, reload, elevations, blocks per hit, recoil and impact push) and `combat.cannon_block_damage`.

Placing a cannon: click the deck block where the **front** of the carriage should stand; the rear takes the block
toward you and the barrel overhangs one block ahead, so click the spot by the rail. Both halves need a block
underneath. Load, aim and fire on either half; breaking either half gives the whole cannon back.

Cannonballs obey the world's rules: with `mobGriefing` off or inside the spawn protection nothing breaks. Blocks
they smash drop their items, a gun you break gives back its powder and shot, and a ball that grazes a hull at a
shallow angle pings off instead of breaking it; a square hit does the most damage.

## Swivel gun
A small gun on a yoke (three iron ingots over a stick) that mounts on a fence, wall, iron bars, brig bars or any
full block. Load gunpowder, then a cannonball (or lead shot if the server says so). Hold right-click with an empty
hand: the gun turns wherever you look while you hold, up to 45° up and 30° down; let go to fire. Sneak-use tells
you what it needs. It hits less hard and less far than the cannon and breaks no planks unless the server allows it;
it reloads in three seconds. Crew assigned to it fire it on the whistle's "Fire!".

Crew at a gun can also load it: put gunpowder and cannonballs (or the swivel's shot) in a chest, barrel or cargo
crate within 4 blocks of the gun on the same ship, then blow "Load!" on the whistle. After every "Fire!" the crew
reloads by itself from that supply, so a manned, supplied gun keeps firing as fast as it cools down (server config
`cannons.crew`).

## Grappling hook
**Shooting the hook.** Put the grappling hook in your **off hand** and a musket in your main hand. Hold use to load
the hook into the musket (its full reload and one gunpowder), then aim with right-click and fire it with left-click: a flat shot on a 64-block
rope. Thrown by hand, the hook's rope is 32 blocks. The hook catches on any solid surface: another ship (the rope hauls
both ships together), your own ship (a line to slide down, e.g. from the mast top), or land (from a ship it slowly
hauls your ship toward that point like a kedge; from land it is a zip line). It slips off leaves and glass panes. To
slide, look at the rope with an empty hand (or a hook in it) and use it; with a musket in hand, use always works the
musket. Left-handed players can turn off `grapple.launch.offhand_required` to swap the hands.

**Cleats** work like mooring rings: a hook flying close to a cleat catches on it, and using a cleat on your ship
while your hook is out ties the rope off there.

**Mooring rings** (4 iron ingots make 2) mount on decks, walls or beams. A hook that flies within a block of a ring
on another ship catches on it and holds twice as far before tearing loose. With your hook latched, use a ring on
your own ship to tie the rope there: the ships keep hauling together and you can walk away. Sneak and use with an
empty hand, or breaking a ring, lets go.

Right-click to throw the hook. If it hits the hull of another ship it bites in and hangs there, following the ship.
If you stand on your own ship, the taut rope hauls both ships together until they lie side by side, ready for
boarding; then it goes slack. From land the rope drags the hooked ship slowly toward you. A hook that hits your own
ship, land, water or a creature does not hold (a creature takes a small hit) and comes back after two seconds. To let
go, sneak and right-click with an empty hand. More than 24 blocks from the hook the rope snaps and the hook comes back
(or is lost, if the server says so). Throwing a second hook releases the first. Server options: section `grapple`.

## Swordplay
Swords make themselves heard, one sound per attack: a miss whooshes (lower for a thrust), a hit slices flesh and a
thrust bites harder, a chestplate rings, and blade on blade clangs, loudly for a parry, dully for a blocked blow
or when you catch an opponent mid-swing. Drawing a sword plays a short scrape. Server owners turn sword sounds off or change
their volume under `melee.sounds`.

With a rapier, cutlass or saber in hand the mouse works differently (server config `melee.skill_based_combat`,
on by default; vanilla weapons are untouched):
- **Slash:** a quick left click. A wide, short arc after a brief wind-up.
- **Thrust:** hold left click about half a second and release. Narrow, long reach, more damage, slow to recover if it
  misses.
- **Guard:** hold right click. It blocks every hit from the front completely, but each blocked blow costs stamina,
  and heavier blows cost more. When you can't pay for a blow, your guard breaks: the hit gets through, softened by
  your blade, and you stagger. Hits from behind or the side ignore your guard.
- **Parry:** a quick right tap just before a hit lands. The hit is deflected, the attacker staggers, and for about a
  second you may **riposte** (attack for bonus damage). A parry with no hit coming costs stamina and locks parrying
  briefly.
- **Stamina:** a brass-framed bar while you hold a sword, above the food row on the right (or left of the hotbar
  with the client option `melee_hud.position`); it fades out when full and comes back the moment you fight. Attacks, guarding and failed parries drain
  it; it refills after a moment of rest. Empty, you can neither guard nor parry and stagger easily. The bar turns
  violet while you are staggered and shows a grey block during the parry lockout.
- Right click is taken over while a mod sword is held, so swap to another item to open doors or use the helm.
- Your attacks, guard, parry and stagger are animated in first and third person through the Player Animation
  Library (a required client mod); the animations are authored in Blockbench (`art/animations/`). Client config
  `melee_animations`: on/off, first person auto/on/off (auto steps back for camera mods), layer priority.
- Client config `melee_input` (tap and hold thresholds) and `melee_hud` (bar on/off, scale, offsets).
