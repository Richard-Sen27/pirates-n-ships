package com.richardsenger.piratesnships.ship.decor.flag;

import com.mojang.serialization.DynamicOps;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagSelection.PoleReading;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The "which flag does a ship show" rule, the downwind rule and codec round trips of the flagpole state. */
class FlagSelectionAndCodecTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    private static PoleReading pole(int x, int y, int z, FlagReading r) {
        return new PoleReading(new BlockPos(x, y, z), r);
    }

    @Test
    void noPolesOrNoFlagsShowNoFlag() {
        assertEquals(FlagReading.NO_FLAG, FlagSelection.pick(List.of()));
        assertEquals(FlagReading.NO_FLAG, FlagSelection.pick(List.of(pole(0, 5, 0, FlagReading.NO_FLAG))));
    }

    @Test
    void highestPoleWithAFlagDecides() {
        assertEquals(FlagReading.flying(FlagKind.MERCHANT), FlagSelection.pick(List.of(
                pole(0, 3, 0, FlagReading.flying(FlagKind.JOLLY_ROGER)),
                pole(0, 9, 0, FlagReading.NO_FLAG),
                pole(4, 7, 0, FlagReading.flying(FlagKind.MERCHANT)))));
        assertEquals(FlagReading.struck(FlagKind.NAVY), FlagSelection.pick(List.of(
                pole(0, 3, 0, FlagReading.flying(FlagKind.JOLLY_ROGER)),
                pole(0, 8, 0, FlagReading.struck(FlagKind.NAVY)))));
    }

    @Test
    void tiesPreferStruckThenTheMoreTellingFlagThenPosition() {
        assertEquals(FlagReading.struck(FlagKind.MERCHANT), FlagSelection.pick(List.of(
                pole(0, 5, 0, FlagReading.flying(FlagKind.JOLLY_ROGER)), pole(1, 5, 0, FlagReading.struck(FlagKind.MERCHANT)))));
        assertEquals(FlagReading.flying(FlagKind.JOLLY_ROGER), FlagSelection.pick(List.of(
                pole(0, 5, 0, FlagReading.flying(FlagKind.MERCHANT)), pole(1, 5, 0, FlagReading.flying(FlagKind.JOLLY_ROGER)),
                pole(2, 5, 0, FlagReading.flying(FlagKind.NAVY)), pole(3, 5, 0, FlagReading.flying(FlagKind.CUSTOM)))));
        assertEquals(FlagReading.flying(FlagKind.NAVY), FlagSelection.pick(List.of(
                pole(0, 5, 0, FlagReading.flying(FlagKind.CUSTOM)), pole(1, 5, 0, FlagReading.flying(FlagKind.NAVY)))));
        // Order of the input never matters
        List<PoleReading> a = List.of(pole(2, 5, 1, FlagReading.flying(FlagKind.CUSTOM)), pole(-1, 5, 3, FlagReading.flying(FlagKind.CUSTOM)));
        assertEquals(FlagSelection.pick(a), FlagSelection.pick(List.of(a.get(1), a.get(0))));
    }

    @Test
    void readingShowsNothingWhenStruck() {
        assertEquals(FlagKind.NONE, FlagReading.struck(FlagKind.JOLLY_ROGER).shown());
        assertEquals(FlagKind.JOLLY_ROGER, FlagReading.struck(FlagKind.JOLLY_ROGER).kind());
        assertEquals(FlagKind.NAVY, FlagReading.flying(FlagKind.NAVY).shown());
        assertEquals(FlagReading.NO_FLAG, FlagReading.flying(FlagKind.NONE));
        assertTrue(FlagReading.struck(FlagKind.NAVY).hasFlag());
    }

    @Test
    void flagPointsDownwind() {
        assertEquals(180f, FlagWind.downwindAngle(0.0, 1.0, null, 0f), 1.0e-3f);
        assertEquals(FlagYaw.wrap((float) Math.toDegrees(Math.atan2(0.1, 0.9))), FlagWind.downwindAngle(0.1, -0.9, null, 90f), 1.0e-3f);
        assertEquals(FlagYaw.wrap((float) Math.toDegrees(Math.atan2(0.8, -0.6))), FlagWind.downwindAngle(0.8, 0.6, null, 0f), 1.0e-3f);
        assertEquals(FlagYaw.wrap((float) Math.toDegrees(Math.atan2(-0.8, 0.6))), FlagWind.downwindAngle(-0.8, -0.6, null, 0f), 1.0e-3f);
        assertEquals(90f, FlagWind.downwindAngle(0.0, 0.0, null, 90f), "calm keeps the current angle");
    }

    private static FlagpoleState roundTrip(FlagpoleState s) {
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Tag tag = FlagpoleState.CODEC.encodeStart(ops, s).getOrThrow();
        return FlagpoleState.CODEC.parse(ops, tag).getOrThrow();
    }

    @Test
    void codecRoundTrips() {
        assertEquals(FlagpoleState.EMPTY, roundTrip(FlagpoleState.EMPTY));
        FlagpoleState plain = new FlagpoleState(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL), true,
                Optional.of(UUID.randomUUID()), Optional.empty());
        assertEquals(plain, roundTrip(plain));
    }

    @Test
    void codecKeepsBannerPatternsAndPendingHoist() {
        ItemStack banner = new ItemStack(Items.GREEN_BANNER);
        var patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .add(patterns.getOrThrow(BannerPatterns.SKULL), DyeColor.WHITE)
                .add(patterns.getOrThrow(BannerPatterns.STRIPE_BOTTOM), DyeColor.RED).build());
        FlagpoleState.Pending pending = new FlagpoleState.Pending(FlagpoleState.Action.HOIST, UUID.randomUUID(), 1234L,
                FlagKind.CUSTOM, banner);
        FlagpoleState s = new FlagpoleState(FlagKind.CUSTOM, banner, false, Optional.empty(), Optional.of(pending));
        FlagpoleState back = roundTrip(s);
        assertEquals(s, back);
        assertTrue(ItemStack.matches(banner, back.flagItem()));
        assertEquals(banner.get(DataComponents.BANNER_PATTERNS), back.pending().orElseThrow().item().get(DataComponents.BANNER_PATTERNS));
    }
}
