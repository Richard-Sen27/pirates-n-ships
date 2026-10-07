# Playtest: steering by the wheel (work package HELM1)

The server rules (deltas turn the wheel, 30° per tick at most, the lock at 1.5 turns, the rudder following the wheel
linearly, the wheel saved with the block and carried by the ship, the session ending at 2 blocks, the click steps
with `drag_steering = false`, a turned wheel turning a ship under sail) are covered by JUnit and GameTests. This
checklist covers what only the client shows: the input feel, the view lock, the turning wheel and the overlay.

Setup: `./gradlew :neoforge:runClient`, a creative world with cheats on, default config (server section `helm`,
subsection `wheel`; client section `helm_view`). Build a small ship with a helm, a square sail and some keel in the
water (or `/pirates ship place` a template) and assemble it at the helm. `/pirates wind set 0 6` gives a steady wind
from the north. Please send screenshots of steps 2, 4 and 6, a short video of step 3 if you can, and `latest.log` if
anything goes wrong.

## Steps

1. **The helm looks as before.** Look at a placed helm on land and in the hotbar.
   - **Expected:** the same helm as before HELM1: pedestal and wheel with eight spokes, the wheel upright on the
     helmsman's side. In the inventory the item shows the whole helm. No gap or double faces between the hub and the
     axle. A helm placed **before** this change shows its wheel too (at the latest after you grab it once; if a helm
     shows only the pedestal, note it).
2. **Grab the wheel.** On the assembled ship, stand in front of the wheel and hold right-click on it (empty hand or
   any item, not sneaking).
   - **Expected:** the action bar says "At the wheel (Rudder midships): move the mouse or hold strafe left/right to
     turn it, let go of use to release"; above the action bar a small dark box says **"Rudder midships"**. Nothing
     else happens while you hold the button (no block placed, no item used).
3. **Drag the mouse.** Keep holding right-click and move the mouse slowly to the right, then to the left.
   - **Expected:** the **view does not turn sideways** (looking up and down still works). The wheel turns
     **clockwise** as you see it when you move right, counter-clockwise when you move left, smoothly, following the
     mouse. The overlay counts up: "Rudder 5° starboard", "Rudder 12° starboard", ... and the other way "port". At
     the default 1° of wheel per degree the view would have turned, a quarter turn of the wheel takes about as much
     mouse movement as turning round by 90°. Tell us whether that is too slow or too fast
     (`helm.wheel.mouse_degrees_per_unit`).
4. **Lock to lock.** Keep moving the mouse right.
   - **Expected:** the wheel stops after **0.75 turns** (270°) from midships; the overlay stops at **"Rudder 35°
     starboard"**. Moving further does nothing; moving back left turns it back at once. Same on the port side. From
     hard to port to hard to starboard is 1.5 turns.
5. **The A/D keys.** Holding right-click, hold D, then A.
   - **Expected:** the wheel turns steadily (6° per tick, a full lock in about 2.3 s), D clockwise (starboard), A
     counter-clockwise (port). **You do not strafe** while steering. W and S still walk.
6. **The ship answers.** Set the sail (winch), let the ship gain way, put the wheel about two thirds to starboard
   and let go of right-click.
   - **Expected:** on release the overlay disappears and the wheel **stays where it was** (it does not spring back).
     The ship turns to **starboard** (clockwise seen from above), harder the further the wheel is turned. At rest
     (no way on) the rudder does next to nothing. `/pirates ship forces` shows the rudder angle matching the overlay.
7. **Release and re-grab.** Grab the wheel again.
   - **Expected:** the wheel starts from where you left it; the overlay shows the same angle as before. Turning back to
     midships ("Rudder midships") straightens the ship's course.
8. **Walk away.** While holding right-click, walk backwards away from the helm.
   - **Expected:** about **2 blocks** from the helm block you let go of the wheel: the overlay disappears and the view
     can turn again even though you still hold the button. Release and grab again works.
9. **View lock off.** In the server config set `helm.wheel.lock_view = false` (in single player: Mods → Pirates 'n'
   Ships → Config, or edit `serverconfig`) and grab the wheel.
   - **Expected:** the mouse now turns the view **and** the wheel together. Set it back to `true` afterwards.
10. **Old click mode.** Set `helm.wheel.drag_steering = false` and click the helm (single clicks).
    - **Expected:** spike 3's steps: clicking the right third of the wheel (as the helmsman sees it) turns the rudder
      one step to starboard (the action bar says "Rudder 1 of 3 to starboard (12°)"), the left third to port, the
      middle midships; holding right-click does not start a session and the view is free. The **wheel shows the
      step** (a third of 270° per step). Set it back to `true`: the wheel and the rudder keep that angle.
11. **Third person.** Press F5 and repeat steps 3 to 5.
    - **Expected:** the same behaviour; the wheel visibly turns on the model; the camera does not swing sideways.
12. **Overlay off.** In the client config set `helm_view.show_rudder_angle = false` and grab the wheel.
    - **Expected:** no overlay; steering works as before.
13. **Multiplayer (if you can).** A second player watches while you steer, then tries to grab the same wheel.
    - **Expected:** the watcher sees the wheel turn (a little behind you); grabbing a wheel someone else holds says
      "Someone else is at the wheel".

## Known limits to report on

- While the mouse moves fast, the view may lag by at most one frame of movement before it is held (the mouse is read
  after the world is drawn; a per-frame event would remove it). If you see the view shaking sideways while steering,
  report it with your frame rate.
- With "cinematic camera" (smooth camera) on, the view may drift slowly while steering.
