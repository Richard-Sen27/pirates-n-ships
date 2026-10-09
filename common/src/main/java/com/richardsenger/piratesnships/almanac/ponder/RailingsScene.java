package com.richardsenger.piratesnships.almanac.ponder;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Railings;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import java.util.List;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * "Railings and bulwarks" (PND4, docs/almanac.md "Scenes"; ship/decor/railing RAIL1, RAIL3 and RAIL4, design.md §4.8),
 * on the schematic {@code assets/pirates_n_ships/ponder/railings.nbt} ({@code art/structures/ponder/railings.py}: a ship
 * at a pier, bow north; her port gunwale at x 3, y 4: the bow bulwark's corner at z 1, a railing at z 2 with a lantern
 * on its post, a gate at z 3, a railing at z 4, the railing port at z 5 with a cannon behind it, the run z 6..9 and a
 * swivel gun on z 7; the bow bulwark along z 1 with a gun port at [6, 4, 1] and a cannon behind it).
 *
 * <p>The posts are the railing's own derived states ({@code RailingRules.posts}, set here the way {@code updateShape}
 * sets them as each block arrives): an end post where a run stops, none where a port or a gate continues it, a corner
 * post when a second click rails a second edge. The gate opens through its {@code open} state, the gun port's lid
 * through its {@code open} state once a cannon stands behind it ({@code BulwarkPortBlock}: open while a cannon inboard
 * faces it). The gangway is one cell of {@code BoardingPlankBlock} from the open gate's sill to the pier (BRD4). About
 * 39 seconds.
 */
final class RailingsScene {

    static final int X = 3;
    static final BlockPos GATE = new BlockPos(X, 4, 3);
    static final BlockPos PORT = new BlockPos(X, 4, 5);
    static final BlockPos LANTERN = new BlockPos(X, 5, 2);
    static final BlockPos SWIVEL = new BlockPos(X, 5, 7);
    static final BlockPos GUN_PORT = new BlockPos(6, 4, 1);
    /** The gun at the railing port (master, rear) facing west, and the gun at the bow gun port facing north. */
    static final BlockPos PORT_GUN = new BlockPos(4, 4, 5);
    static final BlockPos BOW_GUN = new BlockPos(6, 4, 2);
    /** The run laid block by block, bow to stern. */
    static final List<BlockPos> RUN = List.of(new BlockPos(X, 4, 6), new BlockPos(X, 4, 7), new BlockPos(X, 4, 8),
            new BlockPos(X, 4, 9));
    /** The gangway's one cell, outboard of the gate, its board resting on the pier. */
    static final BlockPos GANGWAY = new BlockPos(X - 1, 4, 3);

