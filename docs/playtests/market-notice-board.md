# Market notice board (NB2)

## NB2: the 2×2 board on the market square

What the GameTests cover (`MarketNoticeBoardGameTests`, `WaterloggingGameTests`): placing needs a free 2×2, breaking
any part takes the whole board and drops it once (nothing in creative), using any part opens the board's screen at its
anchor, the `load` of all four parts follows bounties posted through the backend (empty, some at 1, busy at 5) and a
port's open quest offers, every vanilla wood places, and the market square piece carries the board whole. What only a
world shows: the models, the light on the four parts, the look in a real village and the recount timing. A **new** world,
cheats on, F2 screenshots of each step. The render of the three looks is `art/renders/market_notice_board.png`.

1. **A village square.** `/locate structure pirates_n_ships:seafarer_village` until the village has its market square
   (paved, two striped stalls with their vendors), go there.
   - **Expected:** south of the west stall (the provisions vendor's), in the square's back half, a 2 wide × 2 high
     board on two posts under a small pitched roof, the notices facing the stalls and the aisle, a free row in front of
     it to stand on; the vendors, stalls and benches untouched. Report a board cut in half, floating, facing a wall, a
     part lit much darker than the others, or a vendor or path it blocks.
2. **Its look.** Compare the board with the render.
   - **Expected:** **some** (three notices) or **busy** (papered over with a wanted poster) in a village that offers
     quests (the desk's Quests tab); with no bounties and no offers (`quests.enabled` off on a quiet world) **empty**:
     only pins and two torn scraps. No flickering papers or ink from any angle, close up or far.
3. **It counts.** Note the look, then open the board (use any of its four blocks) and place bounties on a name until
   five or more are listed (or `/pirates law bounty place` from a second player).
   - **Expected:** the screen is the small board's (same list and form). The board turns from empty to some on the first
     bounty and to busy at five postings (bounties plus the port's offers) within a second of placing at a board, and
     within 10 seconds for bounties placed by command (`law.notice_board.refresh_ticks` 200).
4. **Crafting and placing.** Craft one from two notice boards of one wood with a paper between them over two of its
   logs (try spruce and cherry). Place it facing you on open ground, then once more with a block where its top-right
   (your right, one up) would go.
   - **Expected:** the board stands with the clicked block as its bottom left, running to your right and up; with the
     blocked cell nothing is placed and the item stays. The wood shows in the posts, feet, rails and roof; the board
     behind the notices is spruce in every wood.
5. **Breaking.** In survival, break its top-right block; place another and break its bottom-left. In creative, break
   any block of a third.
   - **Expected:** each time the whole board goes and one board drops (none in creative). Pistons can't push it.
6. **In water.** Place one with its bottom row in a one-deep pool, break it.
   - **Expected:** the bottom parts are waterlogged (water shows round the posts), the water stays when it is broken.
