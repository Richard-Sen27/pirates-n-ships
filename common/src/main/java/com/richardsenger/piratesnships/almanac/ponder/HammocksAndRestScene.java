package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Rest;
import com.richardsenger.piratesnships.crew.hammock.HammockSeat;
import com.richardsenger.piratesnships.crew.hammock.SleepAxis;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.station.StationContent;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Hammocks and rest" (PND2, docs/almanac.md "Scenes"; crew/hammock and the morale rule of crew/morale, the sea cot of
 * ship/decor, design.md §7.1), on the schematic {@code assets/pirates_n_ships/ponder/hammocks_and_rest.nbt}
 * ({@code art/structures/ponder/hammocks_and_rest.py}: two hammocks facing east between fence posts, feet at [2, 1, 2]
 * and [2, 1, 4], and a sea cot at [2, 1, 6]).
 *
 * <p>A sleeper lies where the hammock seat puts him ({@link HammockSeat#spot}: on the canvas where foot and head meet),
 * turned along the hammock by {@link SleepAxis#yaw}, in the sleep pose the server syncs. Night and dawn are shown by
 * a clock, not by the sky (Ponder has no time of day). About 27 seconds.
 */
final class HammocksAndRestScene {

    static final List<BlockPos> FEET = List.of(new BlockPos(2, 1, 2), new BlockPos(2, 1, 4));
    static final BlockPos COT = new BlockPos(2, 1, 6);
    static final Direction FACING = Direction.EAST;

    private HammocksAndRestScene() {
    }

    /** Where a crew member lies in the hammock whose foot is at {@code foot}. */
    static Vec3 bunk(BlockPos foot) {
        return HammockSeat.spot(foot, FACING);
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.HAMMOCKS_AND_REST, Rest.TITLE);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(1, 1, 2, 1, 1, 4).add(util.select().fromTo(4, 1, 2, 4, 1, 4)),
                Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 3, 1, 4), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "posts", util.select().position(1, 1, 2)
                .add(util.select().position(4, 1, 2)), 60);
        say(scene.overlay().showText(60), Rest.HANG)
                .pointAt(util.vector().topOf(1, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(55), Rest.BUNKS)
                .pointAt(util.vector().topOf(2, 1, 4))
                .placeNearTarget();
        scene.idle(60);

        // nightfall: free hands turn in
        List<AtomicReference<CrewMember>> hands = List.of(new AtomicReference<>(), new AtomicReference<>());
        for (int i = 0; i < FEET.size(); i++) {
            AtomicReference<CrewMember> ref = hands.get(i);
            Vec3 start = util.vector().of(5.5, 1.0, 2.5 + 2 * i);
            scene.world().createEntity(level -> {
                CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
                SceneKit.place(c, start, SceneKit.yaw(-1, 0));
                ref.set(c);
                return c;
            });
        }
        scene.idle(10);
        scene.overlay().showControls(util.vector().of(3.0, 2.6, 0.5), Pointing.DOWN, 50).withItem(new ItemStack(Items.CLOCK));
        say(scene.overlay().showText(65), Rest.NIGHTFALL)
                .pointAt(util.vector().topOf(3, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        for (int i = 0; i < FEET.size(); i++) {
            AtomicReference<CrewMember> ref = hands.get(i);
            SceneKit.walk(scene, ref, util.vector().of(5.5, 1.0, 2.5 + 2 * i), util.vector().of(4.6, 1.0, 3.5 + 2 * i - 1.0 + 0.1), 15);
        }
        scene.idle(16);
        for (int i = 0; i < FEET.size(); i++) {
            AtomicReference<CrewMember> ref = hands.get(i);
            BlockPos foot = FEET.get(i);
            SceneKit.once(scene, s -> {
                CrewMember c = ref.get();
                if (c != null) {
                    SceneKit.place(c, bunk(foot), SleepAxis.yaw(FACING, null));
                    SceneSync.resting(c, true);
                }
            });
            SceneKit.focus(scene, bunk(foot).add(0, 0.3, 0));
        }
        scene.idle(55);

        // dawn: up again, and the night settles their morale
        scene.overlay().showControls(util.vector().of(3.0, 2.6, 0.5), Pointing.DOWN, 50).withItem(new ItemStack(Items.CLOCK));
        scene.idle(10);
        for (int i = 0; i < FEET.size(); i++) {
            AtomicReference<CrewMember> ref = hands.get(i);
            Vec3 up = util.vector().of(4.6, 1.0, 2.6 + 2 * i);
            SceneKit.once(scene, s -> {
                CrewMember c = ref.get();
                if (c != null) {
                    SceneSync.resting(c, false);
                    SceneKit.place(c, up, SceneKit.yaw(1, 0));
                }
            });
        }
        say(scene.overlay().showText(70), Rest.DAWN)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().of(4.6, 2.9, 2.6))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);
        say(scene.overlay().showText(60), Rest.DUTY)
                .pointAt(util.vector().of(4.6, 2.9, 4.6))
                .placeNearTarget();
        scene.idle(65);
        hands.forEach(ref -> SceneKit.discard(scene, ref));

        // you sleep there too, and in the sea cot
        scene.overlay().showControls(util.vector().topOf(3, 1, 2), Pointing.DOWN, 40).rightClick();
        say(scene.overlay().showText(60), Rest.PLAYER)
                .pointAt(util.vector().topOf(3, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        scene.world().showSection(util.select().fromTo(2, 1, 6, 3, 1, 6), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(COT.east()), Pointing.DOWN, 40).rightClick();
        say(scene.overlay().showText(60), Rest.COT)
                .pointAt(util.vector().topOf(COT))
                .placeNearTarget();
        scene.idle(65);
        scene.markAsFinished();
    }
}
