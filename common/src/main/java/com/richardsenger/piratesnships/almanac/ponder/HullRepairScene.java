package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Repair;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Patching a breach" (PND1, docs/almanac.md "Scenes"; the hull patch of ship/hull/pump, {@code HullPatchItem},
 * design.md §4.5), on the schematic {@code assets/pirates_n_ships/ponder/hull_repair.nbt}
 * ({@code art/structures/ponder/hull_repair.py}: an open hull section x 1..5, z 1..5 in the sea, the breach at
 * [5, 2, 3] open to the sea, flood water on the hold's floor row).
 *
 * <p>The patch sets the game's {@code hull_patch} block into the hole: the inflow stops at once, as in the game (there
 * is no repair time); the flood already inside stays. About 24 seconds.
 */
final class HullRepairScene {

    private HullRepairScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.HULL_REPAIR, Repair.TITLE);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(20);

        BlockPos breach = util.grid().at(5, 2, 3);
        Vec3 hole = util.vector().blockSurface(breach, Direction.WEST);
        ParticleEmitter spray = scene.effects().simpleParticleEmitter(ParticleTypes.SPLASH, util.vector().of(-0.1, 0.05, 0));
        scene.effects().emitParticles(hole, spray, 1.5f, 60);
        scene.overlay().showOutline(PonderPalette.RED, "breach", util.select().position(breach), 60);
        say(scene.overlay().showText(60), Repair.BREACH)
                .colored(PonderPalette.RED)
                .pointAt(hole)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        say(scene.overlay().showText(50), Repair.RECIPE)
                .independent(10);
        scene.idle(55);

        // the patch goes in from inside, aimed at the edge of the hole
        Vec3 edge = util.vector().blockSurface(util.grid().at(5, 2, 2), Direction.WEST);
        scene.overlay().showControls(edge.add(-0.4, 0.4, 0.3), Pointing.LEFT, 40).rightClick()
                .withItem(SceneKit.item("hull_patch"));
        say(scene.overlay().showText(60), Repair.AIM)
                .pointAt(edge)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(45);
        scene.world().setBlock(breach, SceneKit.block("hull_patch"), true);
        scene.idle(20);
        scene.overlay().showOutline(PonderPalette.GREEN, "breach", util.select().position(breach), 60);
        say(scene.overlay().showText(60), Repair.CLOSED)
                .colored(PonderPalette.GREEN)
                .pointAt(hole)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        say(scene.overlay().showText(50), Repair.WATER)
                .pointAt(util.vector().topOf(3, 2, 3))
                .placeNearTarget();
        scene.idle(55);
        say(scene.overlay().showText(55), Repair.ONLY)
                .independent(10);
        scene.idle(60);
        say(scene.overlay().showText(60), Repair.SWAP)
                .pointAt(hole)
                .placeNearTarget();
        scene.idle(60);
        scene.markAsFinished();
    }
}
