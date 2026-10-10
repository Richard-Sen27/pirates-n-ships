package com.richardsenger.piratesnships.almanac.ponder;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Nets;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingLatch;
import com.richardsenger.piratesnships.ship.rigging.NetFitting;
import com.richardsenger.piratesnships.ship.rigging.RailingLatchBlock;
import com.richardsenger.piratesnships.ship.rigging.NetFittingBlockEntity;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.lookout.CrowsNestBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * "Ratline nets" (PND4, docs/almanac.md "Scenes"; ship/rigging RL2, RL3, RL4 and RL5, design.md §4.8), on the schematic
 * {@code assets/pirates_n_ships/ponder/ratline_nets.nbt} ({@code art/structures/ponder/ratline_nets.py}: a deck, bow
 * north, a railing along the port edge at x 0, y 1, with the invisible net ends of three latches over it at z 2, 4 and
 * 6 ({@code railing_latch_end}, RL5), the mast at x 4, z 4 up to y 6 with a mast hoop on its west face at [3, 6, 4] and the
 * crow's nest on top at [4, 7, 4]).
 *
 * <p>A net is strung the way {@code RatlineNetItem} strings it: the railing gets its latch (the eye bolt, its
 * {@code latch} state), both fittings' block entities learn the link ({@link NetFittingBlockEntity#setNet}, the latch's
 * net end as end A), and the hoop, the fan's hub, keeps its strands in the
 * bearing order of {@link RatlineNetRules#fanOrder} with RL5's row rule ({@link RatlineNetRules#joins}) between neighbours
 * ({@link NetFittingBlockEntity#setFan}), so the game's {@code RatlineNetRenderer} draws the nets and the fan. The
 * climbable cells are invisible and left out. The climber is a crew member moved along the line (in the game a player
 * climbs; crew plan no climbs), up past the hoop by RL3's lift and forward onto the nest's floor. About 33 seconds.
 */
final class RatlineNetsScene {

    static final BlockPos HOOP = new BlockPos(3, 6, 4);
    static final BlockPos NEST = new BlockPos(4, 7, 4);
    /** The latches' net ends over the rail: the first net's, then the two that fan into the same hoop. */
    static final BlockPos PLATE = new BlockPos(0, 2, 4);
    static final List<BlockPos> FAN = List.of(new BlockPos(0, 2, 2), PLATE, new BlockPos(0, 2, 6));
    /** The rail's latched edge (port, west). */
    static final Direction EDGE = Direction.WEST;
    /** The default {@code rigging.net_max_spread}: neighbouring chainplates closer than this fan into one net. */
    static final double MAX_SPREAD = 6.0;
    /** RL3: how far above the hoop's block the climb carries on before forward walks the climber off. */
    static final double EXIT_LIFT = 0.6;

    private RatlineNetsScene() {
    }

    /** A latch's ring ({@link RailingLatchBlock#ring}: the eye bolt on the rail's west edge under the net end {@code end}). */
    static Point plateRing(BlockPos end) {
        return RailingLatchBlock.ring(end, EDGE);
    }

    /** The railing under the net end {@code end} gets its latch (the eye bolt appears on its cap). */
    private static void latch(SceneBuilder scene, BlockPos end) {
        scene.world().modifyBlock(end.below(), s -> s.hasProperty(RailingBlock.LATCH)
                ? s.setValue(RailingBlock.LATCH, RailingLatch.of(EDGE)) : s, false);
    }

    /** The hoop's ring ({@code MastHoopBlock#ring}, facing west: {@link NetFitting#RING_FROM_SUPPORT} off the mast's face). */
    static Point hoopRing() {
        return new Point(HOOP.getX() + 1 - NetFitting.RING_FROM_SUPPORT, HOOP.getY() + 0.5, HOOP.getZ() + 0.5);
    }

    static Vec3 vec(Point p) {
        return new Vec3(p.x(), p.y(), p.z());
    }

    /** The fan's strands in the hub's order ({@link RatlineNetRules#fanOrder}) and whether each joins the next. */
    record Fan(List<BlockPos> order, boolean[] joinNext) {
    }

    static Fan fan(List<BlockPos> plates) {
        List<Point> feet = plates.stream().map(RatlineNetsScene::plateRing).toList();
        int[] idx = RatlineNetRules.fanOrder(hoopRing(), feet);
        List<BlockPos> order = new ArrayList<>();
        for (int i : idx) {
            order.add(plates.get(i));
        }
        boolean[] join = new boolean[order.size()];
        for (int k = 0; k + 1 < order.size(); k++) {
            join[k] = RatlineNetRules.joins(plateRing(order.get(k)), hoopRing(), plateRing(order.get(k + 1)), hoopRing(), MAX_SPREAD);
        }
        return new Fan(order, join);
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.RATLINE_NETS, Nets.TITLE);
        scene.configureBasePlate(0, 0, 9);
        // the nest stands eight blocks over the deck: a smaller, lower view keeps it and its texts on the screen (PND3)
        scene.scaleSceneView(0.8f);
        scene.setSceneOffsetY(-2.5f);
        scene.showBasePlate();
        scene.idle(10);

        Selection fittings = util.select().position(HOOP);
        for (BlockPos p : FAN) {
            fittings = fittings.add(util.select().position(p));
        }
        scene.world().showSection(util.select().layersFrom(1).substract(fittings), Direction.DOWN);
        scene.idle(20);

        // the fittings
        Vec3 a = vec(plateRing(PLATE));
        Vec3 b = vec(hoopRing());
        scene.world().showSection(util.select().position(PLATE), Direction.DOWN);
        scene.world().showSection(util.select().position(HOOP), Direction.EAST);
        scene.idle(10);
        say(scene.overlay().showText(65), Nets.FITTINGS)
                .pointAt(a)
                .placeNearTarget()
                .attachKeyFrame();
        scene.overlay().showOutline(PonderPalette.WHITE, "hoop", util.select().position(HOOP), 65);
        scene.idle(70);

        // strung: on one fitting, then on the other
        scene.overlay().showControls(a.add(0, 0.2, 0), Pointing.DOWN, 20).rightClick().withItem(SceneKit.item("ratline_net"));
        scene.idle(25);
        scene.overlay().showControls(b, Pointing.LEFT, 20).rightClick().withItem(SceneKit.item("ratline_net"));
        scene.idle(15);
        latch(scene, PLATE);
        scene.world().modifyBlockEntity(PLATE, NetFittingBlockEntity.class, be -> be.setNet(HOOP, true));
        scene.world().modifyBlockEntity(HOOP, NetFittingBlockEntity.class, be -> be.setNet(PLATE, false));
        scene.idle(5);
        say(scene.overlay().showText(60), Nets.STRING)
                .pointAt(SceneKit.lerp(a, b, 0.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        scene.overlay().showLine(PonderPalette.GREEN, a, b, 60);
        say(scene.overlay().showText(60), Nets.LINE)
                .colored(PonderPalette.GREEN)
                .pointAt(SceneKit.lerp(a, b, 0.35))
                .placeNearTarget();
        scene.idle(65);

        // the climb, and off the top into the nest
        Vec3 deck = util.vector().of(2.5, 1.0, 4.5);
        Vec3 foot = new Vec3(0.9, 2.0, 4.5);
        Vec3 top = new Vec3(b.x - 0.2, b.y - 0.2, b.z);
        Vec3 lift = new Vec3(b.x - 0.2, HOOP.getY() + 1 + EXIT_LIFT, b.z);
        Vec3 floor = new Vec3(NEST.getX() + 0.5, NEST.getY() + CrowsNestBlock.FLOOR_HEIGHT / 16.0, NEST.getZ() + 0.5);
        AtomicReference<CrewMember> climber = new AtomicReference<>();
        scene.world().createEntity(level -> {
            CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
            SceneKit.place(c, deck, SceneKit.yaw(-1, 0));
            climber.set(c);
            return c;
        });
        scene.idle(10);
        SceneKit.walk(scene, climber, deck, foot, 15);
        scene.idle(15);
        SceneKit.walk(scene, climber, foot, top, 45);
        SceneKit.once(scene, s -> face(climber.get(), SceneKit.yaw(1, 0)));
        say(scene.overlay().showText(60), Nets.CLIMB)
                .pointAt(SceneKit.lerp(a, b, 0.5))
                .placeNearTarget()
                .attachKeyFrame();
        SceneKit.everyTick(scene, 45, (s, t, total) -> face(climber.get(), SceneKit.yaw(1, 0)));
        scene.idle(50);
        SceneKit.walk(scene, climber, top, lift, 12);
        SceneKit.everyTick(scene, 12, (s, t, total) -> face(climber.get(), SceneKit.yaw(1, 0)));
        scene.idle(12);
        SceneKit.walk(scene, climber, lift, floor, 12);
        scene.idle(12);
        SceneKit.focus(scene, floor.add(0, 1.0, 0));
        say(scene.overlay().showText(60), Nets.TOP)
                .pointAt(util.vector().of(NEST.getX() + 0.5, NEST.getY() + 1.2, NEST.getZ() + 0.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // two more chainplates fanning into the same hoop
        for (BlockPos p : List.of(FAN.get(0), FAN.get(2))) {
            scene.world().showSection(util.select().position(p), Direction.DOWN);
        }
        for (BlockPos p : List.of(FAN.get(0), FAN.get(2))) {
            scene.overlay().showControls(vec(plateRing(p)).add(0, 0.2, 0), Pointing.DOWN, 15).rightClick()
                    .withItem(SceneKit.item("ratline_net"));
            scene.idle(18);
            latch(scene, p);
            scene.world().modifyBlockEntity(p, NetFittingBlockEntity.class, be -> be.setNet(HOOP, true));
        }
        string(scene, FAN);
        scene.idle(5);
        say(scene.overlay().showText(60), Nets.FAN)
                .pointAt(b)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        Vec3 first = vec(plateRing(FAN.get(0)));
        Vec3 last = vec(plateRing(FAN.get(2)));
        SceneKit.path(scene, PonderPalette.GREEN, List.of(first, b, last, first), 65);
        say(scene.overlay().showText(60), Nets.FILL)
                .colored(PonderPalette.GREEN)
                .pointAt(SceneKit.lerp(SceneKit.lerp(first, last, 0.5), b, 0.3))
                .placeNearTarget();
        scene.idle(65);

        // a broken latch drops its strand (and the eye bolt goes); the rest fan on
        BlockPos gone = FAN.get(2);
        scene.overlay().showControls(last.add(0, 0.2, 0), Pointing.DOWN, 15).leftClick();
        scene.idle(15);
        scene.world().setBlock(gone, Blocks.AIR.defaultBlockState(), true);
        scene.world().modifyBlock(gone.below(), s -> s.hasProperty(RailingBlock.LATCH)
                ? s.setValue(RailingBlock.LATCH, RailingLatch.NONE) : s, false);
        scene.world().modifyBlockEntity(HOOP, NetFittingBlockEntity.class, be -> be.clearNet(gone));
        string(scene, List.of(FAN.get(0), FAN.get(1)));
        scene.world().createItemEntity(last.add(0, 0.3, 0), util.vector().of(0, 0.15, 0), SceneKit.item("ratline_net"));
        say(scene.overlay().showText(65), Nets.BREAK)
                .colored(PonderPalette.RED)
                .pointAt(last)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);
        scene.markAsFinished();
    }

    /** The hoop holds a strand to each of {@code plates}, in fan order and joined as the rule joins them. */
    private static void string(SceneBuilder scene, List<BlockPos> plates) {
        Fan fan = fan(plates);
        scene.world().modifyBlockEntity(HOOP, NetFittingBlockEntity.class, be -> {
            for (BlockPos p : plates) {
                be.setNet(p, false);
            }
            be.setFan(fan.order(), fan.joinNext());
        });
    }

    private static void face(CrewMember c, float yaw) {
        if (c != null) {
            SceneKit.place(c, c.position(), yaw);
        }
    }
}
