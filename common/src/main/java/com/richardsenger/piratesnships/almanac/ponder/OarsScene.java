package com.richardsenger.piratesnships.almanac.ponder;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Oars;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.sailing.oars.Rowing;
import com.richardsenger.piratesnships.station.StationContent;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * "Oars" (OAR1, docs/almanac.md "Scenes"; sailing/oars, design.md §5.3b), on the schematic
 * {@code assets/pirates_n_ships/ponder/oars.nbt} ({@code art/structures/ponder/oars.py}: a small ship in a calm, bow
 * north, a railing along the port edge, an oar bench on the starboard edge at [6, 4, 3] facing east, the helm aft).
 *
 * <p>The crew walk to their places as {@code Rowing} sends them (the rail on either side in turn, the bench) and show
 * the poses the server syncs ({@link SceneSync}): {@link CrewPose#ROW_STANDING} at the rail, {@link CrewPose#ROW_SEATED}
 * on the bench, facing aft, the oar in the hand on the outboard side ({@link Rowing#leftHanded}). About 30 seconds.
 */
final class OarsScene {

    static final BlockPos BENCH = new BlockPos(6, 4, 3);
    static final BlockPos HELM = new BlockPos(4, 4, 7);
    /** The rowers' places: inboard of the port railing, at the starboard edge, on the bench. */
    static final Vec3 PORT_SPOT = new Vec3(3.5, 4.0, 4.5);
    static final Vec3 STARBOARD_SPOT = new Vec3(6.5, 4.0, 5.5);
    static final Vec3 BENCH_SPOT = new Vec3(6.5, 4.0, 3.5);
    /** Where the hands stand before the order. */
    static final Vec3[] START = {new Vec3(4.5, 4.0, 3.5), new Vec3(4.5, 4.0, 5.5), new Vec3(4.5, 4.0, 2.5)};
    /** The whistle in the air above the deck. */
    static final Vec3 WHISTLE = new Vec3(4.5, 6.2, 4.5);

    private OarsScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.OARS, Oars.TITLE);
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(15);

        AtomicReference<CrewMember> a = new AtomicReference<>();
        AtomicReference<CrewMember> b = new AtomicReference<>();
        scene.world().createEntity(level -> crew(level, START[0], a));
        scene.world().createEntity(level -> crew(level, START[1], b));
        scene.idle(10);

        // Row: the whistle's oars entry
        order(scene);
        say(scene.overlay().showText(70), Oars.ROW)
                .pointAt(WHISTLE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        SceneKit.walk(scene, a, START[0], PORT_SPOT, 20);
        SceneKit.walk(scene, b, START[1], STARBOARD_SPOT, 20);
        scene.idle(22);
        SceneKit.once(scene, s -> {
            row(a.get(), PORT_SPOT, Direction.WEST, CrewPose.ROW_STANDING);
            row(b.get(), STARBOARD_SPOT, Direction.EAST, CrewPose.ROW_STANDING);
        });
        scene.idle(35);

        // at the rail, the sides in turn
        scene.overlay().showOutline(PonderPalette.WHITE, "rail", util.select().fromTo(2, 4, 2, 2, 4, 6), 70);
        SceneKit.focus(scene, PORT_SPOT.add(0, 1.0, 0));
        say(scene.overlay().showText(70), Oars.RAIL)
                .pointAt(PORT_SPOT.add(0, 1.8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);

        // the push along the bow
        SceneKit.arrow(scene, PonderPalette.GREEN, new Vec3(4.5, 4.4, 6.0), new Vec3(4.5, 4.4, 1.0), 75);
        say(scene.overlay().showText(70), Oars.PUSH)
                .colored(PonderPalette.GREEN)
                .pointAt(new Vec3(4.5, 4.4, 2.0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // the bench
        AtomicReference<CrewMember> c = new AtomicReference<>();
        scene.world().createEntity(level -> crew(level, START[2], c));
        scene.idle(10);
        SceneKit.walk(scene, c, START[2], BENCH_SPOT, 18);
        scene.idle(20);
        SceneKit.once(scene, s -> row(c.get(), BENCH_SPOT, Direction.EAST, CrewPose.ROW_SEATED));
        scene.overlay().showOutline(PonderPalette.GREEN, "bench", util.select().position(BENCH), 70);
        SceneKit.focus(scene, BENCH);
        say(scene.overlay().showText(70), Oars.BENCH)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(BENCH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // Ship oars
        order(scene);
        SceneKit.once(scene, s -> {
            stow(a.get());
            stow(b.get());
            stow(c.get());
        });
        SceneKit.walk(scene, a, PORT_SPOT, START[0], 20);
        SceneKit.walk(scene, b, STARBOARD_SPOT, START[1], 20);
        SceneKit.walk(scene, c, BENCH_SPOT, START[2], 20);
        say(scene.overlay().showText(70), Oars.SHIP)
                .pointAt(WHISTLE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        say(scene.overlay().showText(65), Oars.FASTER)
                .pointAt(util.vector().topOf(HELM))
                .placeNearTarget();
        scene.idle(70);
        scene.markAsFinished();
    }

    /** The whistle's oars entry picked in the air: the whistle, then the oar icon. */
    private static void order(SceneBuilder scene) {
        scene.overlay().showControls(WHISTLE, Pointing.DOWN, 25).rightClick().withItem(SceneKit.item("captains_whistle"));
        scene.idle(12);
        scene.overlay().showControls(WHISTLE, Pointing.DOWN, 20).withItem(SceneKit.item("oar"));
        scene.idle(8);
    }

    private static CrewMember crew(net.minecraft.world.level.Level level, Vec3 at, AtomicReference<CrewMember> ref) {
        CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
        SceneKit.place(c, at, SceneKit.yaw(0, 1));
        ref.set(c);
        return c;
    }

    /** At its oar: facing aft (south), the oar in the hand on the {@code outboard} side, rowing. */
    private static void row(CrewMember c, Vec3 at, Direction outboard, CrewPose pose) {
        if (c == null) return;
        SceneKit.place(c, at, SceneKit.yaw(0, 1));
        c.setLeftHanded(Rowing.leftHanded(Direction.SOUTH, outboard));
        c.setItemSlot(EquipmentSlot.MAINHAND, SceneKit.item("oar"));
        SceneSync.stationPose(c, pose);
    }

    private static void stow(CrewMember c) {
        if (c == null) return;
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        SceneSync.stationPose(c, null);
    }
}
