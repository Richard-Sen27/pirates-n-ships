package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Boarding;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlankBlock;
import com.richardsenger.piratesnships.combat.boarding.PlankRun;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.MeleePose;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Boarding" (PND2, docs/almanac.md "Scenes"; the grappling hook of combat/grapple, the boarding plank of
 * combat/boarding, the swordplay of combat/melee and the capture of worldsim/materialize, design.md §8.3, §8.4), on the
 * schematic {@code assets/pirates_n_ships/ponder/boarding.nbt} ({@code art/structures/ponder/boarding.py}: open sea as
 * the base plate, our ship x 1..4 and a pirate ship at the east edge x 9..12, both z 3..9 with gunwales on y 3, her
 * Jolly Roger at [11, 3, 8]).
 *
 * <p>The hook is the item on a drawn arc (the game's hook entity needs its thrower), the haul moves the pirate ship's
 * section three blocks west (an element transform, not Sable: in the game both hulls move toward each other). The
 * plank is the real run of {@link BoardingPlankBlock} states from {@link PlankRun}'s rule (BRD2) for this gap: two flat cells from
 * the top of our gunwale, the tip on hers. The pirates telegraph their blows through the melee pose the server would
 * sync ({@link SceneSync#melee}); the player is not drawn, only the clicks. About 38 seconds.
 */
final class BoardingScene {

    /** Our gunwale block the plank is laid from, and the run's two cells (rail top to rail top, one block up). */
    static final BlockPos GUNWALE = new BlockPos(4, 3, 6);
    static final List<BlockPos> RUN = List.of(new BlockPos(5, 4, 6), new BlockPos(6, 4, 6));
    /** How far the pirate ship comes in (blocks west). */
    static final int HAUL = 3;
    static final BlockPos THEIR_POLE = new BlockPos(11, 3, 8);

    private BoardingScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.BOARDING, Boarding.TITLE);
        scene.configureBasePlate(0, 0, 13);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(10);

        Selection theirs = util.select().fromTo(9, 1, 3, 12, 4, 9);
        scene.world().showSection(util.select().layersFrom(1).substract(theirs), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> theirLink = scene.world().showIndependentSection(theirs, Direction.DOWN);
        SceneFlags.fly(scene, THEIR_POLE, FlagKind.JOLLY_ROGER);
        scene.idle(15);

        // the hook flies onto her gunwale
        Vec3 deck = util.vector().of(3.5, 4.4, 6.5);
        Vec3 bite = util.vector().of(9.5, 4.05, 6.5);
        scene.overlay().showControls(util.vector().of(3.5, 3.0, 6.5), Pointing.DOWN, 25).rightClick()
                .withItem(SceneKit.item("grappling_hook"));
        scene.idle(10);
        AtomicReference<ItemEntity> hook = new AtomicReference<>();
        scene.world().createEntity(level -> SceneKit.floatingItem(level, deck, SceneKit.item("grappling_hook"), hook));
        SceneKit.fly(scene, hook, deck, bite, 2.0, 16);
        scene.overlay().showLine(PonderPalette.OUTPUT, deck, bite, 50);
        say(scene.overlay().showText(55), Boarding.HOOK)
                .pointAt(bite)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // hauling alongside: she comes in, the hook with her
        Vec3 hauled = bite.add(-HAUL, 0, 0);
        scene.world().moveSection(theirLink, util.vector().of(-HAUL, 0, 0), 60);
        SceneKit.everyTick(scene, 60, (s, t, total) -> {
            ItemEntity e = hook.get();
            if (e != null) {
                Vec3 p = SceneKit.lerp(bite, hauled, (t + 1) / (double) total);
                e.setPos(p.x, p.y, p.z);
            }
        });
        SceneKit.arrow(scene, PonderPalette.WHITE, util.vector().of(10.5, 5.2, 10.5), util.vector().of(7.5, 5.2, 10.5), 60);
        say(scene.overlay().showText(70), Boarding.HAUL)
                .pointAt(util.vector().of(7.5, 4.5, 6.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(62);
        scene.overlay().showLine(PonderPalette.OUTPUT, deck, hauled, 300);
        scene.idle(10);

        // the plank, from the top of our gunwale onto hers
        scene.overlay().showControls(util.vector().topOf(GUNWALE), Pointing.DOWN, 30).rightClick()
                .withItem(SceneKit.item("boarding_plank"));
        scene.idle(10);
        for (int i = 0; i < RUN.size(); i++) {
            int segment = i;
            SceneKit.setLater(scene, util.select().position(RUN.get(i)), () -> plank(segment, RUN.size()), false);
            scene.idle(4);
        }
        say(scene.overlay().showText(60), Boarding.PLANK)
                .pointAt(util.vector().topOf(RUN.get(0)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        scene.overlay().showOutline(PonderPalette.GREEN, "plank", util.select().fromTo(5, 4, 6, 6, 4, 6), 55);
        say(scene.overlay().showText(55), Boarding.LAID)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(RUN.get(1)))
                .placeNearTarget();
        scene.idle(60);

        // her crew turns out
        AtomicReference<Pirate> first = new AtomicReference<>();
        AtomicReference<Pirate> second = new AtomicReference<>();
        Vec3 firstAt = util.vector().of(7.5, 3.0, 5.5);
        Vec3 secondAt = util.vector().of(8.5, 3.0, 7.8);
        scene.world().createEntity(level -> pirate(level, firstAt, first));
        scene.world().createEntity(level -> pirate(level, secondAt, second));
        scene.idle(10);

        // crossing
        SceneKit.path(scene, PonderPalette.WHITE, List.of(util.vector().of(2.5, 3.3, 6.5), util.vector().of(4.5, 4.3, 6.5),
                util.vector().of(6.5, 4.3, 6.5)), 55);
        SceneKit.arrow(scene, PonderPalette.WHITE, util.vector().of(6.5, 4.3, 6.5), util.vector().of(6.9, 3.6, 6.2), 55);
        say(scene.overlay().showText(55), Boarding.CROSS)
                .pointAt(util.vector().of(5.5, 4.3, 6.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // the fight: her men telegraph, you strike
        say(scene.overlay().showText(110), Boarding.FIGHT)
                .colored(PonderPalette.RED)
                .pointAt(firstAt.add(0, 1.9, 0))
                .placeNearTarget()
                .attachKeyFrame();
        duel(scene, first, firstAt, AttackKind.SLASH);
        duel(scene, first, firstAt, AttackKind.THRUST);
        fall(scene, first, firstAt);
        SceneKit.focus(scene, secondAt.add(0, 1.0, 0));
        duel(scene, second, secondAt, AttackKind.SLASH);
        fall(scene, second, secondAt);
        scene.idle(10);

        // the prize
        scene.overlay().showOutline(PonderPalette.GREEN, "prize", theirsAfterHaul(util), 70);
        say(scene.overlay().showText(70), Boarding.PRIZE)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().of(7.5, 3.5, 6.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);
        say(scene.overlay().showText(60), Boarding.LAW)
                .pointAt(util.vector().topOf(THEIR_POLE.west(HAUL)))
                .placeNearTarget();
        scene.idle(65);

        // the hulls part: the plank breaks
        SceneKit.discard(scene, hook);
        scene.world().moveSection(theirLink, util.vector().of(2, 0, 0), 30);
        scene.idle(18);
        SceneKit.setLater(scene, util.select().fromTo(5, 4, 6, 6, 4, 6), Blocks.AIR::defaultBlockState, true);
        scene.world().createItemEntity(util.vector().centerOf(RUN.get(0)), util.vector().of(0, 0.1, 0),
                SceneKit.item("boarding_plank"));
        say(scene.overlay().showText(55), Boarding.PART)
                .pointAt(util.vector().centerOf(RUN.get(0)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);
        scene.markAsFinished();
    }

    /** Segment {@code segment} of a run of {@code length} going east (the block's own rule for the tip). */
    static BlockState plank(int segment, int length) {
        return BoardingContent.PLANK.get().segment(Direction.EAST, segment, length);
    }

    /** Where her hull lies once hauled alongside. */
    private static Selection theirsAfterHaul(SceneBuildingUtil util) {
        return util.select().fromTo(9 - HAUL, 1, 3, 12 - HAUL, 3, 9);
    }

    private static Pirate pirate(net.minecraft.world.level.Level level, Vec3 at, AtomicReference<Pirate> ref) {
        Pirate p = new Pirate(MobContent.PIRATE.get(), level);
        p.setItemSlot(EquipmentSlot.MAINHAND, SceneKit.item("cutlass"));
        SceneKit.place(p, at, SceneKit.yaw(-1, 0));
        ref.set(p);
        return p;
    }

    /** One exchange: the pirate winds up and swings ({@link MeleePose}), you strike back and he flinches. */
    private static void duel(SceneBuilder scene, AtomicReference<Pirate> ref, Vec3 at, AttackKind attack) {
        SceneKit.once(scene, s -> SceneSync.melee(ref.get(), new MeleePose(Phase.WINDUP, attack, false, 8)));
        scene.idle(8);
        SceneKit.once(scene, s -> SceneSync.melee(ref.get(), new MeleePose(Phase.ACTIVE, attack, false, 4)));
        scene.idle(4);
        SceneKit.once(scene, s -> SceneSync.melee(ref.get(), new MeleePose(Phase.RECOVERY, attack, false, 10)));
        scene.overlay().showControls(at.add(0, 2.3, 0), Pointing.DOWN, 12).leftClick().withItem(SceneKit.item("cutlass"));
        scene.idle(6);
        SceneKit.once(scene, s -> {
            if (ref.get() != null) {
                ref.get().hurtDuration = 10;
                ref.get().hurtTime = 10;
            }
        });
        scene.idle(10);
        SceneKit.once(scene, s -> SceneSync.melee(ref.get(), MeleePose.IDLE));
        scene.idle(4);
    }

    /** The last blow: he goes down. */
    private static void fall(SceneBuilder scene, AtomicReference<Pirate> ref, Vec3 at) {
        scene.overlay().showControls(at.add(0, 2.3, 0), Pointing.DOWN, 10).leftClick().withItem(SceneKit.item("cutlass"));
        scene.idle(6);
        ParticleEmitter poof = scene.effects().simpleParticleEmitter(ParticleTypes.POOF, Vec3.ZERO);
        scene.effects().emitParticles(at.add(0, 0.8, 0), poof, 10, 1);
        SceneKit.discard(scene, ref);
        scene.idle(8);
    }
}
