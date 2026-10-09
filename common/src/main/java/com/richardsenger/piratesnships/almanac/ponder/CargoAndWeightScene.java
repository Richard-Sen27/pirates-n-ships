package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Cargo;
import com.richardsenger.piratesnships.trade.cargo.CargoLoad;
import com.richardsenger.piratesnships.trade.cargo.CargoMass;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Cargo and weight" (PND1, docs/almanac.md "Scenes"; trade/cargo {@code CargoMass}/{@code CargoLoad} and the
 * assembly ballast of sailing/ballast, design.md §4.9 and §5.2 TRM1), on the schematic
 * {@code assets/pirates_n_ships/ponder/cargo_and_weight.nbt} ({@code art/structures/ponder/cargo_and_weight.py}: a ship
 * x 2..6, z 1..9 floating with the bow north, crate places aft at [3, 5, 8] and [5, 5, 8] and amidships at [3, 5, 5]
 * and [5, 5, 5]).
 *
 * <p>The crates fill through their real {@link CargoLoad#LOAD} states (0 to {@link CargoMass#MAX_LOAD}); the ship's trim
 * and draft are Ponder element transforms of its section (a few degrees and a fraction of a block, to be seen, not
 * measured). The game's ballast counts containers empty and is worked out again when blocks are added (PND4: the
 * empty crates placed aft leave her level), so the cargo in them trims her and the scene levels the ship by restowing
 * the cargo amidships, not by ballast. About 36 seconds.
 */
final class CargoAndWeightScene {

    /** The stern-down trim the aft cargo gives the ship in the scene [degrees] and how much lower it sits [blocks]. */
    static final double TRIM_DEGREES = 4.0;
    static final double SINK = 0.25;

    private CargoAndWeightScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.CARGO_AND_WEIGHT, Cargo.TITLE);
        scene.configureBasePlate(0, 0, 9);
        // the deck stands four blocks above the seabed: a lower view keeps the crates and their clicks on the screen (PND3)
        scene.setSceneOffsetY(-1.5f);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos[] aft = {util.grid().at(3, 5, 8), util.grid().at(5, 5, 8)};
        BlockPos[] midships = {util.grid().at(3, 5, 5), util.grid().at(5, 5, 5)};
        Selection crates = util.select().position(aft[0]).add(util.select().position(aft[1]))
                .add(util.select().position(midships[0])).add(util.select().position(midships[1]));
        Selection ship = util.select().fromTo(2, 2, 1, 6, 5, 9).substract(crates);
        scene.world().showSection(util.select().layersFrom(1).substract(ship).substract(crates), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> shipLink = scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.world().configureCenterOfRotation(shipLink, util.vector().of(4.5, 4.0, 5.0));
        scene.idle(20);

        Vec3 deck = util.vector().topOf(4, 4, 5);
        say(scene.overlay().showText(65), Cargo.BALLAST)
                .pointAt(deck)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // crates aft, filling
        scene.overlay().showControls(util.vector().topOf(4, 4, 8), Pointing.DOWN, 30).rightClick()
                .withItem(SceneKit.item("cargo_crate"));
        scene.idle(10);
        for (BlockPos crate : aft) {
            scene.world().glueBlockOnto(crate, Direction.DOWN, shipLink);
        }
        scene.idle(15);
        // TRM1: new blocks have the ballast worked out again, containers counted empty, so the empty crates leave her level
        SceneKit.focus(scene, util.vector().of(4.5, 5.5, 8.5));
        say(scene.overlay().showText(60), Cargo.TRIM)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(aft[1]))
                .placeNearTarget();
        scene.idle(65);
        say(scene.overlay().showText(60), Cargo.CRATES)
                .pointAt(util.vector().topOf(aft[0]))
                .placeNearTarget()
                .attachKeyFrame();
        for (int load = 1; load <= CargoMass.MAX_LOAD; load++) {
            setLoad(scene, aft, load);
            scene.idle(12);
        }
        scene.idle(17);
        say(scene.overlay().showText(55), Cargo.FULL)
                .pointAt(util.vector().topOf(aft[0]))
                .placeNearTarget();
        scene.idle(60);

        // trimmed by the stern, lower in the water
        scene.world().rotateSection(shipLink, TRIM_DEGREES, 0, 0, 40);
        scene.world().moveSection(shipLink, util.vector().of(0, -SINK, 0), 40);
        scene.idle(20);
        say(scene.overlay().showText(65), Cargo.AFT)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(4, 4, 9))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);
        say(scene.overlay().showText(60), Cargo.NOT_BALLAST)
                .pointAt(deck)
                .placeNearTarget();
        scene.idle(65);

        // restowed amidships: level again, still deeper
        setLoad(scene, midships, CargoMass.MAX_LOAD);
        for (BlockPos crate : aft) {
            scene.world().setBlock(crate, Blocks.AIR.defaultBlockState(), false);
        }
        for (BlockPos crate : midships) {
            scene.world().glueBlockOnto(crate, Direction.DOWN, shipLink);
        }
        scene.world().rotateSection(shipLink, -TRIM_DEGREES, 0, 0, 30);
        scene.idle(35);
        say(scene.overlay().showText(60), Cargo.LEVEL)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(midships[1]))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // slower to gather way and to turn
        Vec3 bow = util.vector().of(4.5, 4.6, 0.6);
        SceneKit.arrow(scene, PonderPalette.WHITE, bow, bow.add(0, 0, -2.4), 70);
        SceneKit.arrow(scene, PonderPalette.RED, bow.add(0, -0.4, 0), bow.add(0, -0.4, -1.3), 70);
        say(scene.overlay().showText(65), Cargo.SPEED)
                .pointAt(bow.add(0, 0, -1.2))
                .placeNearTarget();
        scene.idle(70);
        say(scene.overlay().showText(55), Cargo.LEVELS)
                .independent(10);
        scene.idle(55);
        scene.markAsFinished();
    }

    /** The crates' real fill state ({@link CargoLoad#LOAD}), which is what the game's ship mass reads. */
    private static void setLoad(SceneBuilder scene, BlockPos[] crates, int load) {
        for (BlockPos crate : crates) {
            scene.world().modifyBlock(crate, s -> s.hasProperty(CargoLoad.LOAD) ? s.setValue(CargoLoad.LOAD, load) : s, false);
        }
    }
}
