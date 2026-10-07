# Playtest: the in-game guide book (work package D1)

Page generation, ids, recipes, anchors, the recipe and the first-join gift are covered by 6 JUnit tests and 3
GameTests. What only a client shows: GuideME's rendering of the pages.

Setup: the dev client has GuideME; for step 9 remove the GuideME jar from the run.

## Steps
1. **New world:** you start with "Pirates 'n' Ships Guide" with its grey tooltip; rejoining gives no second one.
2. **Recipe:** book + feather makes the guide; it shows in the recipe book after holding a book.
3. **Index:** right-click the book: the intro, the captain's path (the last link jumps to "Sea hazards" on the Sailing
   page) and the topic list with icons.
4. **All blocks:** the item grid, hoverable item links in the table (Helm links to Ships), recipe rows under the table.
5. **Cargo and trade:** the goods table links vanilla sugar, cod and the others.
6. **Commands:** `<fromDegrees>` and the like stay inside code, no MDX error boxes anywhere.
7. **Search:** "bilge" and "cannon".
8. **Hotkey:** hold G over a Pantry item in the inventory: the Provisions page opens.
9. **Without GuideME:** the game starts, no book recipe, nothing given on join, no recipe errors in the log.
