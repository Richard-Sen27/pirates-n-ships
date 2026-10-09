# Player messages (MSG1)

An audit of every message the mod shows a player during play, on the action bar or in the chat, made in MSG1 on the
human's request: "reduce the info text when something happens to a minimum, for example when throwing out the anchor
and so on. only keep the most important ones, and shorten them. there was one that was not even fully visible because
it was out of the screen." Command replies (`/pirates ...`), tooltips, screen titles and the Almanac are not part of it.

## Rules

- **Refusals stay**: a line that says why something did not happen.
- **State the player cannot see stays**: a crew member deserted while you were below, water left in a hold you cannot
  see, how many crew heard an order, the elevation of a gun, a bounty on your head.
- **Confirmations of visible actions go**: the action itself is the feedback (the anchor falls, the sails fill, the
  wheel turns, the shot is fired, the flag climbs, the crew member walks to the station). A routine success returns
  `Component.empty()` and `core.text.ActionBar.show` sends nothing for it, so the line already on the bar stays.
- **Ship handling never uses the chat** (`sailing`, `station`, `ship`, `combat/cannon`, `combat/grapple`,
  `combat/boarding`, `crew/npc`). The crew's answers to orders and the helmsman's reports go to the action bar of the
  players near the crew member. Chat is for people (hails, the harbour master, officers, the navy's and the brethren's
  letters), quests, notices and reports from afar (desertion, mutiny, a chase).
- **Every kept action-bar line is at most 40 characters** in English, each `%s` counted as 3 (a number). Vanilla draws
  the action bar centred above the hotbar: at GUI scale 3 on a 1920×1080 window the GUI is 640 px wide and a character
  about 6 px, so a line beyond about 40 to 45 characters runs off both edges. A name argument (a crew member, a ship,
  an order) can make the rendered line longer; the order names are at most 17 characters.
- **Numbers the player needs stay**, short: "Too deep: no ground within 40 blocks", not a sentence.

`common/src/test/resources/msg1/action_bar_keys.txt` lists the kept action-bar keys; `MessageAuditTest` (JUnit) fails
for any of them longer than 40 characters in `en_us.json`, and for any `displayClientMessage(..., false)`,
`sendSystemMessage` or `sendMessage` in the ship-handling packages. Add a key to the list with every new action-bar
message.

## Counts

- Action bar, kept as they were: 126
- Action bar, kept and shortened: 92
- Moved from the chat to the action bar: 30
- Dropped (call and key removed): 55
- Kept in the chat (people, quests, notices, reports from afar): 120
- Status lines inside a screen (not the action bar, listed for completeness): 48
- Fragments of other lines (names, bearings, report parts): 53

Longest kept action-bar line: `pirates_n_ships.sailing.capstan.not_on_ship`, "The capstan must be on an assembled ship" (40 characters with
its numbers).

The line that ran off the screen was most likely taking the wheel: "At the wheel (Rudder midships): move the mouse or
hold strafe left/right to turn it, let go of use to release" (about 96 characters with the rudder), shown on every use
of the helm. Next were the capstan's "Anchor dropping to the ground 12 blocks below, lands in about 2.4 s" (66), the
whistle's "Nobody to hoist the sails: no crew at a station that takes it and none free on this deck (spawn or hire crew
and bring them aboard)" (about 130) and the winch's "This ship has no sails: " followed by the rigging's first problem
(up to about 150). All four are gone or under 40 characters now.

## Verdicts

