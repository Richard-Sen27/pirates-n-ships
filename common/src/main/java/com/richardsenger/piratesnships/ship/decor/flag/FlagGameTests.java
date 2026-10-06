package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.law.flag.FlagLaw;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Flagpole behavior in a world: hoisting with the delay, changing, striking and raising, taking down, banners as
 * custom flags, breaking, save and reload, the allegiance query, notifications. Timing tests read the configured
 * hoisting delay instead of changing it, so they run in the normal batch.
 */
public final class FlagGameTests {

    private static final int TIMEOUT = 1200;

    private FlagGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FlagGameTests.class);
    }

    // --- helpers ---

    private static int delay() {
        return Math.max(0, FlagConfig.HOIST_DELAY_TICKS.get());
    }

    private static FlagpoleBlockEntity pole(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, ShipDecor.FLAGPOLE.get());
        return helper.getBlockEntity(rel);
    }

    private static FlagpoleBlockEntity be(GameTestHelper helper, BlockPos rel) {
        return helper.getBlockEntity(rel);
    }

    /** Uses the pole the way vanilla does: {@code useItemOn}, then {@code useWithoutItem} if that passes. */
    private static void use(GameTestHelper helper, Player player, BlockPos rel, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = helper.getLevel().getBlockState(abs);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        ItemInteractionResult r = state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        if (r == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) state.useWithoutItem(helper.getLevel(), player, hit);
    }

    private static void assertReading(GameTestHelper helper, BlockPos rel, FlagReading expected) {
        FlagReading actual = FlagAllegiance.at(helper.getLevel(), helper.absolutePos(rel));
        helper.assertTrue(actual.equals(expected), "pole " + rel + " shows " + actual + ", expected " + expected);
        helper.assertBlockProperty(rel, FlagpoleBlock.FLAG, expected.shown());
    }

    private static int count(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** Waits until just after a delayed action completes, checking halfway that it has not completed yet. */
    private static GameTestSequence afterDelay(GameTestSequence seq, Runnable notYet, Runnable done) {
        int d = delay();
        if (d >= 4) seq = seq.thenExecuteAfter(d / 2, notYet).thenExecuteAfter(d - d / 2 + 2, done);
        else seq = seq.thenExecuteAfter(d + 2, done);
        return seq;
    }

    private static ItemStack patternedBanner(GameTestHelper helper) {
        ItemStack banner = new ItemStack(Items.RED_BANNER);
        var skull = helper.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.SKULL);
        var border = helper.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.BORDER);
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(skull, DyeColor.BLACK).add(border, DyeColor.WHITE).build());
        return banner;
    }

    // --- tests ---

    @ModGameTest
    public static void flagItemsAndRecipesExist(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, List.of("merchant_flag", "navy_flag", "jolly_roger_flag"), List.of("flagpole"));
        for (RegistryEntry<Item, Item> flag : Flags.flagItems()) {
            ContentTestSupport.assertRecipe(helper, flag.id().getPath(), flag.get(), 1);
            helper.assertTrue(new ItemStack(flag.get()).is(Flags.FLAGS), flag.id() + " is not in #" + Flags.FLAGS.location());
        }
        helper.assertTrue(new ItemStack(Items.BLUE_BANNER).is(Flags.FLAGS), "banners should be in #" + Flags.FLAGS.location());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = TIMEOUT)
    public static void hoistingEachFlagTakesTheDelay(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<BlockPos> poles = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        List<FlagKind> kinds = List.of(FlagKind.MERCHANT, FlagKind.NAVY, FlagKind.JOLLY_ROGER);
        for (int i = 0; i < 3; i++) {
            pole(helper, poles.get(i));
            use(helper, player, poles.get(i), Flags.stackFor(kinds.get(i)));
            helper.assertTrue(player.getMainHandItem().isEmpty(), "the flag item should be taken by the pole");
        }
        Runnable notYet = () -> poles.forEach(p -> {
            assertReading(helper, p, FlagReading.NO_FLAG);
            helper.assertTrue(be(helper, p).state().pending().isPresent(), "hoist should be pending at " + p);
        });
        if (delay() > 0) notYet.run();
        afterDelay(helper.startSequence(), notYet, () -> {
            for (int i = 0; i < 3; i++) {
                assertReading(helper, poles.get(i), FlagReading.flying(kinds.get(i)));
                helper.assertTrue(be(helper, poles.get(i)).state().hoistedBy().orElseThrow().equals(player.getUUID()), "hoister not stored");
            }
        }).thenSucceed();
    }

    @ModGameTest(timeoutTicks = TIMEOUT)
    public static void changingTheFlagGivesThePreviousOneBack(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        pole(helper, rel);
        use(helper, player, rel, new ItemStack(Flags.MERCHANT_FLAG.get()));
        GameTestSequence seq = afterDelay(helper.startSequence(), () -> { }, () -> {
            assertReading(helper, rel, FlagReading.flying(FlagKind.MERCHANT));
            use(helper, player, rel, new ItemStack(Flags.NAVY_FLAG.get()));
        });
        afterDelay(seq, () -> {
            assertReading(helper, rel, FlagReading.flying(FlagKind.MERCHANT));
            helper.assertTrue(count(player, Flags.MERCHANT_FLAG.get()) == 0, "old flag returned too early");
        }, () -> {
            assertReading(helper, rel, FlagReading.flying(FlagKind.NAVY));
            helper.assertTrue(count(player, Flags.MERCHANT_FLAG.get()) == 1, "the merchant flag should be back in the inventory");
        }).thenSucceed();
    }

    @ModGameTest(timeoutTicks = TIMEOUT)
    public static void strikingAndRaisingTheColors(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        pole(helper, rel).commandSet(FlagKind.JOLLY_ROGER, false, null);
        use(helper, player, rel, ItemStack.EMPTY);
        GameTestSequence seq = afterDelay(helper.startSequence(), () -> assertReading(helper, rel, FlagReading.flying(FlagKind.JOLLY_ROGER)), () -> {
            assertReading(helper, rel, FlagReading.struck(FlagKind.JOLLY_ROGER));
            helper.assertTrue(be(helper, rel).reading().shown() == FlagKind.NONE, "struck colors show no flag");
            use(helper, player, rel, ItemStack.EMPTY);
        });
        afterDelay(seq, () -> assertReading(helper, rel, FlagReading.struck(FlagKind.JOLLY_ROGER)),
                () -> assertReading(helper, rel, FlagReading.flying(FlagKind.JOLLY_ROGER))).thenSucceed();
    }

    @ModGameTest(timeoutTicks = TIMEOUT)
    public static void sneakingTakesTheFlagDown(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        pole(helper, rel).commandSet(FlagKind.NAVY, true, null);
        player.setShiftKeyDown(true);
        use(helper, player, rel, ItemStack.EMPTY);
        afterDelay(helper.startSequence(), () -> assertReading(helper, rel, FlagReading.struck(FlagKind.NAVY)), () -> {
            assertReading(helper, rel, FlagReading.NO_FLAG);
            helper.assertTrue(count(player, Flags.NAVY_FLAG.get()) == 1, "the navy flag should be back in the inventory");
        }).thenSucceed();
    }

    @ModGameTest(timeoutTicks = TIMEOUT)
    public static void anotherActionCancelsAHoist(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        Player a = helper.makeMockPlayer(GameType.SURVIVAL);
        Player b = helper.makeMockPlayer(GameType.SURVIVAL);
        pole(helper, rel);
        use(helper, a, rel, new ItemStack(Flags.JOLLY_ROGER_FLAG.get()));
        if (delay() == 0) {
            helper.succeed(); // nothing can be in progress without a delay
            return;
        }
        use(helper, b, rel, ItemStack.EMPTY);
        helper.assertTrue(be(helper, rel).state().pending().isEmpty(), "the hoist should be cancelled");
        helper.assertTrue(count(a, Flags.JOLLY_ROGER_FLAG.get()) == 1, "the cancelled flag goes back to whoever started the hoist");
        helper.startSequence().thenExecuteAfter(delay() + 2, () -> assertReading(helper, rel, FlagReading.NO_FLAG)).thenSucceed();
    }

    @ModGameTest(timeoutTicks = TIMEOUT)
    public static void bannerIsACustomFlagAndKeepsItsPatterns(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack banner = patternedBanner(helper);
        pole(helper, rel);
        use(helper, player, rel, banner.copy());
        GameTestSequence seq = afterDelay(helper.startSequence(), () -> { }, () -> {
            assertReading(helper, rel, FlagReading.flying(FlagKind.CUSTOM));
            helper.assertTrue(ItemStack.matches(be(helper, rel).state().flagItem(), banner), "the pole should keep the banner with its patterns");
            player.setShiftKeyDown(true);
            use(helper, player, rel, ItemStack.EMPTY);
        });
        afterDelay(seq, () -> { }, () -> {
            assertReading(helper, rel, FlagReading.NO_FLAG);
            ItemStack back = player.getInventory().items.stream().filter(s -> s.is(Items.RED_BANNER)).findFirst().orElse(ItemStack.EMPTY);
            helper.assertTrue(ItemStack.matches(back, banner), "the banner should come back with its patterns, got " + back);
        }).thenSucceed();
    }

    @ModGameTest(batch = "pirates_n_ships_config_ship_decor_banner_flags")
    public static void bannersAreRejectedWhenCustomFlagsAreOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, FlagConfig.CUSTOM_BANNER_FLAGS, false);
        BlockPos rel = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        pole(helper, rel);
        use(helper, player, rel, new ItemStack(Items.WHITE_BANNER));
        helper.assertTrue(be(helper, rel).state().pending().isEmpty() && !be(helper, rel).state().hasFlag(), "banner should not be hoisted");
        helper.assertTrue(player.getMainHandItem().is(Items.WHITE_BANNER), "the banner should stay in hand");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingThePoleDropsPoleAndFlags(GameTestHelper helper) {
        BlockPos rel = new BlockPos(4, 1, 4);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        pole(helper, rel).commandSet(FlagKind.MERCHANT, false, null);
        use(helper, player, rel, new ItemStack(Flags.NAVY_FLAG.get()));
        helper.getLevel().destroyBlock(helper.absolutePos(rel), true);
        helper.assertItemEntityPresent(ShipDecor.FLAGPOLE.get().asItem(), rel, 2.0);
        helper.assertItemEntityPresent(Flags.MERCHANT_FLAG.get(), rel, 2.0);
        if (delay() > 0) helper.assertItemEntityPresent(Flags.NAVY_FLAG.get(), rel, 2.0);
        helper.succeed();
    }

    @ModGameTest
    public static void clearingThePoleDropsNothing(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        FlagpoleBlockEntity be = pole(helper, rel);
        be.commandSet(FlagKind.NAVY, false, null);
        be.clearContent(); // what ship assembly does before it removes the old block
        helper.destroyBlock(rel);
        helper.assertItemEntityNotPresent(Flags.NAVY_FLAG.get(), rel, 3.0);
        helper.succeed();
    }

    @ModGameTest
    public static void stateSurvivesSaveAndReload(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FlagpoleBlockEntity be = pole(helper, rel);
        use(helper, player, rel, patternedBanner(helper));
        if (delay() > 0) use(helper, player, rel, new ItemStack(Flags.NAVY_FLAG.get())); // replaces the pending banner
        be.commandStrike(true);
        BlockPos abs = helper.absolutePos(rel);
        var registries = helper.getLevel().registryAccess();
        CompoundTag tag = be.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag, registries);
        helper.assertTrue(loaded instanceof FlagpoleBlockEntity f && f.state().equals(be.state()),
                "reloaded state differs: " + (loaded instanceof FlagpoleBlockEntity f ? f.state() : loaded) + " vs " + be.state());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void allegianceQueryAgreesWithFlagLaw(GameTestHelper helper) {
        int x = 0;
        for (FlagKind kind : FlagKind.values()) {
            for (boolean struck : new boolean[]{false, true}) {
                BlockPos rel = new BlockPos(x++, 1, 1);
                pole(helper, rel).commandSet(kind, struck, null);
                FlagReading r = FlagAllegiance.at(helper.getLevel(), helper.absolutePos(rel));
                FlagKind seen = struck ? FlagKind.NONE : kind;
                helper.assertTrue(r.kind() == kind && r.shown() == seen && r.isStruck() == (struck && kind != FlagKind.NONE),
                        kind + "/" + struck + " read as " + r);
                for (Faction f : Faction.values()) {
                    for (boolean surrender : new boolean[]{false, true}) {
                        helper.assertTrue(FlagAllegiance.reaction(f, r, surrender) == FlagLaw.react(f, seen, surrender),
                                "reaction of " + f + " to " + r + " disagrees with FlagLaw");
                    }
                }
            }
        }
        // A ship with two poles: the higher one decides
        BlockPos low = new BlockPos(1, 1, 5);
        BlockPos high = new BlockPos(1, 3, 5);
        pole(helper, low).commandSet(FlagKind.NAVY, false, null);
        pole(helper, high).commandSet(FlagKind.JOLLY_ROGER, false, null);
        FlagReading ship = FlagAllegiance.ofShip(helper.getLevel(), List.of(helper.absolutePos(low), helper.absolutePos(high), helper.absolutePos(new BlockPos(5, 1, 5))));
        helper.assertTrue(ship.equals(FlagReading.flying(FlagKind.JOLLY_ROGER)), "ship shows " + ship);
        helper.succeed();
    }

    @ModGameTest
    public static void hoistedFlagPointsDownwind(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        pole(helper, rel).commandSet(FlagKind.MERCHANT, false, null);
        BlockPos abs = helper.absolutePos(rel);
        WindSample wind = WindService.sample(helper.getLevel(), Vec3.atCenterOf(abs));
        Direction expected = FlagConfig.FOLLOW_WIND.get() ? FlagWind.downwind(wind.dirX(), wind.dirZ(), Direction.NORTH) : Direction.NORTH;
        helper.assertBlockProperty(rel, FlagpoleBlock.FACING, expected);
        helper.succeed();
    }

    @ModGameTest
    public static void changesNotifyListeners(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        List<FlagpoleEvents.FlagChange> seen = new ArrayList<>();
        FlagpoleEvents.Listener listener = c -> {
            if (c.pos().equals(abs)) seen.add(c);
        };
        FlagpoleEvents.register(listener);
        try {
            FlagpoleBlockEntity be = pole(helper, rel);
            be.commandSet(FlagKind.NAVY, false, null);
            be.commandStrike(true);
            be.commandStrike(true); // no change, no event
            helper.destroyBlock(rel);
        } finally {
            FlagpoleEvents.unregister(listener);
        }
        helper.assertTrue(seen.size() == 3, "expected 3 changes, got " + seen);
        helper.assertTrue(seen.get(0).before().equals(FlagReading.NO_FLAG) && seen.get(0).after().equals(FlagReading.flying(FlagKind.NAVY)), "set: " + seen.get(0));
        helper.assertTrue(seen.get(1).after().equals(FlagReading.struck(FlagKind.NAVY)), "strike: " + seen.get(1));
        helper.assertTrue(seen.get(2).cause() == FlagpoleMachine.Cause.BROKEN && seen.get(2).after().equals(FlagReading.NO_FLAG), "break: " + seen.get(2));
        helper.succeed();
    }
}
