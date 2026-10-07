# Playtest: sharks (work package M4)

Hunting rules, the circle-charge-bite rhythm, frenzy, deck and boat safety, stranding, the config and the natural
spawn registration are covered by 20 JUnit tests and 11 GameTests. What only a client shows: natural spawning in a
real world, the look and the bone rotation signs, and how the hunt feels.

Setup: `./gradlew :neoforge:runClient`, survival with cheats, an ocean. The shark uses a script-made placeholder
model until its Blockbench pass. Please send `latest.log` if anything differs.

## Steps
1. **Natural spawning.** New world or restart, swim in an ocean or deep ocean for a few minutes: sharks below the
   surface, alone or in pairs; none in rivers or swamps. With `mobs.shark.spawn_weight = 0` and a restart: none. Say
   whether weight 8 feels like too many sharks compared with squid and dolphins.
2. **Look.** The model faces the way it swims, the tail sways and faster when it speeds up, the body tilts up and
   down the right way when it climbs or dives (the pitch sign is unverified), the head turns at most about 30°.
3. **Hunt and bite.** Swim near a shark (or `/pirates mob spawn shark` in deep water): about 3 s of circling, then a
   charge, then a bite with the jaw snapping, about 6 damage on normal difficulty and a little knockback, then it
   circles again.
4. **Frenzy.** Below 40 % health (under 8 health points) it charges without circling and bites about every 1.5 s.
5. **Deck and boat safety.** Board a boat or stand on a ship's deck while hunted: it drops you at once and never
   bites; climbing onto land does the same.
6. **Giving up.** Outswim it for 10 s without being bitten (sprint-swimming): it turns away and ignores you for about
   10 s.
7. **Peaceful.** `mobs.shark.peaceful = true` and hit a shark: it never attacks.
8. **Despawn.** Fly more than 128 blocks away and come back: unnamed sharks are gone or replaced. `enabled = false`
   makes loaded sharks vanish.
9. **Stranding.** A shark spawned on a beach flops toward the water; one far from water takes damage after about
   15 s.


Addendum (Q4): an idle shark in deep ocean cruises below the surface for a minute without bobbing or breaching; when it hunts a surface swimmer it bites from just under the surface (the back may show, it never jumps out); stranding still flops it into the water.


## M4-art: the look
1. **Idle:** slow tail sway, slight fin roll, the jaw breathing a little; no popping between idle and swim.
2. **Cruising:** the tail beats in an S-curve (the tail fin lags the rear body), faster at speed; the body yaws slightly; the pectoral fins flap together.
3. **From all sides:** grey-blue back, white belly, five pale gill slits per side, black eyes, the white tooth line under the snout; pectoral fins swept back and drooping, a tall dorsal fin, the upper tail lobe longer than the lower; no holes or z-fighting between body sections; the right side mirrors the left.
4. **Hunting:** the head turns at most about 30° and the body pitches when climbing or diving without fighting the animation.
5. **The bite:** the jaw drops to about 35° showing a pink mouth and both rows of teeth, then snaps shut; the head dips and the body lunges slightly; lower teeth hidden while shut; watch for a seam at the back of the head.
6. **Stranded on a beach:** the belly sits roughly on the ground, the lower tail lobe a little below.
