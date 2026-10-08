---
navigation:
  title: "Law, bounties and the brig"
  parent: index.md
  position: 80
  icon: pirates_n_ships:bounty_proof
item_ids:
  - pirates_n_ships:brig_bars
  - pirates_n_ships:brig_door
  - pirates_n_ships:shackles
---

# Law, bounties and the brig

## Criminal score
Every player and mob has a score. Crimes raise it:

| Crime | Points |
|---|---|
| Theft from a village chest | 5 |
| Attacking a villager | 5 |
| Attacking the navy | 10 |
| Seen under the Jolly Roger | 10 |
| Attacking a neutral ship, press-ganging, suspected piracy (plunder seen aboard) | 15 |
| Offering stolen goods at a village or navy desk | 17 |
| Killing a villager | 20 |
| Killing a navy member | 30 |
| Caught under false colors, attacking a ship that struck its colors | 40 |
| Piracy (capturing a ship) | 50 |
| Desertion | 60 |

- The score makes you **suspect** from 10, **wanted** from 50 and **notorious** from 200.
- It goes down by 10 points per in-game day, starting 5 minutes after your last crime.
- Hitting the same victim again within 30 seconds doesn't count twice.
- A fine costs 3 doubloons per point (only through `/pirates law fine` for now). Notorious criminals can't pay.

What is detected in the world today:
- **Hurting or killing villagers and wandering traders**, also with arrows or by your pets.
- **Theft:** taking items out of a container that stands in a village and that no player placed, while a villager
  can see you. Putting items in is fine.
- The other crimes need ships, flags or the navy to react, and are reached through `/pirates law crime`.

## Fines, ransom, press-gang and release
Pay your fine at a navy officer: hold doubloons and right-click him. Each criminal-score point costs 3 doubloons by
default, and he takes only the whole points you can afford; he won't deal with wanted criminals. Lead a shackled navy
officer, navy soldier or merchant (sailor, villager, trader) to a navy officer and right-click him with an empty hand
to ransom them. Hold the captain's whistle and right-click a shackled sailor standing on your own ship to press-gang
them into your crew (low morale, and a crime). Sneak and right-click your own prisoner with an empty hand to let them
go. Server config `law.officer_fines`, `law.ransom_needs_port`, `flags_brig.prisoner_interactions`.

## Bounties
- At a score of 50 the navy puts a **bounty** on you of twice your score. It grows with the score and is withdrawn
  when the score falls below 25.
- Players can add bounties on anyone (`/pirates law bounty place`, 10 doubloons or more).
- **Claiming:** killing a target with a bounty puts a **Bounty Proof** into the killer's inventory. Handing it in pays
  the bounty. Delivering the target alive pays 1.5 times as much. A claim wipes the target's score.
- **Turning in:** right-click a navy officer with a Bounty Proof to collect the bounty in doubloons. To deliver
  prisoners alive, walk up to an officer with your shackled prisoners within 4 blocks and right-click him with an
  empty hand: a bounty pays 1.5 times, and a captured pirate earns 10 doubloons on top. The navy leads NPC prisoners
  away; a captured player is set free on the spot with a clean record. You get your shackles back. Officers won't
  deal with you while the navy hunts you.
- **Notice board:** craft it from planks and paper (planks, paper, planks, twice). Use it to see every bounty: who is
  wanted, for how much, who placed it and when, and whether there is a bounty on you. To place one, type a name (an
  online player or anyone already on the board, or click a suggested name), enter at least 10 doubloons and press
  Place; the doubloons come out of your inventory. The list updates while it is open.

## Shackles and prisoners
- **Use shackles on a mob** that is at 25% health or less: it becomes your prisoner. It stops fighting and can't
  despawn. Bosses can't be captured. Players can only be captured if the server allows it and they have a bounty.
- **Use shackles on your prisoner** to lead it or let go. A led prisoner follows you like a mob on a lead.
- A prisoner left alone outside a cell tries to escape (about once per 10 minutes on average).

## Brig bars and brig door
Locking and unlocking a brig door needs a **Brig Key** (an iron ingot over an iron nugget). Use the key on the door:
"Brig door locked" with a padlock, again to unlock. Any key works on any brig door, so keep them from your prisoners.
The owner of a door still opens it without a key; nobody else, no mob and no redstone can. Bars connect to the door.

- **Brig bars** connect like iron bars.
- The **brig door** belongs to the player who placed it. Sneak-use it with an empty hand to lock or unlock it. A locked
  door opens only for its owner and ignores redstone.
- A prisoner is **in a cell** when it stands in a closed space of at most 64 blocks, bounded by solid blocks, brig bars
  and a closed, locked brig door. There it stays, and it doesn't try to escape.

What you can do with a prisoner (deliver at a navy officer; the rest through `/pirates brig`): deliver it for its bounty,
ransom it, press-gang it (a crime), or release it.
