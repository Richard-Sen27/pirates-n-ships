package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Anchor;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.sailing.anchor.AnchorContent;
import com.richardsenger.piratesnships.sailing.anchor.AnchorEntity;
import com.richardsenger.piratesnships.sailing.anchor.AnchorTravel;
import com.richardsenger.piratesnships.sailing.anchor.HullSide;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.capstan.CapstanBlockEntity;
import com.richardsenger.piratesnships.station.capstan.CapstanTurning;
import com.richardsenger.piratesnships.station.capstan.CapstanWalk;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "The anchor and the capstan" (PND1, docs/almanac.md "Scenes"; station/capstan and sailing/anchor, design.md §5.3
 * and §6 CAP2), on the schematic {@code assets/pirates_n_ships/ponder/anchor_and_capstan.nbt}
 * ({@code art/structures/ponder/anchor_and_capstan.py}: a ship's waist x 1..5, z 1..7 floating in a sea four blocks
 * deep, its deck on y 5, the capstan at [3, 6, 4]).
 *
 * <p>The anchor is the game's {@link AnchorEntity}, drawn stowed by its own renderer, hanging where {@link HullSide}
 * puts the hawse for this hull ({@link #HAWSE}); it falls at the default sink speed (4 blocks/s) and comes up at the
 * default raise speed (2.5 blocks/s). The chain is shown as chain blocks in the column below the hawse (the game draws
 * it as a catenary from the hawse, which needs a ship). The capstan steps through its real {@link CapstanBlock#ANCHOR}
 * phases; while the crew member works it, its block entity gets the synced turning and rate its bar renderer reads,
 * and the crew member walks round the drum by the real {@link CapstanWalk}. About 38 seconds.
 */
final class AnchorAndCapstanScene {

    static final BlockPos CAPSTAN = new BlockPos(3, 6, 4);
    /** The hull's blocks, as the anchor rule sees the ship (x 1..5, y 3..5, z 1..7 of the schematic). */
    static final int X0 = 1, X1 = 5, Y0 = 3, Y1 = 5, Z0 = 1, Z1 = 7;
    /** Default {@code crew_stations.capstan.turn_degrees_per_tick} and {@code walk_radius}. */
    static final double TURN_RATE = 6.0;
    static final double WALK_RADIUS = 1.1;
    /** Default {@code anchor.sink_speed} and {@code raise_speed} [blocks per second]. */
    static final double SINK_SPEED = 4.0;
    static final double RAISE_SPEED = 2.5;
    /** The seabed's top. */
    static final double SEABED = 1.0;
    /** Where the stowed anchor's ring hangs: the hawse of {@link HullSide} for this hull and a bow to the north. */
    static final Vec3 HAWSE = hawse();

    private AnchorAndCapstanScene() {
    }

    /** {@link HullSide#find} for the scene's hull, bow north (as the game does it from the capstan, AnchorEntities). */
    static Vec3 hawse() {
        HullSide.Placement p = HullSide.find(0, -1, 8, (dx, dy, dz) -> {
            int x = CAPSTAN.getX() + dx, y = CAPSTAN.getY() + dy, z = CAPSTAN.getZ() + dz;
            return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1;
        });
        double[] o = p.hawseOffset();
        return new Vec3(CAPSTAN.getX() + o[0], CAPSTAN.getY() + o[1], CAPSTAN.getZ() + o[2]);
    }

    /** Ticks for the anchor's crown to travel between stowed and the seabed at {@code blocksPerSecond}. */
    static int travelTicks(double blocksPerSecond) {
        return (int) Math.round(AnchorTravel.distance(HAWSE.y, SEABED) / blocksPerSecond * 20.0);
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.ANCHOR_AND_CAPSTAN, Anchor.TITLE);
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos capstan = util.grid().at(CAPSTAN.getX(), CAPSTAN.getY(), CAPSTAN.getZ());
        Vec3 stowed = HAWSE.subtract(0, AnchorTravel.HEIGHT, 0);
        Vec3 resting = new Vec3(HAWSE.x, SEABED, HAWSE.z);
        int fall = travelTicks(SINK_SPEED);
        int heave = travelTicks(RAISE_SPEED);

        Selection world = util.select().layersFrom(1);
        scene.world().showSection(world.copy().substract(util.select().position(capstan)), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(capstan), Direction.DOWN);
        AtomicReference<AnchorEntity> anchor = new AtomicReference<>();
        scene.world().createEntity(level -> {
            AnchorEntity a = new AnchorEntity(AnchorContent.ANCHOR.get(), level);
            SceneKit.place(a, stowed, 0);
            anchor.set(a);
            return a;
        });
        scene.idle(15);
        say(scene.overlay().showText(70), Anchor.CAPSTAN)
                .pointAt(HAWSE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);

        // let go
        scene.overlay().showControls(util.vector().topOf(capstan), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        letGo(scene, util, capstan, anchor, stowed, resting, fall);
        say(scene.overlay().showText(60), Anchor.DROP)
                .pointAt(util.vector().of(HAWSE.x, 3.5, HAWSE.z))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(60), Anchor.LANDS)
                .pointAt(resting)
                .placeNearTarget();
        scene.idle(65);

        // holding: the wind pushes, the chain holds
        SceneKit.arrow(scene, PonderPalette.WHITE, util.vector().of(3.5, 7.5, 0.0), util.vector().of(3.5, 7.5, 2.0), 70);
        scene.overlay().showBigLine(PonderPalette.GREEN, resting.add(0, 2, 0), HAWSE, 70);
        say(scene.overlay().showText(70), Anchor.HOLD)
                .colored(PonderPalette.GREEN)
                .pointAt(HAWSE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);
        say(scene.overlay().showText(60), Anchor.DRAG)
                .pointAt(resting)
                .placeNearTarget();
        scene.idle(65);
        say(scene.overlay().showText(55), Anchor.DEEP)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(capstan))
                .placeNearTarget();
        scene.idle(60);

        // weigh anchor
        scene.overlay().showControls(util.vector().topOf(capstan), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        weigh(scene, util, capstan, anchor, stowed, resting, heave);
        say(scene.overlay().showText(55), Anchor.RAISE)
                .pointAt(HAWSE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);

        // a crew member works the capstan, walking round the drum
        Vec3 spot = new Vec3(capstan.getX() + 0.5, capstan.getY(), capstan.getZ() + 1.5);
        AtomicReference<CrewMember> sailor = new AtomicReference<>();
        scene.world().createEntity(level -> {
            CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
            SceneKit.place(c, spot, SceneKit.yaw(0, -1));
            sailor.set(c);
            return c;
        });
        scene.idle(10);
        scene.overlay().showControls(spot.add(0, 2.2, 0), Pointing.DOWN, 20).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(capstan), Pointing.DOWN, 20).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        say(scene.overlay().showText(60), Anchor.CREW)
                .pointAt(spot.add(0, 1.8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(25);

        // "Drop anchor": two seconds at the bars, then the anchor runs out; "Weigh anchor": he heaves it in
        int lean = 40;
        int pause = 30;
        int walkTicks = lean + fall + pause + heave + 40;
        walk(scene, capstan, sailor, spot, walkTicks, t -> {
            if (t < lean + fall) return CapstanTurning.DROPPING;
            if (t < lean + fall + pause) return CapstanTurning.NONE;
            if (t < lean + fall + pause + heave) return CapstanTurning.RAISING;
            return CapstanTurning.NONE;
        });
        bars(scene, capstan, CapstanTurning.DROPPING);
        say(scene.overlay().showText(lean + fall + 20), Anchor.WALK)
                .pointAt(util.vector().topOf(capstan))
                .placeNearTarget();
        scene.idle(lean);
        letGo(scene, util, capstan, anchor, stowed, resting, fall);
        bars(scene, capstan, CapstanTurning.NONE);
        scene.idle(pause);
        bars(scene, capstan, CapstanTurning.RAISING);
        weigh(scene, util, capstan, anchor, stowed, resting, heave);
        bars(scene, capstan, CapstanTurning.NONE);
        scene.idle(20);
        say(scene.overlay().showText(50), Anchor.STOWED)
                .pointAt(HAWSE)
                .placeNearTarget();
        scene.idle(50);
        scene.markAsFinished();
    }

    /** The anchor runs out: it falls from the hawse to the seabed in {@code ticks}, the chain following it. */
    private static void letGo(SceneBuilder scene, SceneBuildingUtil util, BlockPos capstan, AtomicReference<AnchorEntity> anchor,
                              Vec3 stowed, Vec3 resting, int ticks) {
        phase(scene, capstan, CapstanBlock.Phase.DROPPING);
        moveAnchor(scene, anchor, stowed, resting, ticks);
        ParticleEmitter splash = scene.effects().simpleParticleEmitter(ParticleTypes.SPLASH, util.vector().of(0, 0.1, 0));
        scene.effects().emitParticles(util.vector().of(HAWSE.x, 5.0, HAWSE.z), splash, 8, 2);
        BlockPos top = BlockPos.containing(HAWSE);
        scene.world().setBlock(top, chain(false), false);
        // the ring passes the next cell down after a quarter and two thirds of the fall
        scene.idle(Math.max(1, ticks / 4));
        scene.world().setBlock(top.below(), chain(true), false);
        scene.idle(Math.max(1, ticks * 2 / 3 - ticks / 4));
        scene.world().setBlock(top.below(2), chain(true), false);
        scene.idle(Math.max(1, ticks - ticks * 2 / 3));
        phase(scene, capstan, CapstanBlock.Phase.HOLDING);
    }

    /** The anchor comes up: the chain is wound in from below as the ring passes each cell. */
    private static void weigh(SceneBuilder scene, SceneBuildingUtil util, BlockPos capstan, AtomicReference<AnchorEntity> anchor,
                              Vec3 stowed, Vec3 resting, int ticks) {
        phase(scene, capstan, CapstanBlock.Phase.RAISING);
        moveAnchor(scene, anchor, resting, stowed, ticks);
        BlockPos top = BlockPos.containing(HAWSE);
        scene.idle(Math.max(1, ticks / 3));
        scene.world().setBlock(top.below(2), Blocks.WATER.defaultBlockState(), false);
        scene.idle(Math.max(1, ticks * 3 / 4 - ticks / 3));
        scene.world().setBlock(top.below(), Blocks.WATER.defaultBlockState(), false);
        scene.idle(Math.max(1, ticks - ticks * 3 / 4));
        scene.world().setBlock(top, Blocks.AIR.defaultBlockState(), false);
        phase(scene, capstan, CapstanBlock.Phase.RAISED);
    }

    private static BlockState chain(boolean wet) {
        return Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, wet);
    }

    private static void moveAnchor(SceneBuilder scene, AtomicReference<AnchorEntity> anchor, Vec3 from, Vec3 to, int ticks) {
        SceneKit.everyTick(scene, ticks, (s, t, total) -> {
            AnchorEntity a = anchor.get();
            if (a != null) {
                SceneKit.place(a, SceneKit.lerp(from, to, (t + 1) / (double) total), 0);
            }
        });
    }

    private static void phase(SceneBuilder scene, BlockPos capstan, CapstanBlock.Phase phase) {
        scene.world().modifyBlock(capstan, s -> s.hasProperty(CapstanBlock.ANCHOR) ? s.setValue(CapstanBlock.ANCHOR, phase) : s, false);
    }

    /** The bars' synced turning, as the server sends it to the bar renderer (CAP2). */
    private static void bars(SceneBuilder scene, BlockPos capstan, CapstanTurning turning) {
        scene.world().modifyBlockEntityNBT(scene.getScene().getSceneBuildingUtil().select().position(capstan),
                CapstanBlockEntity.class, tag -> {
                    tag.putByte("turning", (byte) turning.ordinal());
                    tag.putDouble("rate", TURN_RATE);
                });
    }

    /** What the capstan does at tick {@code t} of the crew member's work. */
    @FunctionalInterface
    private interface Work {
        CapstanTurning at(int t);
    }

    /** The crew member walks round the drum with the bars ({@link CapstanWalk}), facing along the circle. */
    private static void walk(SceneBuilder scene, BlockPos capstan, AtomicReference<CrewMember> sailor, Vec3 spot, int ticks,
                             Work work) {
        double cx = capstan.getX() + 0.5;
        double cz = capstan.getZ() + 0.5;
        double hx = spot.x - cx;
        double hz = spot.z - cz;
        CapstanWalk[] walk = new CapstanWalk[1];
        SceneKit.everyTick(scene, ticks, (s, t, total) -> {
            if (t == 0 || walk[0] == null) {
                walk[0] = new CapstanWalk();
            }
            CrewMember c = sailor.get();
            if (c == null) {
                return;
            }
            int sign = work.at(t).sign();
            walk[0].tick(sign, TURN_RATE);
            double[] o = walk[0].offset(hx, hz, WALK_RADIUS);
            double[] d = CapstanWalk.heading(o, sign);
            float yaw = sign == 0 ? SceneKit.yaw(-o[0], -o[1]) : SceneKit.yaw(d[0], d[1]);
            SceneKit.place(c, new Vec3(cx + o[0], spot.y, cz + o[1]), yaw);
        });
    }
}
