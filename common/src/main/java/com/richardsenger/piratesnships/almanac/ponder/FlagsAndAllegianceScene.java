package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Flags;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Flags and allegiance" (PND2, docs/almanac.md "Scenes"; the flagpole of ship/decor/flag and the flag law of
 * law/flag, design.md §4.7), on the schematic {@code assets/pirates_n_ships/ponder/flags_and_allegiance.nbt}
 * ({@code art/structures/ponder/flags_and_allegiance.py}: open sea as the base plate, our ship x 1..4 with a bare
 * two-block pole in a flag pedestal at [2, 3, 8], its head at [2, 5, 8], a navy patrol x 8..11 with her pole head at
 * [10, 4, 8]).
 *
 * <p>Every flag change is the real {@code FlagpoleMachine} at the pole head (hoist, swap, strike, a banner as a custom
 * flag), written into its block entity so the game's cloth renderer draws it and runs it up or down the pole over the
 * default 3 s ({@link SceneFlags}). What the navy does is shown, not simulated: a soldier aiming while we fly the Jolly
 * Roger, lowering his musket when we fly navy colours or strike. Every click goes to the pedestal, which works the pole
 * as the pole does. The pole on the crow's nest is in the crow's nest scene. About 39 seconds.
 */
final class FlagsAndAllegianceScene {

    /** Our pole's flag pedestal (FLG3, the pole's foot: clicks on it work the pole) and the pole's head. */
    static final BlockPos PEDESTAL = new BlockPos(2, 3, 8);
    static final BlockPos OUR_HEAD = new BlockPos(2, 5, 8);
    static final BlockPos NAVY_HEAD = new BlockPos(10, 4, 8);
    /** Default {@code flags.hoist_delay_ticks}. */
    static final int HOIST_TICKS = 60;

    private FlagsAndAllegianceScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.FLAGS_AND_ALLEGIANCE, Flags.TITLE);
        scene.configureBasePlate(0, 0, 13);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(10);

        Selection navy = util.select().fromTo(8, 1, 3, 11, 4, 9);
        scene.world().showSection(util.select().layersFrom(1).substract(navy), Direction.DOWN);
        scene.idle(15);

        Selection pole = util.select().fromTo(2, 4, 8, 2, 5, 8);
        Vec3 poleTop = util.vector().topOf(OUR_HEAD);
        scene.overlay().showOutline(PonderPalette.WHITE, "pole", pole, 55);
        say(scene.overlay().showText(50), Flags.POLE)
                .pointAt(poleTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);
        scene.overlay().showOutline(PonderPalette.GREEN, "pedestal", util.select().position(PEDESTAL), 50);
        say(scene.overlay().showText(50), Flags.PEDESTAL)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(PEDESTAL))
                .placeNearTarget();
        scene.idle(55);

        // the Jolly Roger
        scene.overlay().showControls(util.vector().centerOf(2, 3, 8), Pointing.LEFT, 30).rightClick()
                .withItem(SceneKit.item("jolly_roger_flag"));
        scene.idle(5);
        SceneFlags.hoist(scene, OUR_HEAD, FlagKind.JOLLY_ROGER, HOIST_TICKS);
        say(scene.overlay().showText(60), Flags.HOIST)
                .pointAt(poleTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(60), Flags.COLOURS)
                .pointAt(poleTop)
                .placeNearTarget();
        scene.idle(65);

        // a navy patrol
        scene.world().showSection(navy, Direction.DOWN);
        SceneFlags.fly(scene, NAVY_HEAD, FlagKind.NAVY);
        Vec3 soldierAt = util.vector().of(9.5, 3.0, 5.5);
        AtomicReference<NavySoldier> soldier = new AtomicReference<>();
        scene.world().createEntity(level -> {
            NavySoldier n = new NavySoldier(MobContent.NAVY_SOLDIER.get(), level);
            n.setItemSlot(EquipmentSlot.MAINHAND, SceneKit.item("musket"));
            SceneKit.place(n, soldierAt, SceneKit.yaw(-1, 0));
            soldier.set(n);
            return n;
        });
        scene.idle(15);
        SceneKit.once(scene, s -> SceneSync.aiming(soldier.get(), true));
        scene.overlay().showBigLine(PonderPalette.RED, soldierAt.add(-0.3, 1.5, 0), util.vector().of(3.5, 3.8, 6.0), 70);
        say(scene.overlay().showText(70), Flags.JOLLY)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().of(3.5, 3.8, 6.0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);

        // false colours
        scene.overlay().showControls(util.vector().centerOf(2, 3, 8), Pointing.LEFT, 30).rightClick()
                .withItem(SceneKit.item("navy_flag"));
        scene.idle(5);
        SceneFlags.hoist(scene, OUR_HEAD, FlagKind.NAVY, HOIST_TICKS);
        SceneKit.once(scene, s -> SceneSync.aiming(soldier.get(), false));
        say(scene.overlay().showText(70), Flags.FALSE)
                .pointAt(poleTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);
        scene.overlay().showOutline(PonderPalette.RED, "caught", util.select().fromTo(1, 1, 3, 4, 2, 9), 60);
        say(scene.overlay().showText(60), Flags.CAUGHT)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().of(2.5, 3.0, 4.5))
                .placeNearTarget();
        scene.idle(65);
        say(scene.overlay().showText(55), Flags.NEST)
                .pointAt(util.vector().topOf(NAVY_HEAD))
                .placeNearTarget();
        scene.idle(60);

        // striking the colours
        scene.overlay().showControls(util.vector().centerOf(2, 3, 8), Pointing.LEFT, 30).rightClick();
        scene.idle(5);
        SceneFlags.toggle(scene, OUR_HEAD, HOIST_TICKS);
        say(scene.overlay().showText(65), Flags.STRIKE)
                .pointAt(poleTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(60), Flags.STRUCK)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().of(3.0, 3.0, 4.5))
                .placeNearTarget();
        scene.idle(65);

        // a banner as a custom flag
        scene.overlay().showControls(util.vector().centerOf(2, 3, 8), Pointing.LEFT, 30).rightClick()
                .withItem(new ItemStack(Items.BLUE_BANNER));
        scene.idle(5);
        SceneFlags.hoistBanner(scene, OUR_HEAD, HOIST_TICKS);
        say(scene.overlay().showText(65), Flags.BANNER)
                .pointAt(poleTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);
        scene.markAsFinished();
    }
}
