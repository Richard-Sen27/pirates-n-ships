package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Assembly;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Assembling a ship" (PND1, docs/almanac.md "Scenes"; ship/assembly, design.md §4.1), on the schematic
 * {@code assets/pirates_n_ships/ponder/ship_assembly.nbt} ({@code art/structures/ponder/ship_assembly.py}: a box hull
 * x 2..6, z 1..7 floating in the sea on y 1..2, its deck on y 3, the helm at [4, 4, 6] facing south, a cargo barrel to
 * place aboard at [4, 4, 3]).
 *
 * <p>The hull fades in, the helm is placed, and using it turns the hull into one section that rolls on the water: the
 * scene moves it with Ponder's element transforms, not with Sable. The barrel placed afterwards is glued onto that
 * section; the ship drifts a few degrees off the grid and disassembly turns her back to the nearest quarter. About
 * 34 seconds.
 */
final class ShipAssemblyScene {

    private ShipAssemblyScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.SHIP_ASSEMBLY, Assembly.TITLE);
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos helm = util.grid().at(4, 4, 6);
        BlockPos barrel = util.grid().at(4, 4, 3);
        Selection hullBox = util.select().fromTo(2, 1, 1, 6, 4, 7);
        Selection sea = util.select().layersFrom(1).substract(hullBox);
        Selection hull = hullBox.copy().substract(util.select().position(helm)).substract(util.select().position(barrel));
        Selection ship = hullBox.copy().substract(util.select().position(barrel));
        Vec3 deck = util.vector().topOf(4, 3, 4);

        scene.world().showSection(sea, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(hull, Direction.DOWN);
        scene.idle(20);

        say(scene.overlay().showOutlineWithText(hull, 70), Assembly.BUILD)
                .colored(PonderPalette.WHITE)
                .pointAt(deck)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        say(scene.overlay().showText(70), Assembly.GAP)
                .pointAt(util.vector().topOf(1, 2, 4))
                .placeNearTarget();
        scene.idle(70);

        // the helm
        scene.overlay().showControls(util.vector().topOf(4, 3, 6), Pointing.DOWN, 40).rightClick()
                .withItem(SceneKit.item("helm"));
        scene.idle(10);
        scene.world().showSection(util.select().position(helm), Direction.DOWN);
        scene.idle(15);
        SceneKit.arrow(scene, PonderPalette.WHITE, util.vector().of(4.5, 5.4, 7.2), util.vector().of(4.5, 5.4, 3.6), 70);
        say(scene.overlay().showText(70), Assembly.HELM)
                .pointAt(util.vector().centerOf(helm))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // assembly: the connected blocks become one ship
        scene.overlay().showControls(util.vector().topOf(helm), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        ElementLink<WorldSectionElement> shipLink = scene.world().makeSectionIndependent(ship);
        scene.world().configureCenterOfRotation(shipLink, util.vector().of(4.5, 3.0, 4.5));
        scene.overlay().showOutline(PonderPalette.GREEN, "ship", ship, 70);
        say(scene.overlay().showText(70), Assembly.USE)
                .colored(PonderPalette.GREEN)
                .pointAt(deck)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        say(scene.overlay().showText(60), Assembly.TERRAIN)
                .pointAt(util.vector().topOf(1, 0, 8))
                .placeNearTarget();
        scene.idle(60);

        // afloat: she rolls a little with the sea
        say(scene.overlay().showText(70), Assembly.AFLOAT)
                .pointAt(deck)
                .placeNearTarget();
        scene.world().rotateSection(shipLink, 0, 0, 2.5, 18);
        scene.idle(18);
        scene.world().rotateSection(shipLink, 0, 0, -5, 30);
        scene.idle(30);
        scene.world().rotateSection(shipLink, 0, 0, 2.5, 18);
        scene.idle(24);

        // a block placed aboard joins her
        scene.overlay().showControls(util.vector().topOf(4, 3, 3), Pointing.DOWN, 30).rightClick()
                .withItem(SceneKit.item("cargo_barrel"));
        scene.idle(10);
        scene.world().glueBlockOnto(barrel, Direction.DOWN, shipLink);
        scene.idle(15);
        say(scene.overlay().showText(60), Assembly.ABOARD)
                .pointAt(util.vector().centerOf(barrel))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // she drifts a little off the block grid
        scene.world().rotateSection(shipLink, 0, 7, 0, 40);
        scene.idle(40);

        // disassembly at the helm
        scene.overlay().showControls(util.vector().topOf(helm), Pointing.DOWN, 40).rightClick().whileSneaking();
        scene.idle(10);
        say(scene.overlay().showText(80), Assembly.DISASSEMBLE)
                .pointAt(util.vector().centerOf(helm))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.world().rotateSection(shipLink, 0, -7, 0, 20);
        scene.idle(25);
        scene.overlay().showOutline(PonderPalette.WHITE, "ship", ship, 70);
        say(scene.overlay().showText(70), Assembly.SNAP)
                .pointAt(deck)
                .placeNearTarget();
        scene.idle(70);
        scene.markAsFinished();
    }
}
