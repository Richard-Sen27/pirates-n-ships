package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Orders;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.harbor.HarborMaster;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.order.RadialLayout;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Crew and orders" (PND2, docs/almanac.md "Scenes"; crew/hiring, the captain's whistle of station/winch and
 * station/order, the job board of station/jobs and the gunnery of combat/cannon/npc, design.md §7.1, §7.2), on the
 * schematic {@code assets/pirates_n_ships/ponder/crew_orders.nbt} ({@code art/structures/ponder/crew_orders.py}: a quay
 * with the harbor desk at [1, 4, 5], a ship alongside with a winch at [4, 4, 3], a cannon at [8, 4, 4] facing east, its
 * supply at [8, 4, 6] and the helm at [6, 4, 7]).
 *
 * <p>The order wheel is drawn from the real menu: {@link WhistleOrder#entries()} with their icons, laid out round a
 * circle by {@link RadialLayout} (entry 0 at the top, the rest clockwise). Crew members walk to their stations and show
 * the work and gun poses the server would sync ({@link SceneSync}); the cannon loads through its real {@code load}
 * state. The arc lines of "fire at will" are the default 15° either side of the barrel. About 36 seconds.
 */
final class CrewOrdersScene {

    static final BlockPos DESK = new BlockPos(1, 4, 5);
    static final BlockPos WINCH = new BlockPos(4, 4, 3);
    static final BlockPos CANNON = new BlockPos(8, 4, 4);
    static final BlockPos SUPPLY = new BlockPos(8, 4, 6);
    static final BlockPos HELM = new BlockPos(6, 4, 7);
    /** Default {@code cannons.npc.arc_degrees}. */
    static final double ARC = 15.0;
    /** The wheel's centre (in the air above the ship) and radius, in blocks. */
    static final Vec3 WHEEL = new Vec3(6.5, 7.6, 5.5);
    static final double WHEEL_RADIUS = 2.3;

    private CrewOrdersScene() {
    }

    /** Where entry {@code i} of the whistle's wheel sits: in the vertical plane through {@link #WHEEL}, clockwise. */
    static Vec3 wheelSlot(int i) {
        List<WhistleOrder> entries = WhistleOrder.entries();
        RadialLayout layout = new RadialLayout(entries.size(), 0, WHEEL_RADIUS);
        double a = layout.centerAngle(i);
        return WHEEL.add(RadialLayout.x(a, WHEEL_RADIUS), -RadialLayout.y(a, WHEEL_RADIUS), 0);
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.CREW_ORDERS, Orders.TITLE);
        scene.configureBasePlate(0, 0, 11);
        // the order wheel floats above the ship: a smaller, lower view keeps it and its icons on the screen (PND3)
        scene.scaleSceneView(0.8f);
        scene.setSceneOffsetY(-2.5f);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(15);

        // hiring at the harbor desk
        scene.world().createEntity(level -> {
            HarborMaster m = new HarborMaster(MobContent.HARBOR_MASTER.get(), level);
            SceneKit.place(m, new Vec3(0.5, 4.0, 5.5), SceneKit.yaw(1, 0));
            return m;
        });
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(DESK), Pointing.DOWN, 40).rightClick();
        say(scene.overlay().showText(70), Orders.HIRE)
                .pointAt(util.vector().topOf(DESK))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);

        Vec3 recruitSpot = new Vec3(4.5, 4.0, 5.5);
        AtomicReference<CrewMember> recruit = new AtomicReference<>();
        scene.world().createEntity(level -> crew(level, recruitSpot, SceneKit.yaw(-1, 0), recruit));
        ParticleEmitter poof = scene.effects().simpleParticleEmitter(ParticleTypes.POOF, Vec3.ZERO);
        scene.effects().emitParticles(recruitSpot.add(0, 1, 0), poof, 6, 1);
        say(scene.overlay().showText(60), Orders.ABOARD)
                .pointAt(recruitSpot.add(0, 1.8, 0))
                .placeNearTarget();
        scene.idle(65);

        // the order wheel
        ItemStack whistle = SceneKit.item("captains_whistle");
        scene.overlay().showControls(new Vec3(6.5, 5.6, 5.5), Pointing.DOWN, 25).rightClick().withItem(whistle);
        scene.idle(15);
        List<WhistleOrder> entries = WhistleOrder.entries();
        for (int i = 0; i < entries.size(); i++) {
            showIcon(scene.overlay().showControls(wheelSlot(i), Pointing.DOWN, 70), entries.get(i).icon());
        }
        say(scene.overlay().showText(70), Orders.WHEEL)
                .pointAt(WHEEL.add(0, -WHEEL_RADIUS, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // assigning a hand by hand: he walks to the winch
        scene.overlay().showControls(recruitSpot.add(0, 2.2, 0), Pointing.DOWN, 20).rightClick().withItem(whistle);
        scene.idle(22);
        scene.overlay().showControls(util.vector().topOf(WINCH), Pointing.DOWN, 20).rightClick().withItem(whistle);
        scene.idle(10);
        Vec3 winchSpot = new Vec3(4.5, 4.0, 4.5);
        SceneKit.walk(scene, recruit, recruitSpot, winchSpot, 25);
        say(scene.overlay().showText(60), Orders.ASSIGN)
                .pointAt(util.vector().topOf(WINCH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(26);
        SceneKit.once(scene, s -> face(recruit.get(), winchSpot, util.vector().centerOf(WINCH)));
        scene.idle(40);

        // an order to the stations: the hand at the winch works it
        order(scene, WhistleOrder.HOIST);
        SceneKit.once(scene, s -> SceneSync.working(recruit.get(), true));
        SceneKit.focus(scene, winchSpot.add(0, 1.0, 0));
        say(scene.overlay().showText(55), Orders.ORDER)
                .pointAt(winchSpot.add(0, 1.8, 0))
                .placeNearTarget();
        scene.idle(45);
        SceneKit.once(scene, s -> SceneSync.working(recruit.get(), false));
        scene.idle(15);

        // the job board: a free hand takes the unmanned cannon by himself
        Vec3 freeSpot = new Vec3(5.5, 4.0, 6.5);
        Vec3 gunSpot = new Vec3(7.5, 4.0, 5.5);
        AtomicReference<CrewMember> gunner = new AtomicReference<>();
        scene.world().createEntity(level -> crew(level, freeSpot, SceneKit.yaw(0, -1), gunner));
        scene.idle(10);
        order(scene, WhistleOrder.LOAD);
        SceneKit.walk(scene, gunner, freeSpot, gunSpot, 22);
        scene.overlay().showOutline(PonderPalette.GREEN, "gun", util.select().fromTo(7, 4, 4, 8, 4, 4), 70);
        scene.overlay().showOutline(PonderPalette.WHITE, "supply", util.select().position(SUPPLY), 70);
        say(scene.overlay().showText(70), Orders.JOBS)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(CANNON))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(23);
        SceneKit.once(scene, s -> {
            face(gunner.get(), gunSpot, util.vector().centerOf(7, 4, 4));
            SceneSync.stationPose(gunner.get(), CrewPose.CANNON_LOAD);
        });
        SceneKit.focus(scene, gunSpot.add(0, 1.0, 0));
        scene.idle(40);
        scene.world().modifyBlock(CANNON, st -> st.hasProperty(CannonBlock.LOAD) ? st.setValue(CannonBlock.LOAD, CannonLoad.LOADED) : st, false);
        SceneKit.once(scene, s -> SceneSync.stationPose(gunner.get(), null));
        scene.idle(15);

        // fire at will: the crew lays the gun by itself within its arc
        order(scene, WhistleOrder.FIRE_AT_WILL);
        SceneKit.once(scene, s -> SceneSync.stationPose(gunner.get(), CrewPose.CANNON_AIM));
        Vec3 muzzle = new Vec3(CANNON.getX() + 1.5, CANNON.getY() + 0.9, CANNON.getZ() + 0.5);
        for (double a : new double[] {-ARC, ARC}) {
            double r = Math.toRadians(a);
            scene.overlay().showLine(PonderPalette.RED, muzzle, muzzle.add(4.0 * Math.cos(r), 0, 4.0 * Math.sin(r)), 80);
        }
        SceneKit.arrow(scene, PonderPalette.RED, muzzle, muzzle.add(3.0, 0, 0), 80);
        say(scene.overlay().showText(75), Orders.AT_WILL)
                .colored(PonderPalette.RED)
                .pointAt(muzzle.add(2.0, 0, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // release crew
        order(scene, WhistleOrder.RELEASE);
        SceneKit.once(scene, s -> SceneSync.stationPose(gunner.get(), null));
        SceneKit.walk(scene, gunner, gunSpot, new Vec3(6.5, 4.0, 5.5), 15);
        SceneKit.walk(scene, recruit, winchSpot, new Vec3(4.5, 4.0, 5.5), 15);
        say(scene.overlay().showText(60), Orders.RELEASE)
                .pointAt(new Vec3(5.5, 5.8, 5.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // the ship screen at the helm
        scene.overlay().showControls(util.vector().topOf(HELM), Pointing.DOWN, 40).rightClick().whileSneaking();
        say(scene.overlay().showText(60), Orders.SCREEN)
                .pointAt(util.vector().topOf(HELM))
                .placeNearTarget();
        scene.idle(65);
        scene.markAsFinished();
    }

    /** A whistle order picked from the wheel: the whistle in the air, then the order's own icon. */
    private static void order(SceneBuilder scene, WhistleOrder order) {
        showIcon(scene.overlay().showControls(WHEEL.add(0, -WHEEL_RADIUS, 0), Pointing.DOWN, 25).rightClick(), order.icon());
        scene.idle(10);
    }

    /**
     * An order's wheel icon in an input window (ART19): an item as the window's item; a GUI sprite as its icon, which
     * Ponder draws at 1.5x like an item (it replaces a click icon, so a sprite order shows no mouse button).
     */
    private static void showIcon(InputElementBuilder window, WhistleOrder.Icon icon) {
        if (icon.isSprite()) {
            window.showing((g, x, y) -> g.blitSprite(icon.id(), x, y, 16, 16));
        } else {
            window.withItem(new ItemStack(BuiltInRegistries.ITEM.get(icon.id())));
        }
    }

    private static CrewMember crew(net.minecraft.world.level.Level level, Vec3 at, float yaw, AtomicReference<CrewMember> ref) {
        CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
        SceneKit.place(c, at, yaw);
        ref.set(c);
        return c;
    }

    /** Turns {@code e} standing at {@code at} toward {@code target}. */
    private static void face(net.minecraft.world.entity.Entity e, Vec3 at, Vec3 target) {
        if (e != null) {
            SceneKit.place(e, at, SceneKit.yaw(target.x - at.x, target.z - at.z));
        }
    }
}
