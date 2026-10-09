package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.CrewSceneText.Galley;
import com.richardsenger.piratesnships.crew.galley.MealRules;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlock;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelRules;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
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
 * "The galley and provisions" (PND2, docs/almanac.md "Scenes"; crew/galley, the provision rules of crew/provisions
 * and the dawn upkeep of crew/upkeep, design.md §7.3, §7.4), on the schematic
 * {@code assets/pirates_n_ships/ponder/galley_and_provisions.nbt} ({@code art/structures/ponder/galley_and_provisions.py}:
 * the pantry at [5, 1, 3] facing west against a bulkhead on the east edge, the far side from the default camera
 * (PND3), an empty water barrel at [5, 1, 5]).
 *
 * <p>The barrel fills through its real fill levels ({@link WaterBarrelRules#fillLevel} for the rations two water
 * buckets give, of the default 16). The meal seats are {@link MealRules#spotsAround} the pantry in the rule's order
 * (the first free ones), each diner turned to the pantry by {@link MealRules#facingYaw}, showing the eating pose the
 * server syncs. Dawn's rations and the morale rules are told, not simulated. About 31 seconds.
 */
final class GalleyAndProvisionsScene {

    static final BlockPos PANTRY = new BlockPos(5, 1, 3);
    static final BlockPos BARREL = new BlockPos(5, 1, 5);

    private GalleyAndProvisionsScene() {
    }

    /** The meal seats the crew takes: the free spots round the pantry, in {@link MealRules#spotsAround}'s order. */
    static List<BlockPos> mealSpots() {
        return MealRules.spotsAround(PANTRY).stream()
                .filter(p -> p.getX() <= 5 && !p.equals(BARREL))
                .limit(2)
                .toList();
    }

    /** The barrel's fill level after {@code buckets} water buckets (default 3 rations each, 16 to the barrel). */
    static int fillAfterBuckets(int buckets) {
        ProvisionSettings d = ProvisionSettings.DEFAULTS;
        return WaterBarrelRules.fillLevel(buckets * d.waterBucketRations(), WaterBarrelRules.capacity(d));
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(CrewSceneText.GALLEY_AND_PROVISIONS, Galley.TITLE);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(15);

        // the pantry
        Vec3 pantryTop = util.vector().topOf(PANTRY);
        scene.overlay().showControls(util.vector().blockSurface(PANTRY, Direction.WEST), Pointing.RIGHT, 40).rightClick()
                .withItem(new ItemStack(Items.BREAD));
        say(scene.overlay().showText(60), Galley.PANTRY)
                .pointAt(pantryTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // the water barrel, filled by the bucket
        for (int b = 1; b <= 2; b++) {
            int level = fillAfterBuckets(b);
            scene.overlay().showControls(util.vector().topOf(BARREL), Pointing.DOWN, 15).rightClick()
                    .withItem(new ItemStack(Items.WATER_BUCKET));
            scene.idle(8);
            scene.world().modifyBlock(BARREL, s -> s.hasProperty(WaterBarrelBlock.FILL) ? s.setValue(WaterBarrelBlock.FILL, level) : s,
                    false);
            scene.idle(12);
        }
        say(scene.overlay().showText(60), Galley.WATER)
                .pointAt(util.vector().topOf(BARREL))
                .placeNearTarget();
        scene.idle(45);

        // dawn: a day's rations
        scene.world().createItemEntity(pantryTop.add(-0.3, 0.1, 0), util.vector().of(-0.05, 0.2, 0), new ItemStack(Items.BREAD));
        say(scene.overlay().showText(65), Galley.DAWN)
                .pointAt(pantryTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // a meal: the free hands sit down round the pantry
        List<BlockPos> spots = mealSpots();
        Vec3 from = util.vector().of(1.5, 1.0, 3.5);
        List<AtomicReference<CrewMember>> diners = List.of(new AtomicReference<>(), new AtomicReference<>());
        for (int i = 0; i < spots.size(); i++) {
            AtomicReference<CrewMember> ref = diners.get(i);
            Vec3 start = from.add(0, 0, i * 1.5 - 0.75);
            scene.world().createEntity(level -> {
                CrewMember c = new CrewMember(StationContent.CREW_MEMBER.get(), level);
                SceneKit.place(c, start, SceneKit.yaw(1, 0));
                ref.set(c);
                return c;
            });
            SceneKit.walk(scene, ref, start, Vec3.atBottomCenterOf(spots.get(i)), 30);
        }
        scene.idle(31);
        for (int i = 0; i < spots.size(); i++) {
            AtomicReference<CrewMember> ref = diners.get(i);
            BlockPos spot = spots.get(i);
            SceneKit.once(scene, s -> {
                CrewMember c = ref.get();
                if (c != null) {
                    SceneKit.place(c, Vec3.atBottomCenterOf(spot), MealRules.facingYaw(spot, PANTRY, null));
                    SceneSync.eating(c, true);
                }
            });
        }
        spots.forEach(spot -> SceneKit.focus(scene, Vec3.atBottomCenterOf(spot).add(0, 1.0, 0)));
        say(scene.overlay().showText(70), Galley.MEALS)
                .pointAt(pantryTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);
        for (int i = 0; i < spots.size(); i++) {
            AtomicReference<CrewMember> ref = diners.get(i);
            SceneKit.once(scene, s -> SceneSync.eating(ref.get(), false));
            SceneKit.walk(scene, ref, Vec3.atBottomCenterOf(spots.get(i)), from.add(0, 0, i * 1.5 - 0.75), 25);
        }
        scene.idle(10);

        // short rations, rum, scurvy, spoilage
        scene.overlay().showOutline(PonderPalette.RED, "pantry", util.select().position(PANTRY), 60);
        say(scene.overlay().showText(65), Galley.SHORT)
                .colored(PonderPalette.RED)
                .pointAt(pantryTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);
        diners.forEach(ref -> SceneKit.discard(scene, ref));
        scene.overlay().showControls(util.vector().blockSurface(PANTRY, Direction.WEST), Pointing.RIGHT, 40).rightClick()
                .withItem(SceneKit.item("rum"));
        say(scene.overlay().showText(55), Galley.RUM)
                .pointAt(pantryTop)
                .placeNearTarget();
        scene.idle(60);
        scene.overlay().showControls(util.vector().blockSurface(PANTRY, Direction.WEST), Pointing.RIGHT, 50).rightClick()
                .withItem(SceneKit.item("lime"));
        say(scene.overlay().showText(70), Galley.SCURVY)
                .colored(PonderPalette.GREEN)
                .pointAt(pantryTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(75);
        scene.overlay().showControls(pantryTop.add(0, 0.3, 0), Pointing.DOWN, 60).withItem(new ItemStack(Items.ROTTEN_FLESH));
        say(scene.overlay().showText(65), Galley.SPOIL)
                .pointAt(pantryTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);
        scene.markAsFinished();
    }
}