KEEP: kept unchanged. SHORT: kept, shortened. MOVE: from the chat (or a crew member's chat line) to the action bar.
DROP: the call and its lang key are removed. CHAT: stays in the chat. SCREEN: a status line inside a screen.
PART: a fragment used inside another line or a command reply. The lookout's old chat sighting was replaced by LKO2's
action-bar cue (`lookout.cue`, cut to 40 characters by `SightingCue`); its old keys are listed as DROP.
Length: characters of the English text, each `%s` counted as 3.

### Sailing: helm, capstan, winch, yards, cleats, rope

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `helm.holding` | DROP | bar | ~~At the wheel (%s): move the mouse or hold strafe left/right to turn it, let go of use to release~~ | 97 | taking the wheel (the wheel overlay shows the rudder); the 96-character line that ran off the screen |
| `helm.busy` | KEEP | bar | Someone else is at the wheel | 28 | using a wheel another player holds |
| `helm.rudder` | PART | - | Rudder %s° %s | 15 | the wheel overlay while holding the wheel |
| `sailing.rudder.step` | DROP | bar | ~~Rudder %s of %s to %s (%s°)~~ | 31 | a click step with drag steering off (the wheel and the ship HUD show the rudder) |
| `sailing.rudder.midships` | PART | - | Rudder midships | 15 | the wheel overlay |
| `sailing.rudder.port` | PART | - | port | 4 | the wheel overlay |
| `sailing.rudder.starboard` | PART | - | starboard | 9 | the wheel overlay |
| `sailing.rudder.disabled` | SHORT | bar | Steering is off on this server (was: Steering is disabled on this server. Sneak-use with an empty hand to disassemble) | 30 (was 80) | using the helm with steering off |
| `sailing.capstan.dropping` | DROP | bar | ~~Anchor dropping to the ground %s blocks below, lands in about %s s~~ | 68 | letting the anchor go at the capstan |
| `sailing.capstan.raising` | DROP | bar | ~~Raising the anchor, stowed in %s s~~ | 35 | heaving the anchor in at the capstan |
| `sailing.capstan.no_ground` | SHORT | bar | Too deep: no ground within %s blocks (was: No ground within %s blocks below: the anchor would not hold) | 37 (was 60) | letting go with no ground in the chain's reach |
| `sailing.capstan.not_on_ship` | KEEP | bar | The capstan must be on an assembled ship | 40 | using a capstan on no assembled ship |
| `sailing.capstan.disabled` | KEEP | bar | Anchors are disabled on this server | 35 | using a capstan with anchors off |
| `sailing.capstan.already_out` | KEEP | bar | The anchor is out already | 25 | a drop order while the anchor is out (crew, rare) |
| `sailing.capstan.stowed` | KEEP | bar | The anchor is stowed | 20 | a raise order while the anchor is stowed (crew, rare) |
| `sail_winch.set` | DROP | bar | ~~Sails set: %s (%s sails)~~ | 26 | cycling the trim at the winch (the cloth shows it) |
| `sail_winch.sail_set` | DROP | bar | ~~Sail set: %s~~ | 13 | cycling one sail at its yard or cleat |
| `sail_winch.no_sails` | KEEP | bar | This ship has no sails | 22 | the winch, the yard or the whistle on a ship without sails |
| `sail_winch.no_sails_why` | DROP | bar | ~~This ship has no sails: %s~~ | 27 | the same with the rigging's problem (up to 150 characters); the command still names it |
| `sail_winch.not_on_ship` | KEEP | bar | The winch must be on an assembled ship | 38 | a winch on no assembled ship |
| `yard.no_sail` | SHORT | bar | No sail: hang a yard %s-%s below (was: This yard heads no sail: hang a second yard %s to %s blocks straight below its middle, on the same mast) | 34 (was 105) | cycling a yard that heads no sail |
| `cleat.no_sail` | SHORT | bar | No sail: rig a stay, and a cleat below (was: This cleat heads no sail: rig a rope stay from it down to a lower cleat, and put a third cleat straight below it) | 38 (was 112) | cycling a cleat that heads no sail |
| `sail.dyed` | DROP | bar | ~~Sail dyed %s~~ | 13 | dyeing a sail |
| `sail.hung` | DROP | bar | ~~Banner hung on the sail~~ | 23 | hanging a banner on a sail |
| `sail.taken` | DROP | bar | ~~Banner taken down~~ | 17 | taking a banner back |
| `sail.same_dye` | KEEP | bar | This sail is already %s | 24 | dyeing a sail its own colour |
| `sail.no_sail` | SHORT | bar | No sail here to dye or hang a banner on (was: This heads no sail: dye or hang a banner on the upper yard of a square sail, or dye the head cleat of a triangular sail) | 39 (was 119) | a dye or banner on a yard that heads no sail |
| `sail.has_banner` | SHORT | bar | Take the banner down first (was: A banner hangs on this sail and gives it its colour: sneak-use the yard with an empty hand to take it back first) | 26 (was 112) | dyeing a sail with a banner |
| `sail.already_banner` | SHORT | bar | A banner hangs on this sail already (was: A banner already hangs on this sail: sneak-use the yard with an empty hand to take it back) | 35 (was 90) | a second banner |
| `sail.too_small` | SHORT | bar | Too small: yards %s+ long, %s+ apart (was: Too small for a banner: both yards must be at least %s blocks long and %s blocks apart) | 38 (was 88) | a banner on a small sail |
| `sail.stay_dye_only` | SHORT | bar | Triangular sails take dye only (was: A triangular sail takes dye only; banners go on large square sails) | 30 (was 66) | a banner on a triangular sail |
| `rope.line` | DROP | bar | ~~Rope line rigged~~ | 16 | rigging a decorative rope line |
| `rope.tied` | SHORT | bar | Tied: now a second cleat, ≤%s blocks (was: Rope tied here: now use it on a second cleat or mooring ring up to %s blocks away) | 37 (was 82) | the first end of a rope (the item remembers it; not visible) |
| `rope.rigged` | SHORT | bar | Stay rigged: put a cleat below its top (was: Stay rigged. A cleat straight below its upper end makes the sail) | 38 (was 64) | rigging a stay (says where the sail's third cleat goes) |
| `rope.elsewhere` | SHORT | bar | First end lost: the rope is tied here (was: The first anchor is gone or in another dimension: the rope is now tied here (ropes reach %s blocks)) | 37 (was 100) | the remembered end is gone |
| `rope.same` | SHORT | bar | Use it on a second cleat, ≤%s blocks (was: Use the rope on a second cleat or mooring ring, up to %s blocks away) | 37 (was 69) | the rope on its own first end |
| `rope.too_long` | SHORT | bar | Too far: %s blocks, a rope reaches %s (was: Too far: the anchors are %s blocks apart, a rope reaches %s) | 39 (was 61) | ends too far apart |
| `rope.too_flat` | SHORT | bar | Too flat: needs %s blocks of drop (was: Too flat: the two ends of a stay must be at least %s blocks apart in height) | 34 (was 76) | a stay without enough height |
| `rope.other_body` | SHORT | bar | Both ends on one ship, or both on land (was: The rope must stay on one ship: both ends on the same ship, or both on land) | 38 (was 75) | ends on two bodies |
| `rope.no_lines` | SHORT | bar | Rope lines are off: cleat to cleat only (was: Rope lines are off: a rope only runs between two cleats as a stay) | 39 (was 65) | a line with rope lines off |
| `rope.already` | KEEP | bar | These two are already roped together | 36 | two ends roped already |
| `rope.full` | KEEP | bar | An anchor holds at most %s ropes | 33 | an anchor full of ropes |

### Crew stations, whistle, orders, courses

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `crew.ack.drop_anchor` | DROP | chat | ~~Aye, letting go the anchor!~~ | 27 | a crew member starts an order ("Aye, ...!"): the work shows |
| `crew.ack.raise_anchor` | DROP | chat | ~~Aye, heave away!~~ | 16 | as above |
| `crew.ack.hoist` | DROP | chat | ~~Aye, hoisting the sails!~~ | 24 | as above |
| `crew.ack.reef` | DROP | chat | ~~Aye, reefing the sails!~~ | 23 | as above |
| `crew.ack.furl` | DROP | chat | ~~Aye, furling the sails!~~ | 23 | as above |
| `crew.ack.pump` | DROP | chat | ~~Aye, manning the pump!~~ | 22 | as above |
| `crew.ack.fire` | DROP | chat | ~~Aye, firing!~~ | 12 | as above |
| `crew.ack.load` | DROP | chat | ~~Aye, loading!~~ | 13 | as above |
| `crew.ack.fire_at_will` | DROP | chat | ~~Aye, firing at will!~~ | 20 | as above |
| `crew.ack.hold_course` | DROP | chat | ~~Aye, holding the course!~~ | 24 | the helmsman takes a course |
| `crew.course.waypoint` | DROP | chat | ~~Waypoint reached, steering for the next (%s of %s)~~ | 52 | the helmsman passes a waypoint (the ship turns) |
| `crew.course.arrived` | MOVE | bar | We've arrived, captain! (was: We've arrived, captain! Rudder midships) | 23 (was 39) | the helmsman reaches the last waypoint (crew line, players near him) |
| `crew.course.stuck` | MOVE | bar | We're stuck, captain! (was: We're not making headway, captain! We're stuck) | 21 (was 46) | a course makes no headway (crew line) |
| `crew.course.empty` | MOVE | bar | No course to hold, captain | 26 | a course order with nothing to hold (crew line) |
| `crew.course.unable` | MOVE | bar | This helm doesn't steer, captain! (was: This helm doesn't steer the ship, captain!) | 33 (was 42) | a course order at a helm that does not steer (crew line) |
| `crew.anchor_nothing.drop_anchor` | MOVE | bar | The anchor is out already, captain | 34 | a drop order while the anchor is out (crew line) |
| `crew.anchor_nothing.raise_anchor` | MOVE | bar | The anchor is stowed, captain | 29 | a raise order while the anchor is stowed (crew line) |
| `crew.anchor_unable.drop_anchor` | MOVE | bar | No ground in the chain's reach, captain! (was: No ground for the anchor within the chain's reach, captain!) | 40 (was 59) | a drop order with no ground below (crew line) |
| `crew.anchor_unable.raise_anchor` | MOVE | bar | The anchor is stowed already, captain | 37 | a raise order the capstan cannot carry out (crew line) |
| `crew.bilge_dry` | MOVE | bar | The bilge is dry, captain | 25 | a pump order on a dry bilge (crew line) |
| `crew.pump_unable` | MOVE | bar | This pump won't draw, captain! | 30 | a pump order the pump cannot carry out (crew line) |
| `crew.cannon_already_loaded` | MOVE | bar | The gun is already loaded, captain! | 35 | a load order at a loaded gun (crew line) |
| `crew.cannon_no_supply` | MOVE | bar | No powder and shot nearby, captain! (was: No powder and shot within reach, captain!) | 35 (was 41) | a load order without supplies (crew line) |
| `crew.cannon_not_loaded` | MOVE | bar | The gun is not loaded, captain! | 31 | a fire order at an empty gun (crew line) |
| `crew.cannon_unable` | MOVE | bar | This gun won't fire, captain! | 29 | a fire order the gun cannot carry out (crew line) |
| `crew.no_sails` | MOVE | bar | This ship has no sails, captain! | 32 | a sail order on a ship without sails (crew line) |
| `crew.no_sails_why` | DROP | chat | ~~This ship has no sails, captain: %s~~ | 36 | the same with the rigging's problem; the command still names it |
| `crew.nothing_to_do` | MOVE | bar | The sails are already set, captain (%s) | 40 | a sail order the sails already show (crew line) |
| `crew.wrong_station` | MOVE | bar | I can't %s from this station, captain! | 39 | an order to a crew member at another kind of station (crew line) |
| `crew.assigned` | SCREEN | screen | %s mans the station | 20 | assigning crew from the ship screen; the whistle no longer says it (the crew member walks there) |
| `crew.released` | SCREEN | screen | %s leaves the station | 22 | releasing crew from the ship screen; the whistle shows the crew line instead |
| `crew.taken` | KEEP | bar | Somebody already mans this station | 34 | the whistle on a manned station |
| `crew.not_a_station` | SHORT | bar | Not a station on an assembled ship (was: That is not a station on an assembled ship) | 34 (was 42) | the whistle on a block that is no station |
| `crew.disabled` | SHORT | bar | Crew stations are off on this server (was: Crew stations are disabled on this server) | 36 (was 41) | the whistle with crew stations off |
| `whistle.selected` | SHORT | bar | %s awaits orders, morale %s (was: %s awaits orders: use the whistle on a station) | 29 (was 47) | the whistle on a free crew member (selects it; shows its morale, formerly the chat crew line) |
| `whistle.no_selection` | KEEP | bar | Use the whistle on a crew member first | 38 | the whistle on a station with nobody selected |
| `whistle.not_on_ship` | KEEP | bar | You must stand on a ship to give orders | 39 | an order from the whistle off a ship |
| `whistle.order` | SHORT | bar | %2$s crew to %1$s (was: Order: %s (%s crew carry it out)) | 15 (was 34) | an order from the whistle's menu (how many heard it: not visible below deck) |
| `whistle.released_all` | SHORT | bar | %s crew released (was: %s crew members leave their stations) | 17 (was 37) | the menu's "Release crew" |
| `jobs.order_posted` | SHORT | bar | %2$s crew to %1$s, %3$s jobs open (was: Order: %s (%s crew carry it out, %s stations open for free hands)) | 30 (was 68) | an order that opens jobs for free hands |
| `jobs.no_free_hands` | KEEP | bar | No free hands to %s | 20 | an order nobody is free to take |
| `jobs.posted` | PART | - | %s open jobs posted: %s | 25 | /pirates crew order (command) |
| `order_hint.no_ship` | SHORT | bar | Stand on your ship's deck to give orders (was: No ship under you: stand on the deck of an assembled ship to give orders) | 40 (was 72) | an order off a ship |
| `order_hint.no_station` | SHORT | bar | No station aboard can %s (was: No station on this ship can %s: place one on the deck first) | 25 (was 60) | an order no station takes |
| `order_hint.no_sails` | KEEP | chat | No sails on this ship: %s | 26 | /pirates crew order on a ship without sails (command, with the rigging's problem); the whistle says "This ship has no sails" |
| `order_hint.cannot` | SHORT | bar | No station can %s right now (was: No station on this ship can %s right now) | 28 (was 41) | an order every station refuses |
| `order_hint.nothing_to_do` | SHORT | bar | Nothing left to %s (was: Nothing to do: every station that can %s is done already) | 19 (was 57) | an order every station has done |
| `order_hint.no_crew` | SHORT | bar | No crew free to %s (was: Nobody to %s: no crew at a station that takes it and none free on this deck (spawn or hire crew and bring them aboard)) | 19 (was 119) | an order with no crew at stations or free (was 118 characters) |
| `hammock.crew_line` | KEEP | bar | %s: morale %s, %s | 20 | the whistle releases a crew member: "Jack: morale 65, off duty" (was in chat with ship, hirer and supplies) |
| `hammock.status.free` | PART | - | off duty | 8 | the crew line |
| `hammock.status.hammock` | PART | - | in a hammock | 12 | the crew line |
| `hammock.status.station` | PART | - | at a station | 12 | the crew line |
| `hammock.ship_line` | PART | - | crew %s / bunks %s | 20 | /pirates crew info |
| `hammock.with_ship` | PART | - | %s · %s | 9 | /pirates crew info |

### Ship: assembly, rejoin, ship screen, hull, rigging, decor, bunks

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `assembly.assembled` | MOVE | bar | Ship assembled: %s blocks | 26 | assembling a ship at the helm (the block count is not visible) |
| `assembly.disassembled` | MOVE | bar | Disassembled: %s blocks placed back (was: Ship disassembled: %s blocks placed back) | 36 (was 41) | disassembling at the helm or from the ship screen |
| `assembly.adopted` | MOVE | bar | This helm now steers: %s blocks (was: This helm now steers the body it stands on: a ship of %s blocks) | 32 (was 64) | a helm makes an orphaned body its ship |
| `assembly.named` | MOVE | bar | Ship named "%s" | 16 | a name tag on the helm |
| `assembly.failed` | MOVE | bar | Assembly failed, see the server log | 35 | assembly threw (see the log) |
| `assembly.disabled` | MOVE | bar | Ship assembly is disabled on this server | 40 | assembly with assembly off |
| `assembly.moving` | MOVE | bar | Still moving (%s m/s): wait until still (was: The ship is still moving (%s m/s). Wait until it lies still) | 40 (was 60) | disassembling a moving ship |
| `assembly.no_ship` | MOVE | bar | This helm is on no assembled ship (was: This helm is not part of an assembled ship) | 33 (was 42) | a name tag on a helm on land |
| `assembly.not_level` | MOVE | bar | Tilted %s°, must be under %s° (was: The ship is tilted %s°. It must be within %s° of level) | 31 (was 56) | disassembling a tilted ship |
| `assembly.nothing_to_assemble` | MOVE | bar | Nothing to assemble: no blocks attached (was: Nothing to assemble: no ship blocks are connected to the helm) | 39 (was 61) | a lone helm |
| `assembly.obstructed` | MOVE | bar | Something is in the way at %s %s %s | 38 | disassembling into occupied blocks |
| `assembly.out_of_world` | MOVE | bar | Would leave the world at %s %s %s (was: The ship would end up outside the world at %s %s %s) | 36 (was 54) | disassembling beyond the world |
| `assembly.too_many_blocks` | MOVE | bar | Over %s blocks: touching land? (was: Too many connected blocks: a ship may have at most %s. Is the hull touching a dock or building?) | 31 (was 96) | assembling a hull fused to a dock |
| `assembly.helm_attached` | KEEP | bar | This helm now steers %s | 24 | placing a helm on a helmless ship (which ship it steers is not visible) |
| `assembly.second_helm` | SHORT | bar | Steered from the ship's other helm (was: This ship is steered from its other helm. Break that one to steer from here) | 34 (was 75) | using or placing a second helm |
| `assembly.disassemble_hint` | SHORT | bar | Sneak-use empty-handed to disassemble (was: Sneak-use the helm with an empty hand to disassemble the ship) | 37 (was 61) | using a helm without the steering module |
| `assembly.this_ship` | PART | - | the ship | 8 | a ship without a name |
| `rejoin.started` | KEEP | bar | Hammering %s blocks into place… | 32 | the toolkit starts hammering a piece on (nothing moves for a while) |
| `rejoin.rejoined` | DROP | bar | ~~Rejoined %s blocks~~ | 19 | the piece snaps into place, with a sound |
| `rejoin.marked` | SHORT | bar | Marked %s: now use on the other piece (was: Marked for repair: %s. Now use the toolkit on the piece to join to it) | 38 (was 70) | sneak-use marks the piece to keep (not visible) |
| `rejoin.add_planks` | KEEP | bar | Add planks to bridge the gap | 28 | a gap too wide |
| `rejoin.busy` | KEEP | bar | Still hammering | 15 | the toolkit again while hammering |
| `rejoin.different_ship` | KEEP | bar | These pieces belong to a different ship | 39 | pieces of two ships |
| `rejoin.disabled` | SHORT | bar | Rejoining is off on this server (was: Rejoining ship pieces is disabled on this server) | 31 (was 48) | rejoining off |
| `rejoin.failed` | KEEP | bar | Rejoining failed, see the server log | 36 | a rejoin threw |
| `rejoin.mark_gone` | SHORT | bar | The marked piece is gone: mark again (was: The marked piece is gone. Mark the piece to keep again) | 36 (was 54) | the marked piece left |
| `rejoin.no_mark` | SHORT | bar | Sneak-use the piece to keep first (was: First sneak-use the toolkit on the piece to keep) | 33 (was 48) | no piece marked |
| `rejoin.no_nails` | KEEP | bar | You need %s nails | 18 | not enough nails |
| `rejoin.no_toolkit` | KEEP | bar | You put the toolkit away | 24 | the toolkit put away while hammering |
| `rejoin.not_a_ship` | KEEP | bar | This is not part of a ship | 26 | the toolkit on land |
| `rejoin.not_aligned` | SHORT | bar | Not lined up: within %s° and %s blocks (was: The pieces are not lined up: bring them within %s° and %s blocks of their places) | 40 (was 82) | pieces not lined up |
| `rejoin.out_of_plot` | SHORT | bar | This piece reaches too far to join (was: This piece reaches too far from the ship to join it) | 34 (was 51) | a piece beyond the ship's plot |
| `rejoin.overlap` | SHORT | bar | The pieces overlap (was: The pieces overlap: something is in the way) | 18 (was 43) | overlapping pieces |
| `rejoin.same_piece` | SHORT | bar | This is the marked piece: use the other (was: This is the marked piece. Use the toolkit on the piece to join to it) | 39 (was 68) | the toolkit on the marked piece |
| `rejoin.too_big` | SHORT | bar | Too big to nail: %s blocks, at most %s (was: This piece is too big to nail on: %s blocks, at most %s. A shipwright can join bigger halves) | 40 (was 94) | a piece over the toolkit's limit |
| `rejoin.too_far` | KEEP | bar | Bring the pieces together | 25 | pieces apart |
| `ship_screen.not_yours` | SHORT | bar | Not your ship (was: This is not your ship: only her captain manages her at the helm) | 13 (was 63) | sneak-using another captain's helm |
| `ship_screen.disabled` | SHORT | bar | The ship screen is off on this server (was: The ship screen is turned off on this server) | 37 (was 44) | the ship screen off |
| `ship_screen.not_your_hand` | SHORT | screen | %s takes no orders from you (was: Only the captain or whoever hired %s can order them about) | 28 (was 58) | ordering another captain's hand in the screen |
| `ship_screen.bad_name` | SCREEN | screen | Type a name first | 17 | renaming without a name |
| `ship_screen.gone` | SCREEN | screen | The ship is gone | 16 | the ship is gone |
| `ship_screen.no_crew` | SCREEN | screen | That hand is no longer aboard | 29 | a hand left the ship |
| `ship_screen.not_at_station` | SCREEN | screen | %s mans no station | 19 | releasing a free hand |
| `ship_screen.not_on_this_ship` | SCREEN | screen | That station is not on this ship | 32 | a station of another ship |
| `ship_screen.stale` | SCREEN | screen | This screen shows another ship | 30 | the screen shows another ship |
| `ship_screen.too_far` | SCREEN | screen | You stepped away from the helm | 30 | walking away from the helm |
| `ship_screen.unknown_order` | SCREEN | screen | No such order | 13 | an unknown order |
| `bilge_pump.pumping` | KEEP | bar | Pumping: %s blocks of water left | 33 | working a pump by hand: water left below (not visible from the pump) |
| `bilge_pump.dry` | KEEP | bar | The bilge is dry | 16 | a pump on a dry bilge |
| `bilge_pump.no_compartment` | SHORT | bar | No hold within %s blocks below the pump (was: The pump draws air: no hold within %s blocks below it) | 40 (was 54) | a pump with no hold below |
| `bilge_pump.not_on_ship` | SHORT | bar | Pumps only work on an assembled ship (was: A bilge pump only works on an assembled ship) | 36 (was 44) | a pump on land |
| `bilge_pump.disabled` | KEEP | bar | Bilge pumps are disabled on this server | 39 | pumps off |
| `hull_patch.blocked` | KEEP | bar | Something is stuck in this hole | 31 | a patch into a blocked hole |
| `hull_patch.disabled` | KEEP | bar | Hull patches are disabled on this server | 40 | patches off |
| `hull_patch.not_a_breach` | KEEP | bar | Use the patch on a hole in the hull | 35 | a patch on no hole |
| `hull_patch.not_on_ship` | SHORT | bar | Patches only work on an assembled ship (was: Hull patches only close holes in an assembled ship) | 38 (was 50) | a patch on land |
| `ratlines.disabled` | KEEP | bar | Ratlines are disabled on this server | 36 | ratlines off |
| `ratlines.in_cloth` | SHORT | bar | Sail cloth here: use the yard's end (was: The sail's cloth hangs here: hang the ratlines on the yard's end) | 35 (was 64) | ratlines where the cloth hangs |
| `flag.no_flag` | SHORT | bar | No flag: use a flag or banner on it (was: This flagpole flies no flag. Use a flag or a banner on it.) | 35 (was 58) | working an empty flagpole |
| `flag.struck` | KEEP | bar | Colors struck: you signal surrender | 35 | colours struck: the surrender has consequences |
| `flag.started_hoist` | DROP | bar | ~~Hoisting the %s...~~ | 19 | starting to hoist (the flag climbs) |
| `flag.started_raise` | DROP | bar | ~~Raising the %s...~~ | 18 | starting to raise |
| `flag.started_strike` | DROP | bar | ~~Striking the colors...~~ | 22 | starting to strike |
| `flag.started_take_down` | DROP | bar | ~~Taking down the %s...~~ | 22 | starting to take down |
| `flag.hoisted` | DROP | bar | ~~The %s is flying~~ | 17 | the flag is up |
| `flag.raised` | DROP | bar | ~~The %s is flying again~~ | 23 | the flag is up again |
| `flag.taken_down` | DROP | bar | ~~Took down the flag~~ | 18 | the flag is down |
| `flag.cancelled` | DROP | bar | ~~Stopped working the flagpole~~ | 28 | letting go of the pole mid-way |
| `flag.pole_too_tall` | SHORT | bar | A flagpole is at most %s blocks tall (was: A flagpole can be at most %s blocks tall) | 37 (was 41) | a flagpole over the height limit |
| `sea_cot.no_sleeping` | KEEP | bar | This cot is just for show | 25 | sleeping in the cot with cot sleeping off |
| `sea_cot.on_ship` | SHORT | bar | Just for show while aboard a ship (was: This cot is just for show while it is aboard a ship) | 33 (was 51) | the cot aboard with sleeping aboard off |
| `hammock.crew_only` | SHORT | bar | Hammocks are for the crew (was: Hammocks are for the crew: they turn in here at night) | 25 (was 53) | a player in a hammock with player sleep off |
| `hammock.crew_in_it` | SHORT | bar | A crew member sleeps in this hammock (was: A crew member is sleeping in this hammock) | 36 (was 41) | a player at an occupied hammock |
| `hammock.no_support` | SHORT | bar | Needs a support at each end, same height (was: A hammock hangs between two supports at the same height: a fence, wall, log or solid block at each end) | 40 (was 102) | placing a hammock without supports |
| `hammock.no_hammock` | DROP | chat | ~~No hammock for me… another night on the bare planks.~~ | 52 | a crew member grumbles at dawn after a night without a hammock (per crew member, every dawn); morale shows it |
| `bunk.not_here` | KEEP | bar | You can't sleep here | 20 | sleeping where it is not possible |

### Combat: cannon, swivel gun, grapple, boarding plank, melee

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `cannon.fired` | DROP | bar | ~~Fire!~~ | 5 | firing a cannon (the shot is seen and heard) |
| `cannon.powder_in` | SHORT | bar | Powder in: now load the shot (was: Powder in. Now load a cannonball, chain shot or grapeshot) | 28 (was 57) | powder into a cannon (not visible) |
| `cannon.ball_in` | SHORT | bar | Loaded: empty hand to fire (was: Loaded. Use with an empty hand to fire) | 26 (was 38) | shot into a cannon |
| `cannon.already_powdered` | SHORT | bar | Powder is in: now load the shot (was: The powder is in. Load a cannonball, chain shot or grapeshot) | 31 (was 60) | powder twice |
| `cannon.already_loaded` | SHORT | bar | Already loaded: empty hand to fire (was: Already loaded. Use with an empty hand to fire) | 34 (was 46) | loading a loaded gun |
| `cannon.not_loaded` | SHORT | bar | Not loaded: powder first, then shot (was: Not loaded: put in gunpowder, then a cannonball) | 35 (was 47) | firing an empty gun |
| `cannon.needs_powder_first` | KEEP | bar | Powder first, then the shot | 27 | shot before powder |
| `cannon.reloading` | KEEP | bar | The barrel is still hot: %s s | 30 | a hot barrel |
| `cannon.aimed` | KEEP | bar | Elevation: %s° | 15 | sneak-use to aim: the elevation in degrees |
| `cannon.disabled` | KEEP | bar | Cannons are disabled on this server | 35 | cannons off |
| `cannon.not_a_cannon` | KEEP | bar | That is not a cannon | 20 | a broken multiblock |
| `cannon.shot_disabled` | KEEP | bar | This shot is disabled on this server | 36 | a disabled shot kind |
| `swivel.fired` | DROP | bar | ~~Fire!~~ | 5 | firing a swivel gun |
| `swivel.powder_in` | SHORT | bar | Powder in: now load the shot (was: Powder in. Now load the shot) | 28 (was 28) | powder into a swivel gun |
| `swivel.shot_in` | SHORT | bar | Loaded: hold use to aim, let go to fire (was: Loaded. Hold use with an empty hand to aim, let go to fire) | 39 (was 58) | shot into a swivel gun |
| `swivel.already_loaded` | SHORT | bar | Loaded: hold use to aim, let go to fire (was: Loaded. Hold use with an empty hand to aim, let go to fire) | 39 (was 58) | loading a loaded swivel |
| `swivel.already_powdered` | SHORT | bar | Powder is in: now load the shot (was: The powder is in. Load the shot) | 31 (was 31) | powder twice |
| `swivel.aiming` | KEEP | bar | Aiming: let go to fire | 22 | starting to aim |
| `swivel.aiming_unloaded` | SHORT | bar | Not loaded: powder first, then shot (was: Aiming, but not loaded: gunpowder first, then the shot) | 35 (was 54) | aiming an empty swivel |
| `swivel.not_loaded` | SHORT | bar | Not loaded: powder first, then shot (was: Not loaded: put in gunpowder, then the shot) | 35 (was 43) | firing an empty swivel |
| `swivel.needs_powder_first` | KEEP | bar | Powder first, then the shot | 27 | shot before powder |
| `swivel.not_enough_ammo` | KEEP | bar | One load takes %s × %s | 24 | too few balls for a load |
| `swivel.occupied` | KEEP | bar | Someone else is aiming this gun | 31 | another player aims it |
| `swivel.reloading` | KEEP | bar | The barrel is still hot: %s s | 30 | a hot barrel |
| `swivel.disabled` | KEEP | bar | Swivel guns are disabled on this server | 39 | swivels off |
| `swivel.not_a_swivel` | KEEP | bar | That is not a swivel gun | 24 | a missing gun |
| `grapple.tied` | DROP | bar | ~~Rope tied off here~~ | 18 | tying the hook's rope off (a knot sound and the rope show it) |
| `grapple.already_tied` | KEEP | bar | The rope is already tied off here | 33 | tying off twice |
| `grapple.tie_same_ship` | SHORT | bar | Tie the rope on your own ship (was: The hook hangs on this ship: tie the rope on your own ship) | 29 (was 58) | tying off on the hooked ship |
| `grapple.tie_too_far` | KEEP | bar | The rope does not reach this far | 32 | a ring beyond the rope |
| `grapple.tie_off_hint` | SHORT | bar | Tie it off on a cleat to make a line (was: Tie the rope off on a cleat to use it as a line) | 36 (was 47) | sliding an untied rope (at most every few seconds) |
| `grapple.slide_disabled` | KEEP | bar | Sliding along ropes is disabled | 31 | sliding off |
| `grapple.musket_loaded` | SHORT | bar | Fire the shot before loading the hook (was: The musket is loaded with shot: fire it before loading the hook) | 37 (was 63) | a hook into a loaded musket |
| `grapple.no_powder` | SHORT | bar | The hook needs one gunpowder (was: Loading the hook into the musket takes one gunpowder) | 28 (was 52) | a hook without powder |
| `boarding_plank.blocked` | KEEP | bar | Something is in the way %s blocks out | 38 | a plank into blocks (BRD2) |
| `boarding_plank.disabled` | SHORT | bar | Boarding planks are off on this server (was: Boarding planks are disabled on this server) | 38 (was 43) | planks off |
| `boarding_plank.no_start` | KEEP | bar | No plank from here | 18 | a plank from no edge (BRD2) |
| `boarding_plank.too_far` | KEEP | bar | Too far: a plank reaches %s blocks | 35 | nothing within the plank's reach (BRD2) |
| `boarding_plank.too_steep` | KEEP | bar | Too steep: at most %s degrees (BRD4; BRD2: Too steep: at most %s blocks up or down) | 29 | a landing too high or low for the steepest pitch (BRD2, BRD4) |
| `boarding_plank.nothing_to_land` | KEEP | bar | Nothing to land on within reach | 31 | no landing in reach (BRD2) |
| `melee.refused.locked_out` | KEEP | bar | Can't parry again so soon | 25 | a parry too soon (client) |
| `melee.refused.no_stamina` | KEEP | bar | Too exhausted | 13 | no stamina (client) |
| `melee.refused.staggered` | KEEP | bar | Staggered | 9 | staggered (client) |

### Lookout (since LKO2: an action-bar cue)

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `lookout.kraken` | DROP | chat | ~~Kraken! Something vast stirs %s, %s blocks off!~~ | 49 | the lookout's chat sighting; LKO2 replaced it with the action-bar cue |
| `lookout.land` | DROP | chat | ~~Land ho! Land %s, %s blocks~~ | 29 | the lookout's chat sighting; LKO2 replaced it with the action-bar cue |
| `lookout.shark` | DROP | chat | ~~Shark in the water %s, %s blocks!~~ | 35 | the lookout's chat sighting; LKO2 replaced it with the action-bar cue |
| `lookout.ship` | DROP | chat | ~~Sail ho! %s %s, %s blocks~~ | 28 | the lookout's chat sighting; LKO2 replaced it with the action-bar cue |
| `lookout.ship_named` | DROP | chat | ~~Sail ho! %s %s, %s blocks: it's %s!~~ | 39 | the lookout's chat sighting; LKO2 replaced it with the action-bar cue |
| `lookout.bearing.abaft` | DROP | - | ~~%s abaft the %s beam~~ | 22 | part of the old chat sighting, removed by LKO2 |
| `lookout.bearing.bow` | DROP | - | ~~%s off the %s bow~~ | 19 | part of the old chat sighting, removed by LKO2 |
| `lookout.nest` | DROP | - | ~~Crow's nest~~ | 11 | part of the old chat sighting, removed by LKO2 |
| `lookout.what.ship` | DROP | - | ~~A ship~~ | 6 | part of the old chat sighting, removed by LKO2 |
| `lookout.bearing.aft` | PART | - | aft to %s | 10 | part of the lookout's cue (LKO2) |
| `lookout.bearing.ahead` | PART | - | ahead (was: dead ahead) | 5 (was 10) | part of the lookout's cue (LKO2) |
| `lookout.bearing.astern` | PART | - | astern (was: dead astern) | 6 (was 11) | part of the lookout's cue (LKO2) |
| `lookout.bearing.beam` | PART | - | abeam to %s (was: on the %s beam) | 12 (was 15) | part of the lookout's cue (LKO2) |
| `lookout.bearing.points` | PART | - | %s to %s | 10 | part of the lookout's cue (LKO2) |
| `lookout.bearing.points.1` | PART | - | one point | 9 | part of the lookout's cue (LKO2) |
| `lookout.bearing.points.2` | PART | - | two points | 10 | part of the lookout's cue (LKO2) |
| `lookout.bearing.points.3` | PART | - | three points | 12 | part of the lookout's cue (LKO2) |
| `lookout.bearing.points.4` | PART | - | four points | 11 | part of the lookout's cue (LKO2) |
| `lookout.bearing.points.5` | PART | - | five points | 11 | part of the lookout's cue (LKO2) |
| `lookout.bearing.points.6` | PART | - | six points | 10 | part of the lookout's cue (LKO2) |
| `lookout.bearing.points.7` | PART | - | seven points | 12 | part of the lookout's cue (LKO2) |
| `lookout.bearing.port` | PART | - | port | 4 | part of the lookout's cue (LKO2) |
| `lookout.bearing.quarter` | PART | - | on the %s quarter | 18 | part of the lookout's cue (LKO2) |
| `lookout.bearing.side` | PART | - | to %s | 6 | part of the lookout's cue (LKO2) |
| `lookout.bearing.starboard` | PART | - | starboard | 9 | part of the lookout's cue (LKO2) |
| `lookout.cue` | KEEP | bar | %s %s, %s | 12 | a sighting from the crow's nest (LKO2): what, bearing, distance; SightingCue cuts the rendered line to 40 |
| `lookout.cue.blocks` | PART | - | %s blocks | 10 | part of the lookout's cue (LKO2) |
| `lookout.what.kraken` | PART | - | Kraken | 6 | part of the lookout's cue (LKO2) |
| `lookout.what.land` | PART | - | Land | 4 | part of the lookout's cue (LKO2) |
| `lookout.what.merchant` | PART | - | Merchant (was: A merchant) | 8 (was 10) | part of the lookout's cue (LKO2) |
| `lookout.what.navy` | PART | - | Navy ship (was: A navy ship) | 9 (was 11) | part of the lookout's cue (LKO2) |
| `lookout.what.pirate` | PART | - | Pirate ship (was: A pirate ship) | 11 (was 13) | part of the lookout's cue (LKO2) |
| `lookout.what.sail` | PART | - | Sail | 4 | part of the lookout's cue (LKO2) |
| `lookout.what.shark` | PART | - | Shark | 5 | part of the lookout's cue (LKO2) |
| `lookout.what.wreck` | PART | - | Wreck (was: A wreck) | 5 (was 7) | part of the lookout's cue (LKO2) |

### Crew upkeep, galley, hiring

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `upkeep.say.unpaid` | DROP | chat | ~~No pay again? A sailor can't live on promises.~~ | 46 | every unpaid crew member grumbles at dawn; the owner's pay line says it once |
| `upkeep.say.desert` | DROP | chat | ~~I've had enough of this ship. I'm off.~~ | 38 | a deserter's parting line; the owner's report names him |
| `upkeep.say.deserting` | DROP | chat | ~~That's it. I'm off at the next port.~~ | 36 | a crew member marks itself a deserter; the owner's report names him |
| `upkeep.say.walk_off` | DROP | chat | ~~Fair winds, captain. Find yourself another fool.~~ | 48 | a deserter walks off at a port; the owner's report names him |
| `upkeep.say.mutiny` | DROP | chat | ~~The ship is ours now!~~ | 21 | each mutineer shouts; the owner gets "Mutiny aboard" |
| `upkeep.no_food` | SHORT | bar | No food aboard (was: The crew has no food) | 14 (was 20) | dawn report to the owner aboard: no food |
| `upkeep.no_water` | SHORT | bar | No water aboard (was: The crew has no water) | 15 (was 21) | dawn report: no water |
| `upkeep.scurvy` | SHORT | bar | Scurvy! The crew needs fresh food (was: Scurvy aboard: the crew needs citrus or fresh food) | 33 (was 50) | dawn report: scurvy |
| `upkeep.paid` | KEEP | bar | Paid %s crew, %s doubloons | 28 | dawn report: wages paid (coins leave the ship's chests) |
| `upkeep.paid_wallet` | SHORT | bar | Paid %s crew: %s doubloons, %s yours (was: Paid %s crew, %s doubloons, %s of them from your purse) | 39 (was 57) | dawn report: wages paid partly from the purse |
| `upkeep.unpaid` | KEEP | bar | Could not pay %s crew | 22 | dawn report: wages not paid |
| `upkeep.join` | PART | - | %s · %s | 9 | joins the dawn report's parts (a rare day with several parts may pass 40 characters) |
| `upkeep.wallet_paid` | CHAT | chat | Wages from your purse: %s doubloons | 36 | wages from the purse while the owner is not aboard (state from afar) |
| `upkeep.deserted` | CHAT | chat | %s has deserted | 16 | a crew member deserted (the owner may be anywhere) |
| `upkeep.deserting` | CHAT | chat | %s means to desert and will walk off at the next port | 54 | a crew member will desert at the next port |
| `upkeep.stays` | CHAT | chat | %s has changed their mind and stays aboard | 43 | a marked deserter changed its mind |
| `upkeep.walked_off` | CHAT | chat | %s walked off at %s | 21 | a deserter walked off at a port |
| `upkeep.mutiny` | CHAT | chat | Mutiny aboard %s! | 18 | mutiny: the ship is lost |
| `upkeep.unnamed_ship` | PART | - | your ship | 9 | a ship without a name |
| `upkeep.status.deserting` | PART | - | %s, deserting | 14 | the crew line |
| `upkeep.status.unpaid` | PART | - | %s, unpaid | 11 | the crew line |
| `upkeep.status.hired_by` | PART | - | %s · hired by %s | 18 | /pirates crew info |
| `upkeep.status.supplies` | PART | - | %s · food %s days, water %s days | 35 | /pirates crew info |
| `upkeep.info.pay` | PART | - | Last pay: %s paid, %s unpaid, %s doubloons | 45 | crew info (command, ship screen) |
| `upkeep.info.pay_none` | PART | - | Last pay: no payday yet | 23 | crew info |
| `upkeep.info.pay_off` | PART | - | Last pay: wages are off | 23 | crew info |
| `upkeep.info.pay_wallet` | PART | - | Last pay: %s paid, %s unpaid, %s doubloons, %s of them from the owner's purse | 81 | crew info |
| `upkeep.info.plenty` | PART | - | plenty | 6 | crew info |
| `upkeep.info.supplies` | PART | - | Supplies: food %s days, water %s days, rum %s days | 53 | crew info |
| `upkeep.info.work` | PART | - | Work speed %s%% | 16 | crew info |
| `water_barrel.info` | KEEP | bar | Water barrel: %s / %s water rations | 37 | using a water barrel: rations in it |
| `barrel_water` | PART | - | barrel water rations | 20 | a ration name |
| `pantry.info` | CHAT | chat | Pantry: %s food (%s nutrition), %s water rations, %s rum, weight %s | 72 | using a pantry: its contents, several lines (an inventory readout, not ship handling) |
| `pantry.empty` | CHAT | chat | The pantry holds no provisions | 30 | using an empty pantry |
| `pantry.next_spoil` | CHAT | chat | Next to spoil: %s %s in %s days | 34 | pantry readout |
| `pantry.no_spoil` | CHAT | chat | Nothing in here spoils | 22 | pantry readout |
| `hiring.result.dismissed` | KEEP | bar | %s is dismissed | 16 | sneak-using the whistle on a crew member (it becomes a neutral sailor) |
| `hiring.result.not_yours` | SHORT | bar | Not yours to dismiss: %s (was: Only the captain or the one who hired %s can dismiss them) | 25 (was 58) | dismissing someone else's hand |
| `hiring.result.not_crew` | KEEP | bar | %s is no crew member | 21 | dismissing a non-crew |
| `hiring.result.disabled` | KEEP | bar | Hiring is turned off | 20 | hiring off |
| `hiring.result.hired` | SCREEN | screen | %s signed on and is aboard your ship | 37 | the harbour desk's Crew tab |
| `hiring.result.no_bunk` | SCREEN | screen | No free hammock aboard | 22 | the harbour desk's Crew tab |
| `hiring.result.no_coins` | SCREEN | screen | %s asks more doubloons than you carry | 38 | the harbour desk's Crew tab |
| `hiring.result.no_port` | SCREEN | screen | This desk belongs to no known port | 34 | the harbour desk's Crew tab |
| `hiring.result.no_room` | SCREEN | screen | No room on deck for %s | 23 | the harbour desk's Crew tab |
| `hiring.result.no_session` | SCREEN | screen | Step up to the harbor desk first | 32 | the harbour desk's Crew tab |
| `hiring.result.no_ship` | SCREEN | screen | Moor your ship at this port first | 33 | the harbour desk's Crew tab |
| `hiring.result.not_enlisted` | SCREEN | screen | Navy ratings sign on only with an enlisted officer | 50 | the harbour desk's Crew tab |
| `hiring.result.pirates_distrust` | SCREEN | screen | Pirates sign on only with a friend of the pirates or a captain of some infamy | 77 | the harbour desk's Crew tab |
| `hiring.result.unknown` | SCREEN | screen | That candidate has found another berth | 38 | the harbour desk's Crew tab |
| `hiring.result.villagers_refuse` | SCREEN | screen | The villagers won't sign on with you | 36 | the harbour desk's Crew tab |
| `hiring.result.wrong_port` | SCREEN | screen | %s does not sign on at this port | 33 | the harbour desk's Crew tab |
| `hiring.tab.candidates` | SCREEN | screen | Looking for a berth | 19 | the harbour desk's Crew tab |
| `hiring.tab.detail` | SCREEN | screen | %s, fee %s, wage %s a day | 28 | the harbour desk's Crew tab |
| `hiring.tab.hire` | SCREEN | screen | Hire | 4 | the harbour desk's Crew tab |
| `hiring.tab.no_candidates` | SCREEN | screen | Nobody is looking for a berth today | 35 | the harbour desk's Crew tab |
| `hiring.tab.no_ship_here` | SCREEN | screen | No ship of yours is moored here | 31 | the harbour desk's Crew tab |
| `hiring.tab.ship` | SCREEN | screen | Your ship: %s, crew %s / %s | 30 | the harbour desk's Crew tab |
| `hiring.tab.ship_full` | SCREEN | screen | No free hammock aboard | 22 | the harbour desk's Crew tab |
| `hiring.tab.ship_unnamed` | SCREEN | screen | unnamed | 7 | the harbour desk's Crew tab |
| `hiring.tab.too_poor` | SCREEN | screen | Not enough doubloons | 20 | the harbour desk's Crew tab |
| `hiring.kind.navy` | SCREEN | screen | Navy rating | 11 | the harbour desk's Crew tab |
| `hiring.kind.pirate` | SCREEN | screen | Pirate | 6 | the harbour desk's Crew tab |
| `hiring.kind.sailor` | SCREEN | screen | Sailor | 6 | the harbour desk's Crew tab |

### Trade, ports, desks, cargo

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `harbor_dues.desk_owed` | SHORT | bar | Dues owed: %s, pay with coins in hand (was: Harbor dues owed: %s. Use the desk with doubloons in hand to pay) | 38 (was 65) | using the harbour desk while dues are owed |
| `harbor_dues.desk_settled` | CHAT | chat | Harbor dues paid: %s doubloons | 31 | paying dues at the desk (the harbour master's receipt) |
| `harbor_dues.owed` | CHAT | chat | Harbor dues at %s: %s doubloons owed for %s. Pay at the harbor desk with doubloons in hand | 93 | mooring in a port, dues owed (the harbour master) |
| `harbor_dues.paid` | CHAT | chat | Harbor dues at %s: %s doubloons | 33 | mooring, dues paid from the purse |
| `harbor_dues.paid_ship` | CHAT | chat | Harbor dues at %s: %s doubloons, taken from the coins aboard %s | 66 | mooring, dues paid from the ship's coins |
| `harbor_dues.waived` | CHAT | chat | Harbor dues at %s: waived | 26 | mooring, dues waived |
| `harbor_dues.your_ship` | PART | - | your ship | 9 | a ship without a name |
| `harbor_desk.no_market` | KEEP | bar | This desk's port has no market | 30 | a desk without a market |
| `harbor_desk.talk_to_master` | KEEP | bar | Talk to the harbor master | 25 | a desk whose master is away |
| `harbor_desk.unbound` | KEEP | bar | This desk does not belong to a port | 35 | a desk of no port |
| `harbor_master.greeting.navy_outpost` | CHAT | chat | %s: "State your business, captain. The Crown's ledger is open." | 64 | talking to a harbour master (a person) |
| `harbor_master.greeting.pirate_island` | CHAT | chat | %s: "Coin first, questions never. What've you got?" | 52 | as above |
| `harbor_master.greeting.seafarer_village` | CHAT | chat | %s: "Welcome ashore, captain. Goods, contracts or a new ship?" | 63 | as above |
| `harbor_master.no_desk` | KEEP | bar | "I've no desk to serve you at here." | 36 | a harbour master without a desk |
| `harbor_master.not_now` | SHORT | bar | The harbor master waves you off (was: The harbor master waves you off: not now!) | 31 (was 41) | a harbour master who will not talk |
| `cargo.holds` | KEEP | bar | %s: %s / %s %s | 18 | loading or inspecting a cargo container: what it holds |
| `cargo.holds_plundered` | KEEP | bar | %s: %s / %s %s (plundered) | 30 | as above, plundered |
| `cargo.empty` | KEEP | bar | %s: empty | 10 | an empty container |
| `cargo.refused.empty_stack` | KEEP | bar | Nothing to put in | 17 | nothing in hand |
| `cargo.refused.full` | KEEP | bar | Full, refused %s (%s) | 23 | a full container |
| `cargo.refused.not_accepted` | KEEP | bar | %s is no trade good (%s) | 26 | no trade good |
| `cargo.refused.not_stackable` | KEEP | bar | %s doesn't go into bulk cargo (%s) | 36 | an unstackable item |
| `cargo.refused.other_kind` | KEEP | bar | Holds one kind only, refused %s (%s) | 38 | a second kind |
| `cargo.refused.plunder_mix` | SHORT | bar | Plunder and clean goods don't mix (was: Plundered and clean goods don't mix, refused %s (%s)) | 33 (was 54) | mixing plunder and clean goods |
| `ship_order.picked_up` | CHAT | chat | Your %s lies at berth %s | 26 | a shipwright receipt at the desk (the shipwright) |
| `ship_order.not_assembled` | CHAT | chat | Your %s lies at berth %s, but could not be assembled: use its helm | 68 | as above, the ship could not be assembled |
| `ship_order.disabled` | CHAT | chat | The shipwright takes no orders on this server | 45 | the shipwright desk: order and pickup answers |
| `ship_order.lost` | CHAT | chat | This order was lost | 19 | the shipwright desk: order and pickup answers |
| `ship_order.no_berth` | CHAT | chat | No berth is free; come back later | 33 | the shipwright desk: order and pickup answers |
| `ship_order.no_coins` | CHAT | chat | You need %s doubloons | 22 | the shipwright desk: order and pickup answers |
| `ship_order.no_logs` | CHAT | chat | You need %s logs | 17 | the shipwright desk: order and pickup answers |
| `ship_order.no_session` | CHAT | chat | You are too far from the desk | 29 | the shipwright desk: order and pickup answers |
| `ship_order.no_wool` | CHAT | chat | You need %s wool | 17 | the shipwright desk: order and pickup answers |
| `ship_order.not_ready` | CHAT | chat | Not ready: %s days to go | 25 | the shipwright desk: order and pickup answers |
| `ship_order.not_village` | CHAT | chat | There is no shipwright at this port | 35 | the shipwright desk: order and pickup answers |
| `ship_order.ordered` | CHAT | chat | Ordered: %s, ready in %s days. Keep the receipt | 49 | the shipwright desk: order and pickup answers |
| `ship_order.too_many` | CHAT | chat | The shipwright already has %s ships on the slipway; come back later | 68 | the shipwright desk: order and pickup answers |
| `ship_order.unknown_template` | CHAT | chat | The shipwright does not build %s | 33 | the shipwright desk: order and pickup answers |
| `ship_order.wrong_port` | CHAT | chat | This receipt belongs to the shipwright at %s | 45 | the shipwright desk: order and pickup answers |

### Law: brig, officers, notices

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `brig.lead.start` | DROP | bar | ~~You lead %s~~ | 12 | leading a prisoner on the chain (the chain shows it) |
| `brig.lead.stop` | DROP | bar | ~~You let go of %s's chain~~ | 25 | letting go of the chain |
| `brig.lead.take_over` | KEEP | bar | You take %s's chain | 20 | taking another captor's prisoner |
| `brig.lead.someone_else` | KEEP | bar | This prisoner is led by %s | 27 | a prisoner led by someone else |
| `brig.lead.lost` | KEEP | bar | You lost hold of %s | 20 | the chain broke (out of reach) |
| `brig.capture.ok` | KEEP | bar | %s is in shackles | 18 | shackling a target |
| `brig.capture.already_prisoner` | KEEP | bar | %s is already a prisoner | 25 | shackling a prisoner |
| `brig.capture.dead` | KEEP | bar | %s is beyond capture | 21 | shackling the dead |
| `brig.capture.not_capturable` | KEEP | bar | %s can't be shackled | 21 | shackling what cannot be |
| `brig.capture.self` | KEEP | bar | You can't shackle yourself | 26 | shackling yourself |
| `brig.capture.no_bounty` | SHORT | bar | Only players with a bounty can be taken (was: Only players with a bounty can be captured) | 39 (was 42) | shackling a player without bounty |
| `brig.capture.player_capture_disabled` | SHORT | bar | Capturing players is off on this server (was: Capturing players is disabled on this server) | 39 (was 44) | player capture off |
| `brig.capture.too_healthy` | SHORT | bar | %s is too strong: weaken them first (was: %s is still too strong. Weaken them first) | 36 (was 42) | a healthy target |
| `brig.captured_player` | CHAT | chat | You were captured by %s | 24 | you were shackled (state you must know) |
| `brig.player_freed` | CHAT | chat | You slipped out of your shackles | 32 | you slipped your shackles |
| `brig.in_cell` | KEEP | bar | %s is locked in the brig | 25 | your prisoner is in the brig (it may be out of sight) |
| `brig.escaped` | KEEP | bar | %s escaped! | 12 | your prisoner escaped |
| `brig.door.locked` | KEEP | bar | Brig door locked | 16 | locking a brig door (the lock is not visible) |
| `brig.door.unlocked` | KEEP | bar | Brig door unlocked | 18 | unlocking it |
| `brig.door.is_locked` | KEEP | bar | The brig door is locked | 23 | a locked brig door |
| `brig.door.needs_key` | SHORT | bar | You need a brig key (was: You need a brig key to lock or unlock this door) | 19 (was 47) | a brig door without a key |
| `brig.door.not_owner` | SHORT | bar | Only the door's owner can lock it (was: Only the door's owner can lock or unlock it) | 33 (was 43) | someone else's door |
| `brig.pressgang.disabled` | KEEP | bar | Press-ganging is disabled on this server | 40 | press-ganging off |
| `brig.pressgang.not_a_prisoner` | SHORT | bar | Only a shackled sailor can be pressed (was: Only a shackled sailor can be pressed into a crew) | 37 (was 49) | press-ganging a free sailor |
| `brig.pressgang.not_a_sailor` | KEEP | bar | Only sailors can be pressed into a crew | 39 | press-ganging a non-sailor |
| `brig.pressgang.not_on_ship` | KEEP | bar | Bring them aboard your ship first. | 34 | press-ganging ashore |
| `brig.pressgang.not_your_ship` | KEEP | bar | This isn't your ship to crew. | 29 | press-ganging on another ship |
| `brig.pressgang.done` | CHAT | chat | %s, grumbling: "Aye... Captain." Pressed into your crew | 56 | the prisoner speaks as he joins the crew |
| `brig.release.done` | CHAT | chat | "Go, before I change my mind." %s is free | 42 | releasing a prisoner (the captain speaks) |
| `law.theft_seen` | KEEP | bar | %s saw you stealing! | 21 | a villager saw you steal |
| `flags.cover_blown` | CHAT | chat | The navy has seen through your colours | 38 | the navy saw through your colours (notice) |
| `law.plunder_reported` | CHAT | chat | The harbor master reported you to the navy for offering stolen goods | 68 | the harbour master reported you (notice) |
| `law.plunder_spotted` | CHAT | chat | Your cover is blown: the navy has spotted plunder in your hold | 62 | the navy spotted plunder (notice) |
| `officer.hostile` | SHORT | bar | The officer won't deal with a criminal (was: The officer won't deal with a wanted criminal) | 38 (was 45) | a wanted player at an officer |
| `officer.fine.notorious` | SHORT | bar | "No coin buys your pardon." (was: "No coin buys a pardon for the likes of you.") | 27 (was 45) | paying a fine as notorious |
| `officer.fine.too_poor` | SHORT | bar | "Not enough: %s doubloons a point." (was: "That won't settle a single point. %s doubloons a point.") | 36 (was 58) | too few coins for a fine |
| `officer.fine.nothing_owed` | KEEP | bar | "You owe the Crown nothing." | 28 | a clean record |
| `officer.fine.cleared` | CHAT | chat | "That settles %s points. Your slate is clean. Mind yourself." (%s doubloons) | 78 | a fine paid off (the officer speaks) |
| `officer.fine.paid` | CHAT | chat | "That settles %s points. Mind yourself." (%s doubloons) | 57 | a fine paid in part |
| `officer.prisoner.no_port` | KEEP | bar | Ransoms are only paid at a navy outpost | 39 | ransom outside a navy outpost |
| `officer.prisoner.nothing` | KEEP | bar | The navy pays nothing for %s | 29 | a prisoner worth nothing |
| `officer.prisoner.led_away` | CHAT | chat | %s is led away by the navy | 27 | a prisoner taken by the navy |
| `officer.prisoner.paid` | CHAT | chat | The navy takes %s prisoner(s) off your hands and pays you %s doubloons | 72 | prisoners turned in, paid |
| `officer.prisoner.ransomed` | CHAT | chat | "The Crown thanks you." %s is ransomed for %s doubloons | 57 | a ransom paid |
| `officer.prisoner.released` | CHAT | chat | %s took you into custody. Your record is wiped and the shackles are off | 72 | you were turned in (your record is wiped) |
| `officer.proof.blank` | KEEP | bar | This proof names no one | 23 | a blank proof |
| `officer.proof.no_bounty` | KEEP | bar | There is no bounty left to claim on %s | 39 | a proof without bounty |
| `officer.proof.self` | KEEP | bar | You can't claim the bounty on yourself | 38 | your own proof |
| `officer.proof.paid` | CHAT | chat | The officer takes the proof of %s's death and pays you %s doubloons | 69 | a bounty paid |
| `notice_board.placed` | SCREEN | screen | Posted a bounty of %s doubloons on %s (%s in all) | 52 | the notice board screen |
| `notice_board.refused.below_minimum` | SCREEN | screen | A bounty must be at least %s doubloons | 39 | the notice board screen |
| `notice_board.refused.disabled` | SCREEN | screen | Players can't place bounties on this server | 43 | the notice board screen |
| `notice_board.refused.not_enough` | SCREEN | screen | You need %s doubloons and carry %s | 36 | the notice board screen |
| `notice_board.refused.self` | SCREEN | screen | You can't place a bounty on yourself | 36 | the notice board screen |
| `notice_board.refused.self_target` | SCREEN | screen | You can't place a bounty on yourself | 36 | the notice board screen |
| `notice_board.refused.unknown_target` | SCREEN | screen | Nobody called %s is online or on the board | 43 | the notice board screen |
| `notice_board.refused.closed` | KEEP | bar | The notice board is out of reach | 32 | the notice board out of reach |

