package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.law.world.FlagWorldGameTests.Ship;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Collection;

/**
 * The navy notices plunder aboard (LAW3, docs/design.md §13.4): the FL2 observation of a ship within
 * {@code law.flags.observe_range} looks into its containers; more than {@code law.plunder_notice_units} (16)
 * plunder-marked units records {@code suspected_piracy} of the owner once per ship and day. Fixtures are
 * {@link FlagWorldGameTests}'s (a flagless hull afloat beside land with a navy soldier about 12 blocks away) with a chest
 * in the hold. The observation interval is shortened, so each test has a config batch of its own (and the ships of
 * different tests stay out of each other's observers).
 */
public final class PlunderNoticeGameTests {

    private static final String CONFIG_BATCH = "pirates_n_ships_config_law_plunder_notice_";
    /** The chest in the hold, relative to the helm (hold spans dx/dz −1..1, dy −3..−2). */
    private static final BlockPos CHEST_FROM_HELM = new BlockPos(-1, -3, -1);

    private PlunderNoticeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PlunderNoticeGameTests.class);
    }

    /** A flagless ship whose hold chest holds {@code marked} plunder-marked sugar and 40 clean sugar. */
    private static Ship shipWithPlunder(GameTestHelper h, int marked) {
        Ship ship = FlagWorldGameTests.shipBesideLand(h, FlagKind.NONE, false, helm -> {
            BlockPos at = helm.offset(CHEST_FROM_HELM);
            h.setBlock(at, Blocks.CHEST);
            if (!(h.getBlockEntity(at) instanceof ChestBlockEntity chest)) throw new GameTestAssertException("no chest at " + at);
            chest.setItem(0, PlunderMark.mark(new ItemStack(Items.SUGAR, marked)));
            chest.setItem(1, new ItemStack(Items.SUGAR, 40));
        });
        long aboard = PlunderAboard.markedUnits(ship.body().plotBlockEntities());
        h.assertValueEqual(aboard, (long) marked, "marked units aboard after assembly");
        return ship;
    }

    /** 20 marked units aboard: the navy soldier records suspected_piracy once although it keeps looking. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = CONFIG_BATCH + "over")
    public static void navyObserverSuspectsPiracyOnceAboveTheLimit(GameTestHelper h) {
        ConfigOverrides.during(h, LawConfig.OBSERVE_INTERVAL_TICKS, 10);
        Ship ship = shipWithPlunder(h, 20);
        Player owner = FlagWorldGameTests.playerAboard(h, ship);
        FlagWorldGameTests.setOwner(h, ship, owner.getUUID());
        FlagWorldGameTests.onLand(h, MobContent.NAVY_SOLDIER.get());
        int interval = LawConfig.OBSERVE_INTERVAL_TICKS.get();
        long[] seenAt = {-1};
        h.onEachTick(() -> {
            if (seenAt[0] < 0 && FlagWorldGameTests.count(owner, CrimeType.SUSPECTED_PIRACY) > 0) seenAt[0] = h.getTick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(seenAt[0] >= 0, "the navy has not noticed the plunder yet");
            h.assertTrue(h.getTick() >= seenAt[0] + 3L * interval, "waiting for three more looks");
            h.assertValueEqual(FlagWorldGameTests.count(owner, CrimeType.SUSPECTED_PIRACY), 1L, "suspected_piracy crimes");
            h.assertValueEqual(CrimeLog.last(owner.getUUID()).orElseThrow().type(), CrimeType.SUSPECTED_PIRACY, "last crime");
        });
    }

    /** 10 marked units aboard (at most 16 pass): the navy looks several times and records nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = CONFIG_BATCH + "under")
    public static void navyObserverIgnoresPlunderBelowTheLimit(GameTestHelper h) {
        ConfigOverrides.during(h, LawConfig.OBSERVE_INTERVAL_TICKS, 10);
        Ship ship = shipWithPlunder(h, 10);
        Player owner = FlagWorldGameTests.playerAboard(h, ship);
        FlagWorldGameTests.setOwner(h, ship, owner.getUUID());
        FlagWorldGameTests.onLand(h, MobContent.NAVY_SOLDIER.get());
        int interval = LawConfig.OBSERVE_INTERVAL_TICKS.get();
        h.runAfterDelay(6L * interval, () -> {
            h.assertValueEqual(FlagWorldGameTests.count(owner, CrimeType.SUSPECTED_PIRACY), 0L, "suspected_piracy crimes");
            h.assertTrue(CrimeLog.last(owner.getUUID()).isEmpty(), "a crime was logged: " + CrimeLog.last(owner.getUUID()));
            h.succeed();
        });
    }

    /** {@code law.plunder_notice = false}: 20 marked units aboard and a navy soldier in range record nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = CONFIG_BATCH + "off")
    public static void plunderNoticeOffIgnoresPlunderAboard(GameTestHelper h) {
        ConfigOverrides.during(h, LawConfig.OBSERVE_INTERVAL_TICKS, 10);
        ConfigOverrides.during(h, LawConfig.PLUNDER_NOTICE, false);
        Ship ship = shipWithPlunder(h, 20);
        Player owner = FlagWorldGameTests.playerAboard(h, ship);
        FlagWorldGameTests.setOwner(h, ship, owner.getUUID());
        FlagWorldGameTests.onLand(h, MobContent.NAVY_SOLDIER.get());
        int interval = LawConfig.OBSERVE_INTERVAL_TICKS.get();
        h.runAfterDelay(6L * interval, () -> {
            h.assertValueEqual(FlagWorldGameTests.count(owner, CrimeType.SUSPECTED_PIRACY), 0L, "suspected_piracy crimes");
            h.succeed();
        });
    }
}
