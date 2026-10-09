package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Pumps;
import com.richardsenger.piratesnships.ship.hull.pump.BilgePumpBlockEntity;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Pumps and flooding" (PND1, docs/almanac.md "Scenes"; the flooding of ship/hull and the bilge pump and hull patch of
 * ship/hull/pump, design.md §4.5), on the schematic {@code assets/pirates_n_ships/ponder/pumps_and_flooding.nbt}
 * ({@code art/structures/ponder/pumps_and_flooding.py}: an open hull section x 2..6, z 2..6 in the sea, its hold x 3..5,
 * z 3..5 below the waterline on rows y 2 and 3, the bilge pump on the hold floor at [3, 2, 3]).
 *
 * <p>The east wall's block [6, 2, 4] breaks: a breach below the waterline. The flood rises row by row (the game draws a
 * smooth flat surface at the compartment's level; Ponder shows water blocks, a row at a time), the pump's handle swings
 * through its block entity's synced pumping flag, the hull patch block closes the hole, and the hold drains. About
 * 38 seconds.
 */
final class PumpsAndFloodingScene {

    private PumpsAndFloodingScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.PUMPS_AND_FLOODING, Pumps.TITLE);
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos breach = util.grid().at(6, 2, 4);
        BlockPos pump = util.grid().at(3, 2, 3);
        Selection hold = util.select().fromTo(3, 2, 3, 5, 3, 5);
        Selection low = util.select().fromTo(3, 2, 3, 5, 2, 5).substract(util.select().position(pump));
        Selection high = util.select().fromTo(3, 3, 3, 5, 3, 5);

        scene.world().showSection(util.select().layersFrom(1).substract(util.select().position(pump)), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(pump), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, "hold", hold, 60);
        say(scene.overlay().showText(60), Pumps.DRY)
                .pointAt(util.vector().centerOf(4, 2, 5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // the breach
        scene.world().destroyBlock(breach);
        scene.idle(5);
        scene.world().setBlock(breach, Blocks.WATER.defaultBlockState(), false);
        ParticleEmitter spray = scene.effects().simpleParticleEmitter(ParticleTypes.SPLASH, util.vector().of(-0.1, 0.05, 0));
        Vec3 hole = util.vector().blockSurface(breach, Direction.WEST);
        scene.effects().emitParticles(hole, spray, 1.5f, 40);
        scene.overlay().showOutline(PonderPalette.RED, "breach", util.select().position(breach), 70);
        say(scene.overlay().showText(65), Pumps.BREACH)
                .colored(PonderPalette.RED)
                .pointAt(hole)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(35);
        flood(scene, low, pump, true);
        scene.idle(35);
        say(scene.overlay().showText(65), Pumps.RATE)
                .pointAt(hole)
                .placeNearTarget();
        scene.idle(40);
        flood(scene, high, null, true);
        scene.idle(30);
        say(scene.overlay().showText(60), Pumps.WEIGHT)
                .pointAt(util.vector().topOf(4, 3, 4))
                .placeNearTarget();
        scene.idle(65);

        // the pump against an open breach
        scene.overlay().showControls(util.vector().topOf(pump), Pointing.DOWN, 110).rightClick();
        pumping(scene, util, pump, true);
        say(scene.overlay().showText(60), Pumps.PUMP)
                .pointAt(util.vector().topOf(pump))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(50), Pumps.LEVEL)
                .colored(PonderPalette.RED)
                .pointAt(hole)
                .placeNearTarget();
        scene.idle(55);

        // the patch closes it
        scene.overlay().showControls(hole.add(-0.3, 0.3, 0), Pointing.LEFT, 30).rightClick()
                .withItem(SceneKit.item("hull_patch"));
        scene.idle(10);
        scene.world().setBlock(breach, SceneKit.block("hull_patch"), true);
        scene.overlay().showOutline(PonderPalette.GREEN, "breach", util.select().position(breach), 50);
        say(scene.overlay().showText(55), Pumps.PATCH)
                .colored(PonderPalette.GREEN)
                .pointAt(hole)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // and the hold drains
        scene.overlay().showControls(util.vector().topOf(pump), Pointing.DOWN, 60).rightClick();
        say(scene.overlay().showText(60), Pumps.DRAIN)
                .pointAt(util.vector().topOf(pump))
                .placeNearTarget();
        scene.idle(25);
        flood(scene, high, null, false);
        scene.idle(30);
        flood(scene, low, pump, false);
        scene.idle(10);
        pumping(scene, util, pump, false);
        say(scene.overlay().showText(50), Pumps.REACH)
                .pointAt(util.vector().topOf(pump))
                .placeNearTarget();
        scene.idle(55);
        scene.overlay().showControls(util.vector().topOf(pump), Pointing.DOWN, 40).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        say(scene.overlay().showText(55), Pumps.CREW)
                .pointAt(util.vector().topOf(pump))
                .placeNearTarget();
        scene.idle(55);
        scene.markAsFinished();
    }

    /** One row of the hold filled or emptied; the pump in it (if any) waterlogged with it. */
    private static void flood(SceneBuilder scene, Selection row, BlockPos pump, boolean water) {
        scene.world().setBlocks(row, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), false);
        if (pump != null) {
            scene.world().modifyBlock(pump, s -> s.hasProperty(BlockStateProperties.WATERLOGGED)
                    ? s.setValue(BlockStateProperties.WATERLOGGED, water) : s, false);
        }
    }

    /** The pump's synced pumping flag, which swings its handle ({@code PumpHandleRenderer}). */
    private static void pumping(SceneBuilder scene, SceneBuildingUtil util, BlockPos pump, boolean on) {
        scene.world().modifyBlockEntityNBT(util.select().position(pump), BilgePumpBlockEntity.class,
                tag -> tag.putBoolean("pumping", on));
    }
}
