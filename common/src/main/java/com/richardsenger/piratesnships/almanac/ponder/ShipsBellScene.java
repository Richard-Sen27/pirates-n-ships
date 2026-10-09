package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Bell;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "The ship's bell" (PND2, docs/almanac.md "Scenes"; ship/decor {@code ShipsBellBlock} and BELL1's swing, the raid
 * alarm of worldsim/raid, design.md §4.8), on the schematic {@code assets/pirates_n_ships/ponder/ships_bell.nbt}
 * ({@code art/structures/ponder/ships_bell.py}: a bell on its wall bracket at [3, 2, 3] on the bulkhead along the south
 * edge, the far side from the default camera (PND3), and one on a post at [1, 2, 1]).
 *
 * <p>Each ring is what {@code ShipsBellBlock#ring} syncs: the ring's start and the strike direction (from the striker
 * toward the bell) in the bell's block entity, from which the game's bell renderer swings the bell and its clapper.
 * Ponder pauses the game in single player, so the start is re-timed every scene tick ({@link #ring}). The sound is not
 * played. Short on purpose: the bell has no other gameplay. About 22 seconds.
 */
final class ShipsBellScene {

    static final BlockPos WALL_BELL = new BlockPos(3, 2, 3);
    static final BlockPos POST_BELL = new BlockPos(1, 2, 1);
    /** Default {@code ship_decor.bell_ring_ticks}: the swing's length. */
    static final int RING_TICKS = 20;

    private ShipsBellScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.SHIPS_BELL, Bell.TITLE);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.WHITE, "wall", util.select().position(WALL_BELL), 60);
        scene.overlay().showOutline(PonderPalette.WHITE, "post", util.select().fromTo(1, 1, 1, 1, 2, 1), 60);
        say(scene.overlay().showText(60), Bell.MOUNT)
                .pointAt(util.vector().centerOf(POST_BELL))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // rung from the front: it swings away from the striker
        scene.overlay().showControls(util.vector().blockSurface(WALL_BELL, Direction.NORTH), Pointing.UP, 25).rightClick();
        scene.idle(6);
        ring(scene, WALL_BELL, Direction.SOUTH);
        say(scene.overlay().showText(60), Bell.RING)
                .pointAt(util.vector().centerOf(WALL_BELL))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // from the side: half the swing
        scene.overlay().showControls(util.vector().blockSurface(POST_BELL, Direction.WEST), Pointing.RIGHT, 25).rightClick();
        scene.idle(6);
        ring(scene, POST_BELL, Direction.EAST);
        say(scene.overlay().showText(55), Bell.SIDE)
                .pointAt(util.vector().centerOf(POST_BELL))
                .placeNearTarget();
        scene.idle(60);

        say(scene.overlay().showText(65), Bell.MEANING)
                .independent(10);
        scene.idle(70);

        // the raid alarm: every bell rings, again and again
        for (int i = 0; i < 2; i++) {
            ring(scene, WALL_BELL, null);
            ring(scene, POST_BELL, null);
            scene.idle(30);
        }
        say(scene.overlay().showText(60), Bell.ALARM)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().centerOf(WALL_BELL))
                .placeNearTarget()
                .attachKeyFrame();
        ring(scene, WALL_BELL, null);
        ring(scene, POST_BELL, null);
        scene.idle(65);

        say(scene.overlay().showText(55), Bell.RECIPE)
                .pointAt(util.vector().centerOf(POST_BELL))
                .placeNearTarget();
        scene.idle(60);
        scene.markAsFinished();
    }

    /**
     * One ring of the bell at {@code bell}, struck toward {@code push} (null: the raid alarm, as if from the front):
     * the start the bell's block entity syncs, re-timed each tick so it is {@code t} ticks old, then left at rest.
     */
    private static void ring(SceneBuilder scene, BlockPos bell, Direction push) {
        SceneKit.everyTick(scene, RING_TICKS + 1, (s, t, total) -> SceneKit.editBlockEntity(s, bell, tag -> {
            String start = SceneSync.key(SceneSync.BELL_RING_START, "ring_start");
            String strike = SceneSync.key(SceneSync.BELL_STRIKE, "strike");
            long now = s.getWorld().getGameTime();
            // the last tick puts the ring well past its swing: the bell rests
            tag.putLong(start, t < total - 1 ? now - t : now - 4L * RING_TICKS);
            if (push != null) {
                tag.putByte(strike, (byte) push.get3DDataValue());
            } else {
                tag.remove(strike);
            }
        }));
    }
}
