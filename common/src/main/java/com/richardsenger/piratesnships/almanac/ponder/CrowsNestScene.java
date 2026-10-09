package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Nest;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFooting;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.lookout.CrowsNestBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "The crow's nest" (PND2, docs/almanac.md "Scenes"; station/lookout and the ratlines of ship/rigging, design.md §6),
 * on the schematic {@code assets/pirates_n_ships/ponder/crows_nest.nbt} ({@code art/structures/ponder/crows_nest.py}: a
 * deck with the bow north, a mast at x 3, z 3 up to y 6, the nest at [3, 7, 3] and ratlines from the deck to its rim).
 *
 * <p>The lookout stands where the station seats him ({@link CrowsNestBlock#seatSpot}: inside the nest's own block, on
 * its floor). He is put there at once: crew walk on decks but plan no climbs, so the nest is reached by the walk's
 * fallback. The call's bearing is {@link #CALL_POINTS} points off the starboard bow, drawn at that angle from the bow
 * (the bearing rule of {@code Bearings}); the scan circle stands for the 160-block range (not to scale). Last, a
 * flagpole stands in the nest (FLG3: the nest's {@code pole} state turns on, a merchant flag flies through the pole's
 * real state, {@link SceneFlags}). About 35 seconds.
 */
final class CrowsNestScene {

    static final BlockPos NEST = new BlockPos(3, 7, 3);
    /** FLG3: the flagpole standing in the nest (one block: its head). */
    static final BlockPos POLE = new BlockPos(3, 8, 3);
    /** The example call: a ship two points off the starboard bow, 140 blocks off, with the bow north. */
    static final int CALL_POINTS = 2;
    static final double CALL_DISTANCE = 140.0;

    private CrowsNestScene() {
    }

    /** The unit offset (dx, dz) of a ship {@link #CALL_POINTS} points to starboard of a bow pointing north. */
    static Vec3 callDirection() {
        double a = Math.toRadians(CALL_POINTS * 11.25);
        return new Vec3(Math.sin(a), 0, -Math.cos(a));
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.CROWS_NEST, Nest.TITLE);
        scene.configureBasePlate(0, 0, 7);
        // the mast is nine blocks tall: a smaller, lower view keeps the nest and its texts on the screen (PND3)
        scene.scaleSceneView(0.8f);
        scene.setSceneOffsetY(-3f);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 6, 3), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(NEST), Direction.DOWN);
        scene.idle(10);
        Vec3 nestTop = util.vector().of(3.5, 8.25, 3.5);
        say(scene.overlay().showText(60), Nest.NEST)
                .pointAt(nestTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // ratlines from the deck to the rim, on the mast's port side (west), the side the watcher sees
        scene.world().showSection(util.select().fromTo(0, 1, 3, 2, 7, 3), Direction.WEST);
        scene.idle(10);
        SceneKit.path(scene, PonderPalette.WHITE, List.of(util.vector().of(0.1, 1.2, 3.5), util.vector().of(2.4, 4.0, 3.5),
                util.vector().of(2.4, 7.9, 3.5)), 60);
        SceneKit.arrow(scene, PonderPalette.WHITE, util.vector().of(2.4, 7.9, 3.5), util.vector().of(3.1, 8.4, 3.5), 60);
        say(scene.overlay().showText(60), Nest.RATLINES)
                .pointAt(util.vector().of(2.4, 4.0, 3.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        scene.overlay().showOutline(PonderPalette.GREEN, "nest", util.select().position(NEST), 55);
        say(scene.overlay().showText(55), Nest.RIM)
                .colored(PonderPalette.GREEN)
                .pointAt(nestTop)
                .placeNearTarget();
        scene.idle(60);

        // posting a lookout
        Vec3 deckSpot = util.vector().of(1.5, 1.0, 5.5);
        Vec3 post = util.vector().of(3.5, NEST.getY() + CrowsNestBlock.FLOOR_HEIGHT / 16.0, 3.5);
        AtomicReference<CrewMember> lookout = new AtomicReference<>();
        scene.world().createEntity(level -> {
            CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
            SceneKit.place(c, deckSpot, SceneKit.yaw(1, -1));
            lookout.set(c);
            return c;
        });
        scene.idle(10);
        scene.overlay().showControls(deckSpot.add(0, 2.2, 0), Pointing.DOWN, 20).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        scene.idle(22);
        scene.overlay().showControls(nestTop, Pointing.DOWN, 20).rightClick().withItem(SceneKit.item("captains_whistle"));
        scene.idle(12);
        ParticleEmitter poof = scene.effects().simpleParticleEmitter(ParticleTypes.POOF, Vec3.ZERO);
        scene.effects().emitParticles(deckSpot.add(0, 1, 0), poof, 6, 1);
        SceneKit.once(scene, s -> {
            if (lookout.get() != null) {
                SceneKit.place(lookout.get(), post, 180f);
            }
        });
        scene.effects().emitParticles(post.add(0, 1.2, 0), poof, 6, 1);
        say(scene.overlay().showText(60), Nest.POST)
                .pointAt(post.add(0, 1.8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // the watch
        Vec3 eye = util.vector().of(3.5, 8.9, 3.5);
        SceneKit.path(scene, PonderPalette.GREEN, SceneKit.arc(eye, 3.2, 0, 360, 32), 70);
        say(scene.overlay().showText(65), Nest.SCAN)
                .colored(PonderPalette.GREEN)
                .pointAt(eye.add(3.2, 0, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // the call: two points off the starboard bow
        Vec3 dir = callDirection();
        Vec3 tip = eye.add(dir.scale(3.4));
        scene.overlay().showLine(PonderPalette.WHITE, eye, eye.add(0, 0, -3.4), 80);
        SceneKit.arrow(scene, PonderPalette.OUTPUT, eye, tip, 80);
        scene.overlay().showControls(tip.add(0, 0.4, 0), Pointing.DOWN, 80).withItem(SceneKit.item("merchant_flag"));
        say(scene.overlay().showText(80), Nest.CALL)
                .pointAt(tip)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);
        say(scene.overlay().showText(65), Nest.WHAT)
                .pointAt(eye)
                .placeNearTarget();
        scene.idle(70);

        // a player keeps the watch as well
        SceneKit.discard(scene, lookout);
        scene.effects().emitParticles(post.add(0, 1.2, 0), poof, 6, 1);
        scene.overlay().showOutline(PonderPalette.WHITE, "nest", util.select().position(NEST), 55);
        say(scene.overlay().showText(55), Nest.SELF)
                .pointAt(nestTop)
                .placeNearTarget();
        scene.idle(60);

        // FLG3: a flagpole in the nest, the nest its footing
        scene.world().modifyBlock(NEST, s -> s.hasProperty(FlagpoleFooting.POLE) ? s.setValue(FlagpoleFooting.POLE, true) : s, false);
        scene.world().showSection(util.select().position(POLE), Direction.DOWN);
        SceneFlags.fly(scene, POLE, FlagKind.MERCHANT);
        scene.idle(10);
        say(scene.overlay().showText(60), Nest.POLE)
                .pointAt(util.vector().topOf(POLE))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        scene.markAsFinished();
    }
}