### Careers, captains, navy, raids, voyages, quests

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `career.hostile` | KEEP | bar | The officer will not speak with you. | 36 | an officer who will not speak |
| `career.prize.earned` | SHORT | bar | Prize money: +%s (%s waiting) (was: Prize money: +%s doubloons (%s waiting at any navy officer)) | 31 (was 61) | a pirate killed under a letter of marque |
| `career.deserted` | CHAT | chat | Desertion! You have lost your navy rank. | 40 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.deserted.crime` | CHAT | chat | Desertion! You have lost your navy rank, and the navy wants you for it. | 71 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.disabled` | CHAT | chat | Careers are off on this server. | 31 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.already_enlisted` | CHAT | chat | You already serve in the navy | 29 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.disabled` | CHAT | chat | The navy is not recruiting (careers are off on this server) | 59 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.hostile` | CHAT | chat | The navy wants nothing to do with you | 37 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.infamous` | CHAT | chat | The navy does not take a known pirate | 37 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.low_standing` | CHAT | chat | Your navy reputation is too low (need %s) | 42 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.ok` | CHAT | chat | You may enlist | 14 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.pirate_friend` | CHAT | chat | The navy does not trust a friend of the pirates | 47 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlist.wanted` | CHAT | chat | There is a bounty on your head | 30 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.enlisted` | CHAT | chat | You have enlisted in the navy as %s. | 37 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.gift` | CHAT | chat | With your new rank you receive: %s | 35 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.gift.dropped` | CHAT | chat | Your pack is full: the rest lies at your feet. | 46 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.already_held` | CHAT | chat | You already hold a letter of marque | 35 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.blocked` | CHAT | chat | Your last letter was voided; ask again in %s days | 50 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.disabled` | CHAT | chat | The navy grants no letters of marque on this server | 51 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.enlisted` | CHAT | chat | Navy officers need no letter of marque | 38 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.granted` | CHAT | chat | You now hold a letter of marque (paid %s doubloons). Every pirate you kill earns prize money. | 94 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.infamous` | CHAT | chat | The navy grants no letter to a pirate of your infamy | 52 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.low_standing` | CHAT | chat | Your navy reputation is too low (need %s) | 42 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.ok` | CHAT | chat | You may request a letter of marque | 34 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.too_poor` | CHAT | chat | A letter of marque costs %s doubloons | 38 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.letter.voided` | CHAT | chat | Your letter of marque is void. | 30 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.not_enlisted` | CHAT | chat | You do not serve in the navy. | 29 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.officer_gone` | CHAT | chat | The officer is gone or too far away. | 36 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.prize.collected` | CHAT | chat | Prize money paid: %s doubloons. | 32 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.prize.none` | CHAT | chat | No prize money is owed to you. | 30 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.prize.ship` | CHAT | chat | Prize money under your letter of marque: %s doubloons for the pirate ship. | 75 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.promoted.infamy` | CHAT | chat | Your infamy grows: they call you %s now. | 41 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.promoted.navy` | CHAT | chat | You have been promoted to %s! | 30 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.resigned` | CHAT | chat | You have left the navy's service. | 33 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.already_redeemed` | CHAT | chat | Your ship was already delivered | 31 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.delivered` | CHAT | chat | Your ship, the %s, lies at berth %s. Powder and shot are yours to buy | 71 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.disabled` | CHAT | chat | No ships are granted on this server | 35 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.granted.navy` | CHAT | chat | The navy commissions you a ship: hand the commission to a navy officer or the harbor master's desk at a navy outpost | 116 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.granted.pirates` | CHAT | chat | The brethren grant you a ship: hand the commission in at the fence's desk on a pirate island | 92 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.lost` | CHAT | chat | The ship of this commission cannot be built any more | 52 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.no_berth` | CHAT | chat | No berth is free; come back later | 33 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.no_port` | CHAT | chat | There is no port here to deliver a ship from | 44 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.not_assembled` | CHAT | chat | Your ship lies at berth %s, but could not be assembled: use its helm | 69 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.not_yours` | CHAT | chat | This commission is made out to another captain | 46 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.rank_lost` | CHAT | chat | You no longer hold the rank this commission was granted for | 59 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.wrong_port.navy` | CHAT | chat | A navy commission is redeemed at a navy outpost | 47 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `career.ship_grant.wrong_port.pirates` | CHAT | chat | The brethren deliver their ships at a pirate island | 51 | the navy and the brethren: enlisting, letters, promotions, ship grants |
| `captain.duel.accepted` | CHAT | chat | %s accepts your challenge! His crew stand back: this is between the two of you. | 80 | a pirate captain accepts a duel |
| `captain.duel.ended.captain_died` | CHAT | chat | %s is beaten. The duel is yours! | 33 | a duel won |
| `captain.duel.ended.challenger_died` | CHAT | chat | You lost the duel with %s | 26 | a duel lost |
| `captain.duel.ended.challenger_left` | CHAT | chat | You fled the duel with %s. His crew remember you | 49 | a duel fled |
| `captain.duel.ended.timed_out` | CHAT | chat | The duel with %s is over. His crew stand back no longer | 56 | a duel ran out |
| `captain.duel.busy` | KEEP | bar | %s is already dueling someone else | 35 | a captain in another duel |
| `captain.duel.disabled` | KEEP | bar | %s won't duel anyone | 21 | duels off |
| `captain.duel.exempt` | KEEP | bar | %s won't cross swords with a spirit | 36 | a ghost challenger |
| `captain.duel.grudge` | KEEP | bar | You struck %s first. No honour, no duel | 40 | you struck first |
| `captain.duel.no_sword` | KEEP | bar | Draw a sword to challenge %s | 29 | no sword drawn |
| `captain.duel.not_sneaking` | KEEP | bar | Sneak to challenge %s | 22 | challenge without sneaking |
| `captain_hunt.chase` | CHAT | chat | Pirate captain %s has sighted the %s and gives chase! | 55 | a pirate captain gives chase (may be out of sight) |
| `captain_hunt.gave_up` | CHAT | chat | Pirate captain %s has lost the %s and breaks off the chase | 60 | he breaks off |
| `navy.chase` | CHAT | chat | A navy patrol has sighted the %s and gives chase! | 50 | a navy patrol gives chase (may be out of sight) |
| `navy.gave_up` | CHAT | chat | The navy patrol has lost the %s and breaks off the chase | 57 | it breaks off |
| `navy.resumes` | CHAT | chat | The navy patrol leaves the %s and returns to its route | 55 | it returns to its route |
| `navy.surrender` | CHAT | chat | The navy patrol sees the %s strike her colours and holds its fire | 66 | it accepts your surrender |
| `captain_voyage.label` | PART | - | captain %s aboard %s | 22 | a voyage name |
| `captain_voyage.label_without` | PART | - | %2$s without captain %1$s | 23 | a voyage name |
| `voyage.captured` | KEEP | bar | You took the %s! She is yours now | 34 | a merchant ship captured |
| `voyage.plundered` | KEEP | bar | You plundered a merchant ship | 29 | a merchant ship plundered |
| `worldsim.raid.sighted` | CHAT | chat | Sails on the horizon! Pirates are making for the settlement | 59 | raiders sighted near a settlement |
| `worldsim.raid.repelled` | CHAT | chat | The raiders are beaten off! | 27 | raid beaten off |
| `worldsim.raid.succeeded` | CHAT | chat | The raiders held the shore and sail off | 39 | raid succeeded |
| `quest.completed` | CHAT | chat | Quest complete: %s | 19 | quests (chat is for quests) |
| `quest.completed_paid` | CHAT | chat | Quest complete: %s. %s doubloons paid | 39 | quests (chat is for quests) |
| `quest.deliver_hint` | CHAT | chat | Deliver it at the destination's desk, Contracts tab | 51 | quests (chat is for quests) |
| `quest.escort_alone` | CHAT | chat | The %s made port, but you were not with her for long enough | 60 | quests (chat is for quests) |
| `quest.escort_chart_timeout` | CHAT | chat | The harbor master could not chart the route to %s in time; the escort is called off | 84 | quests (chat is for quests) |
| `quest.escort_charting` | CHAT | chat | The harbor master is charting the route; the convoy sails as soon as it is ready | 80 | quests (chat is for quests) |
| `quest.escort_hint` | CHAT | chat | The %s sets sail now. Keep within %s blocks of her until she makes port | 73 | quests (chat is for quests) |
| `quest.escort_lost` | CHAT | chat | The %s is lost | 15 | quests (chat is for quests) |
| `quest.escort_no_route` | CHAT | chat | The harbor master found no sea route to %s; the escort is called off | 69 | quests (chat is for quests) |
| `quest.failed` | CHAT | chat | Quest failed: %s | 17 | quests (chat is for quests) |
| `quest.map_given` | CHAT | chat | You received a treasure map | 27 | quests (chat is for quests) |
| `quest.progress` | CHAT | chat | Quest: %s (%s/%s) | 20 | quests (chat is for quests) |
| `quest.target_lost` | CHAT | chat | %s is gone, and not by your hand | 33 | quests (chat is for quests) |

