package com.richardsenger.piratesnships.almanac.ponder;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.OnDeck;
import com.richardsenger.piratesnships.crew.lean.LeanRules;
import com.richardsenger.piratesnships.crew.lean.RailSpots;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.ship.rigging.NetFittingBlockEntity;
import com.richardsenger.piratesnships.station.StationContent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * "On deck" (PND4, docs/almanac.md "Scenes"; crew/lean and the boarding nets of ship/rigging, RAIL5, design.md §4.8), on
 * the schematic {@code assets/pirates_n_ships/ponder/on_deck.nbt} ({@code art/structures/ponder/on_deck.py}: our ship,
 * bow north, a railing all along her port gunwale at x 3, y 4, z 1..9, two floor chainplates on it at [3, 5, 3] and
 * [3, 5, 6], and a pirate longboat alongside to the west at x 0..1, z 3..6).
 *
 * <p>The leaners stand where {@link RailSpots#leanPoint} puts them (the railing's own cell, {@link LeanRules#LEAN_OFFSET}
 * outboard of its centre, facing out) and play the {@code lean} pose through the synced flag
 * ({@link CrewMember#setLeaning}); their heads turn by {@link LeanRules#headYaw} and look {@link LeanRules#HEAD_PITCH}
 * up, as the server drives them. The boarding net is strung between the two chainplates the way {@code RatlineNetItem}
 * strings it (both fittings learn the link), so {@code RatlineNetRenderer} draws it as a boarding net along the rail;
 * the hold is shown by stopping the boarder for {@code rigging.boarding_net_hold} (20 ticks). Without BRD3 on main the
 * boarding crew's recall is left out. About 37 seconds.
 */
final class OnDeckScene {

    static final int X = 3;
    static final BlockPos NET_A = new BlockPos(X, 5, 3);
    static final BlockPos NET_B = new BlockPos(X, 5, 6);
    /** The railing cells the two hands lean in, and the default hold of a boarding net [ticks]. */
    static final List<BlockPos> LEAN_CELLS = List.of(new BlockPos(X, 4, 1), new BlockPos(X, 4, 8));
    static final int HOLD_TICKS = 20;
    /** The boarder's way: on the longboat, held at the net over the rail, then over onto the deck. */
    static final Vec3 BOAT = new Vec3(1.5, 3.0, 4.5);
    static final Vec3 AT_NET = new Vec3(2.9, 4.6, 4.5);
    static final Vec3 ABOARD = new Vec3(4.6, 4.0, 4.5);
    /** The hand who passes the net: from the deck, over the rail, down into the longboat. */
    static final Vec3 DECK = new Vec3(5.5, 4.0, 5.5);
    static final Vec3 OVER = new Vec3(3.3, 5.1, 5.5);
    static final Vec3 DOWN = new Vec3(1.5, 3.0, 5.5);

    private OnDeckScene() {
    }

    /** Where a hand leaning in {@code cell} stands (port side: facing west). */
    static Vec3 leanPoint(BlockPos cell) {
        return RailSpots.leanPoint(new RailSpots.Spot(cell, Direction.WEST));
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.ON_DECK, OnDeck.TITLE);
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.idle(10);
        Selection plates = util.select().position(NET_A).add(util.select().position(NET_B));
        scene.world().showSection(util.select().layersFrom(1).substract(plates), Direction.DOWN);
        scene.idle(20);

        // two idle hands lean on the rail, looking out to sea
        float west = SceneKit.yaw(-1, 0);
        AtomicReference<CrewMember> bow = new AtomicReference<>();
        AtomicReference<CrewMember> aft = new AtomicReference<>();
        scene.world().createEntity(level -> leaner(level, leanPoint(LEAN_CELLS.get(0)), bow));
        scene.world().createEntity(level -> leaner(level, leanPoint(LEAN_CELLS.get(1)), aft));
        // the slow head turn, all scene long for the one who stays; the other's ends with the order
        SceneKit.everyTick(scene, 720, (s, t, total) -> look(aft.get(), west, t));
        SceneKit.everyTick(scene, 150, (s, t, total) -> look(bow.get(), west, t + 70));
        scene.idle(10);
        Vec3 aftHead = leanPoint(LEAN_CELLS.get(1)).add(0, 1.9, 0);
        SceneKit.focus(scene, leanPoint(LEAN_CELLS.get(1)).add(0, 1.2, 0));
        say(scene.overlay().showText(60), OnDeck.IDLE)
                .pointAt(aftHead)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(60), OnDeck.LOOK)
                .pointAt(aftHead)
                .placeNearTarget();
        scene.idle(65);

        // any order calls him off
        Vec3 whistle = util.vector().of(6.5, 6.0, 2.5);
        scene.overlay().showControls(whistle, Pointing.DOWN, 20).rightClick().withItem(SceneKit.item("captains_whistle"));
        scene.idle(10);
        Vec3 bowAt = leanPoint(LEAN_CELLS.get(0));
        Vec3 bowOff = util.vector().of(5.5, 4.0, 2.5);
        SceneKit.once(scene, s -> {
            if (bow.get() != null) {
                bow.get().setLeaning(false);
            }
        });
        SceneKit.walk(scene, bow, bowAt, bowOff, 20);
        say(scene.overlay().showText(55), OnDeck.CALLED)
                .pointAt(bowOff.add(0, 1.9, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // a boarding net along the rail
        Vec3 a = util.vector().of(X + 0.5, 5.3, 3.5);
        Vec3 b = util.vector().of(X + 0.5, 5.3, 6.5);
        scene.world().showSection(plates, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(a.add(0, 0.3, 0), Pointing.DOWN, 15).rightClick().withItem(SceneKit.item("ratline_net"));
        scene.idle(18);
        scene.overlay().showControls(b.add(0, 0.3, 0), Pointing.DOWN, 15).rightClick().withItem(SceneKit.item("ratline_net"));
        scene.idle(12);
        scene.world().modifyBlockEntity(NET_A, NetFittingBlockEntity.class, be -> be.setNet(NET_B, true));
        scene.world().modifyBlockEntity(NET_B, NetFittingBlockEntity.class, be -> be.setNet(NET_A, false));
        say(scene.overlay().showText(60), OnDeck.NET)
                .pointAt(a)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        Vec3 mid = SceneKit.lerp(a, b, 0.5).add(0, 0.4, 0);
        say(scene.overlay().showText(55), OnDeck.ALONG)
                .pointAt(mid)
                .placeNearTarget();
        scene.idle(60);

        // a pirate comes over the rail: held, then over
        AtomicReference<Pirate> boarder = new AtomicReference<>();
        scene.world().createEntity(level -> pirate(level, BOAT, boarder));
        scene.idle(10);
        SceneKit.walk(scene, boarder, BOAT, AT_NET, 15);
        scene.idle(15);
        SceneKit.everyTick(scene, HOLD_TICKS + 40, (s, t, total) -> {
            Pirate p = boarder.get();
            if (p != null) {
                SceneKit.place(p, AT_NET, SceneKit.yaw(1, 0));
            }
        });
        scene.overlay().showOutline(PonderPalette.RED, "net", util.select().fromTo(X, 5, 4, X, 5, 5), 55);
        say(scene.overlay().showText(55), OnDeck.HELD)
                .colored(PonderPalette.RED)
                .pointAt(AT_NET.add(0, 1.9, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(62);
        SceneKit.fly(scene, boarder, AT_NET, ABOARD, 0.6, 15);
        scene.idle(15);
        say(scene.overlay().showText(50), OnDeck.OVER)
                .pointAt(ABOARD.add(0, 1.9, 0))
                .placeNearTarget();
        scene.idle(55);

        // your own hand passes through
        AtomicReference<CrewMember> hand = new AtomicReference<>();
        scene.world().createEntity(level -> {
            CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
            SceneKit.place(c, DECK, west);
            hand.set(c);
            return c;
        });
        scene.idle(8);
        SceneKit.walk(scene, hand, DECK, OVER, 15);
        scene.idle(15);
        SceneKit.walk(scene, hand, OVER, DOWN, 12);
        scene.idle(12);
        say(scene.overlay().showText(55), OnDeck.PASS)
                .colored(PonderPalette.GREEN)
                .pointAt(DOWN.add(0, 1.9, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // three cutlass blows
        for (int i = 0; i < 3; i++) {
            scene.overlay().showControls(mid, Pointing.DOWN, 8).leftClick().withItem(SceneKit.item("cutlass"));
            scene.idle(10);
        }
        scene.world().modifyBlockEntity(NET_A, NetFittingBlockEntity.class, NetFittingBlockEntity::clearNet);
        scene.world().modifyBlockEntity(NET_B, NetFittingBlockEntity.class, NetFittingBlockEntity::clearNet);
        scene.world().createItemEntity(a.add(0, 0.3, 0), util.vector().of(0, 0.15, 0), SceneKit.item("ratline_net"));
        say(scene.overlay().showText(55), OnDeck.CUT)
                .pointAt(mid)
                .placeNearTarget();
        scene.idle(60);
        scene.markAsFinished();
    }

    private static CrewMember leaner(Level level, Vec3 at, AtomicReference<CrewMember> ref) {
        CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
        SceneKit.place(c, at, SceneKit.yaw(-1, 0));
        c.setLeaning(true);
        ref.set(c);
        return c;
    }

    /** The lean's head, {@code t} ticks in: the body faces {@code body}, the head turns and looks a little down. */
    private static void look(CrewMember c, float body, int t) {
        if (c == null || !c.isLeaning()) {
            return;
        }
        c.yBodyRot = body;
        c.yBodyRotO = body;
        c.setYRot(body);
        c.yRotO = body;
        c.yHeadRotO = c.yHeadRot;
        c.yHeadRot = body + LeanRules.headYaw(t);
        c.setXRot(LeanRules.HEAD_PITCH);
    }

    private static Pirate pirate(Level level, Vec3 at, AtomicReference<Pirate> ref) {
        Pirate p = new Pirate(MobContent.PIRATE.get(), level);
        p.setItemSlot(EquipmentSlot.MAINHAND, SceneKit.item("cutlass"));
        SceneKit.place(p, at, SceneKit.yaw(1, 0));
        ref.set(p);
        return p;
    }
}
