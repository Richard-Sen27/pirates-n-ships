# Playtest: the NPC helmsman (work package WS3a)

The helm is now a crew station. A crew member at the ship's steering helm holds a course toward a point or along
waypoints by turning the wheel; `station/helm/HelmCourses` steers every 5 ticks with the course keeper
(`CourseKeeper`: rudder = 2 × heading error, 3° deadband, 1.5 s yaw-rate anticipation, at most
`sailing.max_rudder_angle`). The GameTests cover the logic on the small test hull, but a real ship, the wheel's
animation and the crew member's place at the helm have never been seen in game.

Setup: `./gradlew :neoforge:runClient`, a creative world with open sea and a decent wind (`/pirates wind` or wait for
it), a ship you built (or `/pirates ship place`) with a helm, at least one sail with a sail winch, and two crew members
aboard (`/pirates crew spawn` while standing on the deck). The captain's whistle. Note the F3 coordinates of a point
about 60 blocks away on open water. Please send `latest.log` if anything differs.

## Steps
1. **Hoist and order a course.** Stand on the deck. `/pirates crew order hoist` (a free hand takes the winch), then
   `/pirates crew order course <x> <z>` with your point. Expected: the command answers "Course set: 1 waypoints; a
   free hand will take the helm", within a second the other crew member stands beside the wheel and says "Aye,
   holding the course!".
2. **The wheel turns.** Watch the helm. Expected: the wheel turns toward the side of the target (wheel clockwise as
   seen from behind the wheel = to starboard), the ship comes round, and as the bow points at the target the wheel
   comes back toward the middle. It should not swing from lock to lock again and again (say if it oscillates, and
   roughly how often). The HUD heading should settle on the bearing to the target.
3. **Arrival.** Let it sail. Expected: within 8 blocks of the point the helmsman says "We've arrived, captain! Rudder
   midships", the wheel goes to the middle and the ship sails on straight (the sails stay set: furling is not his
   job). He stays at the helm.
4. **Take the wheel yourself.** Order a new course, then hold use on the helm and turn the wheel against him.
   Expected: the wheel follows only you while you hold it; he does not fight you. Let go: within a quarter of a second
   he turns the wheel back toward his course.
5. **Click steps.** In the server config set `helm.wheel.drag_steering = false`, order a course again. Expected: the
   rudder moves in steps (F3 on the helm shows `rudder`), the ship still steers toward the point. Click the middle of
   the wheel: the rudder goes midships and he leaves it there for 5 seconds (`crew_stations.course.manual_override_ticks`),
   then resumes. Set `drag_steering` back to true.
6. **Two waypoints.** `/pirates crew order course <x1> <z1> <x2> <z2>` (both on open water, some 40 blocks apart).
   Expected: at the first point he says "Waypoint reached, steering for the next (2 of 2)" and turns for the second,
   then arrives as in step 3. Add `loop` at the end: he goes back to the first point instead of arriving.
7. **Into a wall.** Order a course whose straight line runs into a coast or a pier. Expected: the ship runs aground or
   stops against it; after about 10 seconds with the sails set and the ship nearly stopped he says "We're not making
   headway, captain! We're stuck". He keeps the rudder toward the target (nothing else happens yet: later packages
   react to it).
8. **Release.** Use the whistle's "Release crew" while he steers. Expected: he leaves the helm and the wheel goes to
   the middle.
9. **Second helm.** Place a second helm on the ship, assign a crew member to it with the whistle and order a course.
   Expected: "This helm doesn't steer the ship, captain!", and nothing turns.

Report: the time the ship needs to turn 90° at your ship's size, and whether the helmsman's place beside the wheel
looks right (he stands at the first free side of the helm: north, east, south, west).