    private RailingsScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.RAILINGS, Railings.TITLE);
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(10);

        Selection run = util.select().fromTo(X, 4, 6, X, 4, 9);
        Selection bulwark = util.select().fromTo(X, 4, 1, 8, 4, 1);
        Selection portGun = util.select().fromTo(4, 4, 5, 5, 4, 5);
        Selection bowGun = util.select().fromTo(6, 4, 2, 6, 4, 3);
        Selection later = run.copy().add(bulwark).add(portGun).add(bowGun).add(util.select().position(PORT))
                .add(util.select().position(LANTERN)).add(util.select().position(SWIVEL));
        // until the port stands at z 5, the railing at z 4 ends there: its end post
        scene.world().modifyBlock(PORT.north(), s -> post(s, RailingBlock.POST_SW, true), false);
        scene.world().showSection(util.select().layersFrom(1).substract(later), Direction.DOWN);
        scene.idle(20);

        // a run along the gunwale, its end post moving with its end
        for (int i = 0; i < RUN.size(); i++) {
            BlockPos p = RUN.get(i);
            scene.overlay().showControls(util.vector().topOf(p.below()).add(-0.3, 0, 0), Pointing.DOWN, 8).rightClick()
                    .withItem(SceneKit.item("oak_railing"));
            boolean first = i == 0;
            scene.world().modifyBlock(p, s -> post(post(s, RailingBlock.POST_NW, first), RailingBlock.POST_SW, true), false);
            if (!first) {
                scene.world().modifyBlock(RUN.get(i - 1), s -> post(s, RailingBlock.POST_SW, false), false);
            }
            scene.world().showSection(util.select().position(p), Direction.DOWN);
            scene.idle(i == 0 ? 10 : 8);
        }
        say(scene.overlay().showText(60), Railings.RUN)
                .pointAt(util.vector().of(X + 0.15, 5.05, 9.85))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(62);

        // a second click: the corner
        BlockPos corner = RUN.get(RUN.size() - 1);
        scene.overlay().showControls(util.vector().topOf(corner), Pointing.DOWN, 15).rightClick()
                .withItem(SceneKit.item("oak_railing"));
        scene.idle(10);
        scene.world().modifyBlock(corner, s -> post(post(s, BlockStateProperties.SOUTH, true), RailingBlock.POST_SE, true), false);
        say(scene.overlay().showText(55), Railings.CORNER)
                .pointAt(util.vector().of(X + 0.5, 5.0, 9.9))
                .placeNearTarget();
        scene.idle(58);

        // the railing port: a one-block opening that continues the run (no posts beside it)
        scene.overlay().showControls(util.vector().topOf(PORT), Pointing.DOWN, 12).rightClick()
                .withItem(SceneKit.item("oak_railing_port"));
        scene.world().modifyBlock(PORT.north(), s -> post(s, RailingBlock.POST_SW, false), false);
        scene.world().modifyBlock(PORT.south(), s -> post(s, RailingBlock.POST_NW, false), false);
        scene.world().showSection(util.select().position(PORT), Direction.DOWN);
        scene.idle(10);
        say(scene.overlay().showText(55), Railings.PORT)
                .pointAt(util.vector().of(X + 0.2, 4.4, 5.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(58);

        // a cannon behind it fires through
        scene.world().showSection(portGun, Direction.DOWN);
        scene.idle(12);
        Vec3 muzzle = CannonRules.muzzle(PORT_GUN.getX(), PORT_GUN.getY(), PORT_GUN.getZ(), CannonRules.muzzleDirection(-1, 0, 0));
        scene.overlay().showControls(util.vector().topOf(PORT_GUN), Pointing.DOWN, 15).rightClick();
        scene.idle(12);
        fire(scene, util, muzzle);
        say(scene.overlay().showText(55), Railings.FIRE)
                .pointAt(muzzle)
                .placeNearTarget();
        scene.idle(58);

        // the gate swings open, and the gangway goes out from its sill
        scene.overlay().showControls(util.vector().topOf(GATE), Pointing.DOWN, 12).rightClick();
        scene.idle(8);
        scene.world().modifyBlock(GATE, s -> post(s, BlockStateProperties.OPEN, true), false);
        say(scene.overlay().showText(55), Railings.GATE)
                .pointAt(util.vector().of(X + 0.2, 4.6, 3.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(58);
        scene.overlay().showControls(util.vector().topOf(GATE).add(0, -0.7, 0), Pointing.DOWN, 12).rightClick()
                .withItem(SceneKit.item("boarding_plank"));
        scene.idle(10);
        SceneKit.setLater(scene, util.select().position(GANGWAY), RailingsScene::gangway, false);
        say(scene.overlay().showText(55), Railings.PLANK)
                .pointAt(util.vector().of(GANGWAY.getX() + 0.5, 4.1, GANGWAY.getZ() + 0.5))
                .placeNearTarget();
        scene.idle(58);

        // a lantern on a post, a swivel gun on the rail
        scene.world().showSection(util.select().position(LANTERN), Direction.DOWN);
        scene.idle(5);
        say(scene.overlay().showText(55), Railings.LANTERN)
                .pointAt(util.vector().centerOf(LANTERN))
                .placeNearTarget();
        scene.idle(55);
        scene.world().showSection(util.select().position(SWIVEL), Direction.DOWN);
        scene.idle(5);
        say(scene.overlay().showText(55), Railings.SWIVEL)
                .pointAt(util.vector().of(X + 0.25, 5.6, SWIVEL.getZ() + 0.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);

        // the bulwark, and a gun port whose lid a cannon opens
        scene.world().showSection(bulwark, Direction.DOWN);
        scene.idle(10);
        say(scene.overlay().showText(55), Railings.BULWARK)
                .pointAt(util.vector().of(4.5, 4.6, 1.1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(58);
        scene.world().showSection(bowGun, Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlock(GUN_PORT, s -> post(s, BlockStateProperties.OPEN, true), false);
        say(scene.overlay().showText(60), Railings.LID)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().of(GUN_PORT.getX() + 0.5, 4.6, GUN_PORT.getZ() + 0.1))
                .placeNearTarget();
        scene.idle(62);
        scene.markAsFinished();
    }

    /** {@code s} with the boolean {@code property} set, when it has it (any other block is left as it is). */
    private static BlockState post(BlockState s, BooleanProperty property, boolean value) {
        return s.hasProperty(property) ? s.setValue(property, value) : s;
    }

    /** One plank cell laid west from the gate's sill (BRD4's straight run of one cell onto the pier). */
    static BlockState gangway() {
        return BoardingContent.PLANK.get().segment(Direction.WEST, 0);
    }

    /** The shot through the port: flash, flame and smoke at the muzzle, the ball away to the west. */
    private static void fire(SceneBuilder scene, SceneBuildingUtil util, Vec3 muzzle) {
        ParticleEmitter flash = scene.effects().simpleParticleEmitter(ParticleTypes.FLASH, Vec3.ZERO);
        ParticleEmitter flame = scene.effects().simpleParticleEmitter(ParticleTypes.FLAME, util.vector().of(-0.08, 0.02, 0));
        ParticleEmitter smoke = scene.effects().particleEmitterWithinBlockSpace(ParticleTypes.LARGE_SMOKE,
                util.vector().of(-0.03, 0.04, 0));
        ParticleEmitter puff = scene.effects().simpleParticleEmitter(ParticleTypes.POOF, util.vector().of(-0.12, 0.01, 0));
        scene.effects().emitParticles(muzzle, flash, 1, 1);
        scene.effects().emitParticles(muzzle, flame, 12, 1);
        scene.effects().emitParticles(muzzle, puff, 16, 1);
        scene.effects().emitParticles(muzzle, smoke, 6, 6);
        scene.world().createItemEntity(muzzle, util.vector().of(-0.6, 0.08, 0), SceneKit.item("cannonball"));
    }
}
