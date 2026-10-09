package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Mooring;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Mooring" (PND1, docs/almanac.md "Scenes"; the mooring ring and the grapple's tie-off of combat/grapple,
 * design.md §8.3), on the schematic {@code assets/pirates_n_ships/ponder/mooring.nbt}
 * ({@code art/structures/ponder/mooring.py}: a stone quay x 0..2 with a ring at [2, 4, 4], the sea east of it, a small
 * ship x 6..8 lying off the quay with its own ring at [6, 4, 4]).
 *
 * <p>The ship comes alongside (its section moved one block toward the quay: an element transform, not Sable) and
 * counts as moored within the port's reach ({@code crew.hiring.ship_radius}, 32 blocks: what "moored at a port"
 * means in the game; there is no separate mooring-rope mechanic). A grappling line holds her there: the hook flies
 * from the deck onto the quay (the item on a drawn arc: the game's hook entity needs its thrower), the line is a
 * Ponder line, tied off at the ship's ring. Casting off lets it go and the hook flies back. About 33 seconds.
 */
final class MooringScene {

    private MooringScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.MOORING, Mooring.TITLE);
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos quayRing = util.grid().at(2, 4, 4);
        BlockPos shipRing = util.grid().at(6, 4, 4);
        Selection ship = util.select().fromTo(6, 1, 1, 8, 4, 7);
        scene.world().showSection(util.select().layersFrom(1).substract(ship), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> shipLink = scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.idle(15);

        // coming alongside: the ship's section moves one block in toward the quay
        scene.world().moveSection(shipLink, util.vector().of(-1, 0, 0), 40);
        say(scene.overlay().showText(45), Mooring.ALONGSIDE)
                .pointAt(util.vector().topOf(6, 3, 4))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(50);

        // within the port's reach she counts as moored
        scene.overlay().showOutline(PonderPalette.GREEN, "port", util.select().fromTo(0, 1, 0, 2, 3, 8), 70);
        SceneKit.arrow(scene, PonderPalette.GREEN, util.vector().of(2.0, 4.6, 7.5), util.vector().of(5.0, 4.6, 7.5), 70);
        say(scene.overlay().showText(70), Mooring.PORT)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().of(3.5, 4.6, 7.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);

        Vec3 ring = util.vector().of(5.5, 4.15, 4.5);
        BlockPos movedRing = shipRing.west();
        scene.overlay().showOutline(PonderPalette.WHITE, "rings", util.select().position(quayRing), 60);
        say(scene.overlay().showText(60), Mooring.RINGS)
                .pointAt(ring)
                .placeNearTarget();
        scene.idle(65);

        // a grappling line: the hook flies from the deck onto the quay
        Vec3 deck = util.vector().of(6.5, 5.4, 4.5);
        Vec3 bite = util.vector().of(1.4, 4.05, 4.5);
        scene.overlay().showControls(util.vector().topOf(6, 3, 5), Pointing.DOWN, 25).rightClick()
                .withItem(SceneKit.item("grappling_hook"));
        scene.idle(10);
        AtomicReference<ItemEntity> hook = new AtomicReference<>();
        scene.world().createEntity(level -> {
            ItemEntity item = new ItemEntity(level, deck.x, deck.y, deck.z, SceneKit.item("grappling_hook"));
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
            hook.set(item);
            return item;
        });
        fly(scene, hook, deck, bite, 2.0, 16);
        scene.overlay().showLine(PonderPalette.OUTPUT, deck, bite, 70);
        say(scene.overlay().showText(60), Mooring.THROW)
                .pointAt(bite)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // tied off at the ship's ring
        scene.overlay().showControls(util.vector().topOf(movedRing), Pointing.DOWN, 25).rightClick();
        scene.idle(10);
        scene.overlay().showLine(PonderPalette.OUTPUT, ring, bite, 212);
        say(scene.overlay().showText(60), Mooring.TIE)
                .pointAt(ring)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(60), Mooring.HOLD)
                .pointAt(ring)
                .placeNearTarget();
        scene.idle(65);
        say(scene.overlay().showText(70), Mooring.ASHORE)
                .pointAt(util.vector().centerOf(quayRing))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);

        Vec3 moored = ring;
        // cast off
        scene.overlay().showControls(moored.add(0, 0.3, 0), Pointing.DOWN, 30).rightClick().whileSneaking();
        scene.idle(10);
        fly(scene, hook, bite, moored.add(0, 0.6, 0), 1.5, 14);
        say(scene.overlay().showText(60), Mooring.CAST_OFF)
                .pointAt(moored)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(16);
        SceneKit.everyTick(scene, 1, (s, t, total) -> {
            ItemEntity item = hook.get();
            if (item != null) {
                item.discard();
            }
        });
        scene.idle(50);
        say(scene.overlay().showText(50), Mooring.CLEATS)
                .independent(10);
        scene.idle(55);
        scene.markAsFinished();
    }

    /** Moves the hook along a parabola from {@code from} to {@code to}, {@code height} above the straight line. */
    private static void fly(SceneBuilder scene, AtomicReference<ItemEntity> hook, Vec3 from, Vec3 to, double height, int ticks) {
        SceneKit.everyTick(scene, ticks, (s, t, total) -> {
            ItemEntity item = hook.get();
            if (item == null) {
                return;
            }
            double f = (t + 1) / (double) total;
            Vec3 p = SceneKit.lerp(from, to, f).add(0, height * 4 * f * (1 - f), 0);
            item.setPos(p.x, p.y, p.z);
            item.setDeltaMovement(Vec3.ZERO);
        });
    }
}
