package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasureBindingTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TreasureSite site(int x, int z, boolean looted) {
        return new TreasureSite(new BlockPos(x, 60, z), looted);
    }

    private static Port port(String id, PortKind kind, int x, int z, TreasureSite... sites) {
        return new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", id), kind, Level.OVERWORLD, new BlockPos(x, 63, z),
                new BoundingBox(x - 30, 50, z - 30, x + 30, 80, z + 30), Climate.TROPICAL, List.of(), Arrays.asList(sites));
    }

    @Test
    void bindsToTheNearestIslandWithAnUnlootedSiteAndItsNearestSite() {
        Port near = port("near", PortKind.PIRATE_ISLAND, 100, 0, site(110, 0, false), site(90, 0, false));
        Port far = port("far", PortKind.PIRATE_ISLAND, 500, 0, site(500, 0, false));
        Optional<TreasureBinding.Choice> c = TreasureBinding.choose(List.of(far, near), Level.OVERWORLD, BlockPos.ZERO, 2000);
        assertTrue(c.isPresent());
        assertEquals(near.id(), c.get().port().id());
        assertEquals(new BlockPos(90, 60, 0), c.get().site().pos(), "the island's site nearest to the reader");
    }

    @Test
    void skipsLootedSitesVillagesAndOtherDimensions() {
        Port looted = port("looted", PortKind.PIRATE_ISLAND, 50, 0, site(50, 0, true));
        Port village = port("village", PortKind.SEAFARER_VILLAGE, 60, 0, site(60, 0, false));
        Port nether = new Port(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "nether"), PortKind.PIRATE_ISLAND, Level.NETHER,
                new BlockPos(10, 63, 0), new BoundingBox(0, 50, -10, 20, 80, 10), Climate.ARID, List.of(), List.of(site(10, 0, false)));
        Port open = port("open", PortKind.PIRATE_ISLAND, 900, 0, site(905, 0, true), site(895, 0, false));
        Optional<TreasureBinding.Choice> c = TreasureBinding.choose(List.of(looted, village, nether, open), Level.OVERWORLD, BlockPos.ZERO, 2000);
        assertEquals(open.id(), c.orElseThrow().port().id());
        assertEquals(new BlockPos(895, 60, 0), c.get().site().pos(), "the looted site is skipped");
    }

    @Test
    void radiusIsMeasuredToTheIslandCentre() {
        Port island = port("island", PortKind.PIRATE_ISLAND, 2000, 0, site(1990, 0, false));
        assertTrue(TreasureBinding.choose(List.of(island), Level.OVERWORLD, BlockPos.ZERO, 2000).isPresent(), "exactly at the radius");
        assertFalse(TreasureBinding.choose(List.of(island), Level.OVERWORLD, BlockPos.ZERO, 1999).isPresent(), "just beyond it");
        assertFalse(TreasureBinding.choose(List.of(), Level.OVERWORLD, BlockPos.ZERO, 2000).isPresent(), "no ports");
    }

    @Test
    void tiesGoByPortId() {
        Port b = port("b_island", PortKind.PIRATE_ISLAND, 0, 100, site(0, 100, false));
        Port a = port("a_island", PortKind.PIRATE_ISLAND, 100, 0, site(100, 0, false));
        assertEquals(a.id(), TreasureBinding.choose(List.of(b, a), Level.OVERWORLD, BlockPos.ZERO, 500).orElseThrow().port().id());
    }

    @Test
    void openingAChestNearASiteMarksOnlyThatSite() {
        Port island = port("island", PortKind.PIRATE_ISLAND, 0, 0, site(0, 0, false), site(20, 0, false));
        assertTrue(TreasureBinding.markLooted(island, List.of(new BlockPos(5, 60, 0)), 2).isEmpty(), "a chest 5 blocks away");
        Port after = TreasureBinding.markLooted(island, List.of(new BlockPos(1, 61, -2)), 2).orElseThrow();
        assertTrue(after.treasures().get(0).looted());
        assertFalse(after.treasures().get(1).looted());
        assertEquals(island.id(), after.id());
        assertEquals(island.box(), after.box());
        assertTrue(TreasureBinding.markLooted(after, List.of(new BlockPos(0, 60, 0)), 2).isEmpty(), "already looted: no change");
        assertEquals(new BlockPos(20, 60, 0), TreasureBinding.firstUnlooted(after).orElseThrow().pos());
    }

    @Test
    void foundOnlyWhenTheRegistryMarksTheSiteLooted() {
        Port island = port("island", PortKind.PIRATE_ISLAND, 0, 0, site(0, 0, true), site(20, 0, false));
        assertTrue(TreasureBinding.found(Optional.of(island), new BlockPos(0, 60, 0)));
        assertFalse(TreasureBinding.found(Optional.of(island), new BlockPos(20, 60, 0)));
        assertFalse(TreasureBinding.found(Optional.empty(), new BlockPos(0, 60, 0)), "an unknown port changes nothing");
        assertFalse(TreasureBinding.found(Optional.of(island), new BlockPos(5, 60, 5)), "an unknown site changes nothing");
    }
}
