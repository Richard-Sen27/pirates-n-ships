package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CannonLoadingText.Line;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.ShotKind;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * "Loading and firing the cannon" (ALM4, docs/almanac.md "Scenes"; the cannon of design.md §8.2), on the schematic
 * {@code assets/pirates_n_ships/ponder/cannon_loading.nbt} ({@code art/structures/ponder/cannon_loading.py}: a 7x7 deck
 * patch, the bulwark on the north edge, the cannon's master at [3, 1, 1] facing north through the gun port, its rear at
 * [3, 1, 2], the powder barrel at [1, 1, 2] and the shot chest at [5, 1, 2]).
 *
 * <p>The cannon steps through its real block states ({@link CannonBlock#LOAD}: empty, powder, loaded, empty again
 * after the shot; {@link CannonBlock#SHOT} ball), so the carriage model shows the rammer once powder is in, exactly as
 * in the game; the aim step tilts the barrel through the block entity's elevation step. The muzzle flash and smoke
 * sit where {@link CannonRules#muzzle} puts the muzzle. About 30 seconds (600 ticks).
 */
final class CannonLoadingScene {

    private CannonLoadingScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CannonLoadingText.SCENE, CannonLoadingText.TITLE);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(15);

        BlockPos master = util.grid().at(3, 1, 1);
        Selection gun = util.select().fromTo(3, 1, 1, 3, 1, 2);
        Vec3 gunTop = util.vector().topOf(master);
        Vec3 muzzle = CannonRules.muzzle(master.getX(), master.getY(), master.getZ(),
                CannonRules.muzzleDirection(0, -1, 0));

        // the gun
        say(scene.overlay().showOutlineWithText(gun, 70), Line.GUN)
                .colored(PonderPalette.WHITE)
                .pointAt(gunTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // the order
        say(scene.overlay().showText(60), Line.ORDER)
                .independent(10);
        scene.idle(70);

        // powder
        scene.overlay().showControls(gunTop, Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.GUNPOWDER));
        scene.idle(10);
        scene.world().modifyBlock(master, s -> s.setValue(CannonBlock.LOAD, CannonLoad.POWDER), false);
        say(scene.overlay().showText(60), Line.POWDER)
                .pointAt(gunTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // shot
        scene.overlay().showControls(gunTop, Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(CombatContent.CANNONBALL.get()));
        scene.idle(10);
        scene.world().modifyBlock(master, s -> s.setValue(CannonBlock.LOAD, CannonLoad.LOADED)
                .setValue(CannonBlock.SHOT, ShotKind.BALL), false);
        say(scene.overlay().showText(60), Line.BALL)
                .pointAt(gunTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // rammed home: the carriage model already shows the rammer since the powder went in
        say(scene.overlay().showOutlineWithText(gun, 60), Line.RAM)
                .colored(PonderPalette.GREEN)
                .pointAt(gunTop)
                .placeNearTarget();
        scene.idle(60);

        // aim: one step up and back to level
        scene.overlay().showControls(gunTop, Pointing.DOWN, 40).rightClick().whileSneaking();
        scene.idle(10);
        stepElevation(scene, master, true);
        say(scene.overlay().showText(60), Line.AIM)
                .pointAt(gunTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(45);
        stepElevation(scene, master, false);
        scene.idle(15);

        // fire
        scene.overlay().showControls(gunTop, Pointing.DOWN, 30).rightClick();
        scene.idle(15);
        fire(scene, util, master, muzzle);
        say(scene.overlay().showText(60), Line.FIRE)
                .pointAt(muzzle)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // recoil: the push goes against the barrel, onto the ship
        Vec3 breech = util.vector().centerOf(3, 1, 2);
        scene.overlay().showBigLine(PonderPalette.RED, breech, breech.add(0, 0, 2.5), 60);
        say(scene.overlay().showText(60), Line.RECOIL)
                .colored(PonderPalette.RED)
                .pointAt(breech.add(0, 0, 2.5))
                .placeNearTarget();
        scene.idle(60);
        scene.markAsFinished();
    }

    /** Sets the window's text to {@code line} (Ponder shared text: explicit lang keys, see {@link CannonLoadingText}). */
    private static TextElementBuilder say(TextElementBuilder window, Line line) {
        return window.sharedText(line.key());
    }

    /** One elevation step up or down on the gun's block entity (the renderer eases the barrel to it). */
    private static void stepElevation(SceneBuilder scene, BlockPos master, boolean up) {
        scene.world().modifyBlockEntity(master, CannonBlockEntity.class, be -> be.setElevationStep(
                CannonRules.stepElevation(be.elevationStep(), CannonConfig.ELEVATION_STEPS.get(), up)));
    }

    /** The shot: the barrel empties, flash, flame and smoke at the muzzle, and the ball leaves over the bow. */
    private static void fire(SceneBuilder scene, SceneBuildingUtil util, BlockPos master, Vec3 muzzle) {
        scene.world().modifyBlock(master, s -> s.setValue(CannonBlock.LOAD, CannonLoad.EMPTY)
                .setValue(CannonBlock.SHOT, ShotKind.BALL), false);
        ParticleEmitter flash = scene.effects().simpleParticleEmitter(ParticleTypes.FLASH, Vec3.ZERO);
        ParticleEmitter flame = scene.effects().simpleParticleEmitter(ParticleTypes.FLAME, util.vector().of(0, 0.02, -0.08));
        ParticleEmitter blast = scene.effects().simpleParticleEmitter(ParticleTypes.EXPLOSION, Vec3.ZERO);
        ParticleEmitter smoke = scene.effects().particleEmitterWithinBlockSpace(ParticleTypes.LARGE_SMOKE,
                util.vector().of(0, 0.04, -0.03));
        ParticleEmitter puff = scene.effects().simpleParticleEmitter(ParticleTypes.POOF, util.vector().of(0, 0.01, -0.12));
        scene.effects().emitParticles(muzzle, flash, 1, 1);
        scene.effects().emitParticles(muzzle, flame, 12, 1);
        scene.effects().emitParticles(muzzle.add(0, 0, -0.5), blast, 1, 1);
        scene.effects().emitParticles(muzzle, puff, 16, 1);
        scene.effects().emitParticles(muzzle, smoke, 6, 6);
        scene.world().createItemEntity(muzzle, util.vector().of(0, 0.08, -0.6),
                new ItemStack(CombatContent.CANNONBALL.get()));
    }
}
