# Playtest: hats (work package H2)

Slot, armour, recipes and the hat boxes against the mob geometry are covered by JUnit and 6 GameTests. What only
a client shows: how the hats sit on a real head and skin.

## Steps
1. **GUI:** each hat shows from the front three-quarter with its top visible and fits the slot: navy with a white
   edge and a cockade on the wearer's left; pirate all black with a bone-coloured skull on the front; officer with a
   gold edge and loop; bandana red with bone dots.
2. **Wearing:** right-click while wearing a leather helmet: the hat goes on and the helmet comes back to the hand;
   shift-click into the helmet slot works too; the tooltip shows "+1 Armor When on Head".
3. **Third person, front:** the brim sits about 2 px above the eyes, neither floating nor sunk into the forehead; the
   tricorn's flat front faces forward; the bicorne sits athwart (ends to the sides).
4. **Third person, side:** the depth matches the head with no gap; the bandana's knot and tails hang at the back.
5. **First person:** no hat visible. **Ground and item frame:** sensible size, front facing out.
6. **Next to the mobs** (navy soldier for the tricorns, officer, pirate for the bandana), front and side: same height,
   width and position. If a hat sits high or low, say by how much; a cockade on the wearer's right means a mirrored x.
7. **Config:** `apparel.hat_armor = 0` after a restart: no tooltip line, no armour point.
8. **Others:** an armour stand and a zombie given a hat (`/item replace entity @e[type=zombie,limit=1] armor.head with
   pirates_n_ships:navy_hat`) wear it the same way. Skins with big outer-layer hair may show through the tricorn walls.
