package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Swivel;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlock;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.SwivelRules;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.station.StationContent;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "The swivel gun" (PND2, docs/almanac.md "Scenes"; combat/cannon {@link SwivelGunBlock}, design.md §8.2), on the
 * schematic {@code assets/pirates_n_ships/ponder/swivel_gun.nbt} ({@code art/structures/ponder/swivel_gun.py}: a fence
 * rail along the bow side, the gun on its middle post at [3, 2, 0], powder at [1, 1, 2] and shot at [5, 1, 2]).
 *
 * <p>The gun loads through its real {@link SwivelGunBlock#LOAD} states and turns through its block entity's aim
 * ({@link SwivelGunBlockEntity#setAim}, the yaw and elevation its renderer draws), within the default limits of 30°
 * down and 45° up; the flash and the shot leave from {@link SwivelRules#muzzle} along {@link SwivelRules#direction}.
 * About 28 seconds.
 */
final class SwivelGunScene {

    static final BlockPos GUN = new BlockPos(3, 2, 0);
    /** Default {@code cannons.swivel.min_elevation_degrees} and {@code max_elevation_degrees}. */
    static final double MIN_ELEVATION = -30.0;
    static final double MAX_ELEVATION = 45.0;
    /** The aim the sweep ends on, which the shot leaves along: a little to starboard of the bow, raised. */
    static final double FIRE_YAW = 200.0;
    static final double FIRE_ELEVATION = 10.0;
    /** The sweep while use is held: (yaw, elevation) waypoints, yaw 180 = the bow (north). */
    static final double[][] SWEEP = {{180, 0}, {235, 25}, {140, -20}, {FIRE_YAW, FIRE_ELEVATION}};

    private SwivelGunScene() {
    }

    /** The muzzle for the firing aim. */
    static Vec3 muzzle() {
        return SwivelRules.muzzle(GUN.getX(), GUN.getY(), GUN.getZ(), FIRE_YAW, FIRE_ELEVATION);
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.SWIVEL_GUN, Swivel.TITLE);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        aim(scene, SWEEP[0][0], SWEEP[0][1]);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(15);

        Vec3 gunTop = util.vector().topOf(GUN);
        scene.overlay().showOutline(PonderPalette.GREEN, "mount", util.select().fromTo(3, 1, 0, 3, 2, 0), 60);
        say(scene.overlay().showText(60), Swivel.MOUNT)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(3, 1, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // loading: powder, then the shot
        scene.overlay().showControls(gunTop, Pointing.DOWN, 30).rightClick().withItem(new ItemStack(Items.GUNPOWDER));
        scene.idle(8);
        load(scene, CannonLoad.POWDER);
        say(scene.overlay().showText(50), Swivel.POWDER)
                .pointAt(gunTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);
        scene.overlay().showControls(gunTop, Pointing.DOWN, 30).rightClick().withItem(SceneKit.item("cannonball"));
        scene.idle(8);
        load(scene, CannonLoad.LOADED);
        say(scene.overlay().showText(50), Swivel.SHOT)
                .pointAt(gunTop)
                .placeNearTarget();
        scene.idle(55);

        // hold use: the gun follows the look
        scene.overlay().showControls(gunTop, Pointing.DOWN, 75).rightClick();
        int leg = 22;
        for (int i = 1; i < SWEEP.length; i++) {
            double[] a = SWEEP[i - 1];
            double[] b = SWEEP[i];
            SceneKit.everyTick(scene, leg, (s, t, total) -> {
                double f = (t + 1) / (double) total;
                setAim(s.getWorld().getBlockEntity(GUN), a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f);
            });
            if (i == 1) {
                say(scene.overlay().showText(70), Swivel.AIM)
                        .pointAt(gunTop)
                        .placeNearTarget()
                        .attachKeyFrame();
            }
            scene.idle(leg);
        }
        scene.idle(10);

        // let go: it fires
        fire(scene, util);
        say(scene.overlay().showText(50), Swivel.FIRE)
                .pointAt(muzzle())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);
        say(scene.overlay().showText(65), Swivel.WEAKER)
                .pointAt(gunTop)
                .placeNearTarget();
        scene.idle(70);

        // no chain shot or grapeshot
        scene.overlay().showControls(gunTop, Pointing.DOWN, 25).rightClick().withItem(SceneKit.item("chain_shot"));
        scene.idle(28);
        scene.overlay().showControls(gunTop, Pointing.DOWN, 25).rightClick().withItem(SceneKit.item("grapeshot"));
        scene.overlay().showOutline(PonderPalette.RED, "gun", util.select().position(GUN), 55);
        say(scene.overlay().showText(60), Swivel.AMMO)
                .colored(PonderPalette.RED)
                .pointAt(gunTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // a hand at the gun
        Vec3 spot = util.vector().of(3.5, 1.0, 1.5);
        AtomicReference<CrewMember> hand = new AtomicReference<>();
        scene.world().createEntity(level -> {
            CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
            SceneKit.place(c, spot, SceneKit.yaw(0, -1));
            hand.set(c);
            return c;
        });
        load(scene, CannonLoad.LOADED);
        scene.idle(10);
        scene.overlay().showControls(spot.add(0, 2.2, 0), Pointing.DOWN, 30).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        say(scene.overlay().showText(60), Swivel.CREW)
                .pointAt(spot.add(0, 1.8, 0))
                .placeNearTarget();
        scene.idle(30);
        fire(scene, util);
        scene.idle(35);
        scene.markAsFinished();
    }

    private static void aim(SceneBuilder scene, double yaw, double elevation) {
        SceneKit.once(scene, s -> setAim(s.getWorld().getBlockEntity(GUN), yaw, elevation));
    }

    private static void setAim(net.minecraft.world.level.block.entity.BlockEntity be, double yaw, double elevation) {
        if (be instanceof SwivelGunBlockEntity gun) {
            gun.setAim(new SwivelRules.Aim(yaw, SwivelRules.clampElevation(elevation, MIN_ELEVATION, MAX_ELEVATION)));
        }
    }

    private static void load(SceneBuilder scene, CannonLoad load) {
        scene.world().modifyBlock(GUN, s -> s.hasProperty(SwivelGunBlock.LOAD) ? s.setValue(SwivelGunBlock.LOAD, load) : s, false);
    }

    /** The shot at the firing aim: the barrel empties, flash and smoke at the muzzle, the ball leaves along the barrel. */
    private static void fire(SceneBuilder scene, SceneBuildingUtil util) {
        load(scene, CannonLoad.EMPTY);
        Vec3 muzzle = muzzle();
        Vec3 dir = SwivelRules.direction(FIRE_YAW, FIRE_ELEVATION);
        ParticleEmitter flash = scene.effects().simpleParticleEmitter(ParticleTypes.FLASH, Vec3.ZERO);
        ParticleEmitter smoke = scene.effects().simpleParticleEmitter(ParticleTypes.LARGE_SMOKE, dir.scale(0.05));
        ParticleEmitter puff = scene.effects().simpleParticleEmitter(ParticleTypes.POOF, dir.scale(0.1));
        scene.effects().emitParticles(muzzle, flash, 1, 1);
        scene.effects().emitParticles(muzzle, puff, 10, 1);
        scene.effects().emitParticles(muzzle, smoke, 4, 4);
        scene.world().createItemEntity(muzzle, dir.scale(0.6), SceneKit.item("cannonball"));
    }
}
