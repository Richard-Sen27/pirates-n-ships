package com.richardsenger.piratesnships.world.wreck;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure wreck placement rules: the y rule, the water cover and the weighted choice among fitting pieces. */
class WreckPlanTest {

    private static final ResourceLocation CARGO = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "wreck/cargo_field");
    private static final ResourceLocation MAST = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "wreck/mast_stump");
    private static final ResourceLocation STERN = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "wreck/stern");
    private static final ResourceLocation SLOOP = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "wreck/sunken_sloop");

    @Test
    void seabedRowReplacesTheFloorsTopBlock() {
        // floor height = first free block above the floor (OCEAN_FLOOR_WG); its top block is one below
        assertEquals(40, WreckPlan.seabedY(41, 1));
        assertEquals(41, WreckPlan.seabedY(41, 0));
    }

    @Test
    void waterAboveCountsFromTheRowAboveTheSeabedToTheSurface() {
        // sea level 63 -> surface 62; seabed at 50: rows 51..62 are water
        assertEquals(12, WreckPlan.waterAbove(62, 50));
        assertEquals(0, WreckPlan.waterAbove(62, 62));
    }

    @Test
    void dataPiecesMatchTheSpec() {
        assertEquals(List.of(
                new WreckPieceEntry(CARGO, 3, 4), new WreckPieceEntry(MAST, 3, 12),
                new WreckPieceEntry(STERN, 2, 8), new WreckPieceEntry(SLOOP, 1, 10)), WreckData.PIECES);
    }

    @Test
    void onlyPiecesWithEnoughWaterFit() {
        assertEquals(List.of(), WreckPlan.fitting(WreckData.PIECES, 3));
        assertEquals(List.of(CARGO), templates(WreckPlan.fitting(WreckData.PIECES, 4)));
        assertEquals(List.of(CARGO), templates(WreckPlan.fitting(WreckData.PIECES, 7)));
        assertEquals(List.of(CARGO, STERN), templates(WreckPlan.fitting(WreckData.PIECES, 8)));
        assertEquals(List.of(CARGO, STERN, SLOOP), templates(WreckPlan.fitting(WreckData.PIECES, 11)));
        assertEquals(List.of(CARGO, MAST, STERN, SLOOP), templates(WreckPlan.fitting(WreckData.PIECES, 12)));
    }

    @Test
    void tooShallowGivesNothing() {
        RandomSource random = RandomSource.create(1L);
        for (int i = 0; i < 50; i++) assertEquals(Optional.empty(), WreckPlan.choose(WreckData.PIECES, 3, random));
        assertEquals(Optional.empty(), WreckPlan.choose(WreckData.PIECES, -10, random));
    }

    @Test
    void shallowForTheSloopPicksTheCargoField() {
        RandomSource random = RandomSource.create(2L);
        for (int i = 0; i < 200; i++) assertEquals(CARGO, WreckPlan.choose(WreckData.PIECES, 6, random).orElseThrow().template());
    }

    @Test
    void deepWaterPicksByWeight() {
        RandomSource random = RandomSource.create(3L);
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        int n = 90_000;
        for (int i = 0; i < n; i++) counts.merge(WreckPlan.choose(WreckData.PIECES, 20, random).orElseThrow().template(), 1, Integer::sum);
        // weights 3 / 3 / 2 / 1 of 9
        assertNear(n * 3 / 9, counts.get(CARGO));
        assertNear(n * 3 / 9, counts.get(MAST));
        assertNear(n * 2 / 9, counts.get(STERN));
        assertNear(n / 9, counts.get(SLOOP));
    }

    @Test
    void weightsOfTheFittingPiecesOnly() {
        // 8..9 blocks: cargo field (3) and stern (2)
        RandomSource random = RandomSource.create(4L);
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        int n = 50_000;
        for (int i = 0; i < n; i++) counts.merge(WreckPlan.choose(WreckData.PIECES, 9, random).orElseThrow().template(), 1, Integer::sum);
        assertEquals(2, counts.size());
        assertNear(n * 3 / 5, counts.get(CARGO));
        assertNear(n * 2 / 5, counts.get(STERN));
    }

    @Test
    void choiceIsDeterministicForASeed() {
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(WreckPlan.choose(WreckData.PIECES, 20, RandomSource.create(seed)),
                    WreckPlan.choose(WreckData.PIECES, 20, RandomSource.create(seed)));
        }
    }

    private static List<ResourceLocation> templates(List<WreckPieceEntry> pieces) {
        return pieces.stream().map(WreckPieceEntry::template).toList();
    }

    private static void assertNear(int expected, int actual) {
        assertTrue(Math.abs(expected - actual) < expected * 0.05, "expected about " + expected + ", got " + actual);
    }
}