### Charts and treasure maps

| Key | Verdict | Where | Text now (was) | Length | When |
|---|---|---|---|---|---|
| `chart.disabled` | KEEP | bar | Charts are disabled on this server | 34 | charts off |
| `chart.needs_item` | KEEP | bar | You need a chart in hand to open it | 35 | opening a chart without one |
| `chart.refused.bad_name` | SCREEN | screen | That name cannot be written on a chart | 38 | the chart screen |
| `chart.refused.disabled` | SCREEN | screen | Charts are disabled on this server | 34 | the chart screen |
| `chart.refused.name_too_long` | SCREEN | screen | That name is too long | 21 | the chart screen |
| `chart.refused.out_of_world` | SCREEN | screen | That place is beyond the edge of the world | 42 | the chart screen |
| `chart.refused.too_many` | SCREEN | screen | Your chart holds no more markers | 32 | the chart screen |
| `chart.refused.unknown_marker` | SCREEN | screen | That marker is no longer on your chart | 38 | the chart screen |
| `chart.tile.drawn` | DROP | bar | ~~You draw your chart onto the board~~ | 34 | drawing a chart onto a map tile (the board shows it) |
| `chart.tile.updated` | DROP | bar | ~~You add what you have charted to the board~~ | 42 | adding to a drawn board |
| `chart.tile.cleared` | DROP | bar | ~~You wipe the board clean~~ | 24 | wiping the board |
| `chart.tile.blank` | KEEP | bar | The board is already blank | 26 | wiping a blank board |
| `chart.tile.charts_disabled` | KEEP | bar | Charts are disabled on this server | 34 | charts off |
| `chart.tile.needs_chart` | SHORT | bar | Hold a chart to draw on the tile (was: You need a chart in hand to draw on the tile) | 32 (was 44) | drawing without a chart |
| `chart.tile.no_ink` | KEEP | bar | You do not have enough ink | 26 | no ink |
| `chart.tile.no_ink_amount` | SHORT | bar | You need %s ink (you have %s) (was: You need %s ink for this (you have %s): ink sacs, or kraken ink) | 31 (was 65) | too little ink |
| `chart.tile.no_tile` | KEEP | bar | That map tile is gone | 21 | the tile is gone |
| `chart.tile.not_rectangle` | KEEP | bar | The tiles do not form a rectangle | 33 | tiles not a rectangle |
| `chart.tile.nothing_new` | SHORT | bar | Your chart adds nothing new here (was: Your chart adds nothing new to this board) | 32 (was 41) | nothing to add |
| `chart.tile.out_of_world` | SHORT | bar | That area is beyond the world's edge (was: That area is beyond the edge of the world) | 36 (was 41) | beyond the world |
| `chart.tile.permanent` | KEEP | bar | This tile's drawing is permanent | 32 | a permanent drawing |
| `chart.tile.tiles_disabled` | SHORT | bar | Map tile drawing is off on this server (was: Drawing on map tiles is disabled on this server) | 38 (was 47) | tiles off |
| `chart.tile.too_big` | KEEP | bar | The board is too big | 20 | a board too big |
| `chart.tile.too_far` | KEEP | bar | You are too far from the map tile | 33 | too far from the tile |
| `chart.tile.uncharted` | KEEP | bar | You have charted nothing in that area | 37 | nothing charted there |
| `treasure_map.bearing` | KEEP | bar | %s, %s blocks | 15 | using a treasure map: bearing and distance |
| `treasure_map.here` | KEEP | bar | X marks the spot: dig here! | 27 | standing on the spot |
| `treasure_map.found` | KEEP | bar | The treasure has been found | 27 | the treasure is gone |
| `treasure_map.none` | KEEP | bar | No treasure within reach of this map | 36 | no treasure in reach |
| `treasure_map.disabled` | SHORT | bar | Treasure maps are off on this server (was: Treasure maps are disabled on this server) | 36 (was 41) | treasure maps off |

### Not ours

| Key | Verdict | Where | When |
|---|---|---|---|
| `sleep.not_possible` | KEEP | bar | vanilla: sleeping aboard at the wrong time |
