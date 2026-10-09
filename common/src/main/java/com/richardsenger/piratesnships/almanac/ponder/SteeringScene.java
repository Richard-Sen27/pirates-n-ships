package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Steering;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.sailing.helm.HelmBlockEntity;
import com.richardsenger.piratesnships.sailing.helm.WheelMath;
import com.richardsenger.piratesnships.station.StationContent;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Steering at the helm" (PND1, docs/almanac.md "Scenes"; sailing/helm and the helmsman's course of station/helm,
 * design.md §5.3), on the schematic {@code assets/pirates_n_ships/ponder/steering.nbt}
 * ({@code art/structures/ponder/steering.py}: a small boat x 4..6, z 4..9 lying on the sea, bow north, the helm at
 * [5, 4, 8] facing south).
 *
 * <p>The wheel turns through the helm's real block entity ({@link HelmBlockEntity#setWheel}, the angle its renderer
 * draws), and the rudder line shows {@link WheelMath#rudderAngle} of that wheel with the default locks (1.5 turns lock
 * to lock, 35°). A crew member takes the helm for a course, then the boat sails part of its turning circle to
 * starboard: Ponder turns the boat's section about the circle's centre (not to scale: the real circle is three to four
 * ship lengths across). About 36 seconds.
 */
final class SteeringScene {

    /** The default lock of the wheel ({@code helm.wheel.turns_lock_to_lock} 1.5) and the largest rudder angle (35°). */
    static final double LOCK = WheelMath.lockAngle(1.5);
    static final double MAX_RUDDER = 35.0;

    private SteeringScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.STEERING, Steering.TITLE);
        scene.configureBasePlate(0, 0, 11);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos helm = util.grid().at(5, 4, 8);
        Selection boat = util.select().fromTo(4, 2, 4, 6, 4, 9);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> boatLink = scene.world().showIndependentSection(boat, Direction.DOWN);
        scene.idle(20);

        // take the wheel
        Vec3 wheelTop = util.vector().topOf(helm);
        scene.overlay().showControls(wheelTop, Pointing.DOWN, 50).rightClick();
        say(scene.overlay().showText(55), Steering.WHEEL)
                .pointAt(util.vector().centerOf(helm))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // turn it hard to starboard
        turnWheel(scene, helm, 0, LOCK, 30);
        say(scene.overlay().showText(60), Steering.TURN)
                .pointAt(util.vector().centerOf(helm))
                .placeNearTarget();
        scene.idle(65);

        // the rudder follows
        Vec3 stern = util.vector().of(5.5, 2.4, 10.0);
        scene.overlay().showLine(PonderPalette.WHITE, stern, rudderTip(stern, 0), 120);
        scene.overlay().showBigLine(PonderPalette.RED, stern, rudderTip(stern, WheelMath.rudderAngle(LOCK, LOCK, MAX_RUDDER)), 120);
        say(scene.overlay().showText(60), Steering.RUDDER)
                .colored(PonderPalette.RED)
                .pointAt(stern)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(55), Steering.LET_GO)
                .pointAt(util.vector().centerOf(helm))
                .placeNearTarget();
        scene.idle(60);
        say(scene.overlay().showText(55), Steering.WAY)
                .pointAt(stern)
                .placeNearTarget();
        scene.idle(60);
        turnWheel(scene, helm, LOCK, 0, 20);
        scene.idle(20);

        // a helmsman holds a course
        Vec3 spot = util.vector().of(5.5, 4.0, 9.45);
        ElementLink<EntityElement> sailor = scene.world().createEntity(level -> {
            CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
            SceneKit.place(c, spot, SceneKit.yaw(0, -1));
            return c;
        });
        scene.idle(10);
        scene.overlay().showControls(spot.add(0, 2.2, 0), Pointing.DOWN, 25).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        scene.idle(25);
        scene.overlay().showControls(wheelTop, Pointing.DOWN, 25).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        say(scene.overlay().showText(60), Steering.HELMSMAN)
                .pointAt(spot.add(0, 1.8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        Vec3 mark = util.vector().of(1.5, 2.05, 0.5);
        cross(scene, PonderPalette.GREEN, mark, 90);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().of(5.5, 2.05, 4.0), mark, 90);
        turnWheel(scene, helm, 0, -LOCK / 3, 20);
        say(scene.overlay().showText(75), Steering.COURSE)
                .colored(PonderPalette.GREEN)
                .pointAt(mark)
                .placeNearTarget();
        scene.idle(80);
        turnWheel(scene, helm, -LOCK / 3, 0, 15);
        say(scene.overlay().showText(55), Steering.TAKE_OVER)
                .pointAt(util.vector().centerOf(helm))
                .placeNearTarget();
        scene.idle(60);
        scene.world().modifyEntity(sailor, Entity::discard);
        scene.idle(5);

        // hard over: the turning circle
        turnWheel(scene, helm, 0, LOCK, 15);
        scene.idle(15);
        Vec3 centre = util.vector().of(8.5, 2.05, 7.0);
        SceneKit.path(scene, PonderPalette.WHITE, SceneKit.arc(centre, 3.0, 180, 320, 14), 110);
        scene.world().configureCenterOfRotation(boatLink, util.vector().of(8.5, 2.5, 7.0));
        scene.world().rotateSection(boatLink, 0, -60, 0, 100);
        say(scene.overlay().showText(100), Steering.CIRCLE)
                .pointAt(util.vector().of(5.5, 2.05, 7.0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(105);
        scene.markAsFinished();
    }

    /** The rudder's trailing edge seen from above: aft (+z) from the stern post, swung to starboard (+x) by {@code deg}. */
    static Vec3 rudderTip(Vec3 stern, double deg) {
        double a = Math.toRadians(deg);
        return stern.add(1.4 * Math.sin(a), 0, 1.4 * Math.cos(a));
    }

    /** The wheel turned from {@code from} to {@code to} degrees over {@code ticks}, through the helm's block entity. */
    private static void turnWheel(SceneBuilder scene, BlockPos helm, double from, double to, int ticks) {
        SceneKit.everyTick(scene, ticks, (s, t, total) -> {
            if (s.getWorld().getBlockEntity(helm) instanceof HelmBlockEntity be) {
                be.setWheel(from + (to - from) * (t + 1) / total);
            }
        });
    }

    /** A small flat cross on the water, a course mark. */
    private static void cross(SceneBuilder scene, PonderPalette color, Vec3 at, int ticks) {
        scene.overlay().showBigLine(color, at.add(-0.4, 0, -0.4), at.add(0.4, 0, 0.4), ticks);
        scene.overlay().showBigLine(color, at.add(-0.4, 0, 0.4), at.add(0.4, 0, -0.4), ticks);
    }
}
