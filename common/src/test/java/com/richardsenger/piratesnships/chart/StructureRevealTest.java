package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartSightings;
import com.richardsenger.piratesnships.chart.data.KnownStructure;
import com.richardsenger.piratesnships.chart.data.StructureKind;
import com.richardsenger.piratesnships.chart.data.StructureReveal;
import com.richardsenger.piratesnships.chart.net.ChartStructure;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHART2's structures on the chart: the reveal rule (horizontal distance to the bounds), ports always known or only
 * once revealed, this mod's structures only once revealed, vanilla never, nothing forgotten or doubled.
 */
class StructureRevealTest {

    private static final ResourceLocation WRECK = Constants.id("wreck");
    private static final ResourceLocation VILLAGE_STRUCTURE = Constants.id("seafarer_village");
    private static final ResourceLocation SHIPWRECK = ResourceLocation.withDefaultNamespace("shipwreck");
    private static final ResourceLocation PORT = Constants.id("village_100_200");

    private static StructureReveal.Candidate box(ResourceLocation id, boolean port, int x0, int z0, int x1, int z1) {
        return new StructureReveal.Candidate(id, port, x0, z0, x1, z1);
    }

    @Test
    void distanceIsToTheNearestPointOfTheBounds() {
        assertEquals(0, StructureReveal.distanceToBox(5, 5, 0, 0, 9, 9), 1e-9, "inside");
        assertEquals(10, StructureReveal.distanceToBox(20, 5, 0, 0, 9, 9), 1e-9, "east of a box ending at block 9 (edge at x 10)");
        assertEquals(10, StructureReveal.distanceToBox(-10, 5, 0, 0, 9, 9), 1e-9, "west");
        assertEquals(5, StructureReveal.distanceToBox(13, -4, 0, 0, 9, 9), 1e-9, "a corner: 3-4-5");
    }

    @Test
    void revealWithinTheDistanceOnly() {
        List<StructureReveal.Candidate> c = List.of(box(WRECK, false, 100, 100, 109, 119), box(PORT, true, -500, -500, -400, -400));
        ChartSightings none = StructureReveal.reveal(ChartSightings.EMPTY, c, 0, 0, 96);
        assertSame(ChartSightings.EMPTY, none, "nothing within 96: the same instance");
        ChartSightings wreck = StructureReveal.reveal(ChartSightings.EMPTY, c, 50, 110, 50);
        assertEquals(List.of(new KnownStructure(WRECK, 104, 109)), wreck.structures(), "the wreck at exactly 50 blocks, by its centre");
        assertTrue(wreck.ports().isEmpty());
        ChartSightings both = StructureReveal.reveal(wreck, c, -390, -390, 96);
        assertEquals(List.of(PORT), both.ports());
        assertEquals(wreck.structures(), both.structures(), "nothing forgotten");
        assertSame(both, StructureReveal.reveal(both, c, 50, 110, 50), "a known structure is not added twice");
    }

    @Test
    void vanillaAndPortStructureStartsAreNotRemembered() {
        List<StructureReveal.Candidate> c = List.of(box(SHIPWRECK, false, 0, 0, 10, 10), box(VILLAGE_STRUCTURE, false, 0, 0, 10, 10));
        assertSame(ChartSightings.EMPTY, StructureReveal.reveal(ChartSightings.EMPTY, c, 5, 5, 96),
                "vanilla never; a port's start is left to the port registry");
    }

    @Test
    void kindsByStructureId() {
        assertEquals(StructureKind.WRECK, StructureKind.ofStructure(WRECK));
        assertEquals(StructureKind.VILLAGE, StructureKind.ofStructure(VILLAGE_STRUCTURE));
        assertEquals(StructureKind.OUTPOST, StructureKind.ofStructure(Constants.id("navy_outpost")));
        assertEquals(StructureKind.ISLAND, StructureKind.ofStructure(Constants.id("pirate_island")));
        assertEquals(StructureKind.OTHER, StructureKind.ofStructure(Constants.id("lighthouse")));
        assertNull(StructureKind.ofStructure(SHIPWRECK));
        assertEquals(StructureKind.ISLAND, StructureKind.of(PortKind.PIRATE_ISLAND));
        assertEquals(StructureKind.OUTPOST, StructureKind.of(PortKind.NAVY_OUTPOST));
        assertEquals(StructureKind.VILLAGE, StructureKind.of(PortKind.SEAFARER_VILLAGE));
    }

    @Test
    void shownPortsAndStructures() {
        List<StructureReveal.PortEntry> ports = List.of(
                new StructureReveal.PortEntry(PORT, StructureKind.VILLAGE, 100, 200, "Port Royal"),
                new StructureReveal.PortEntry(Constants.id("island_9_9"), StructureKind.ISLAND, 9, 9, "island_9_9"));
        ChartSightings known = new ChartSightings(List.of(PORT), List.of(new KnownStructure(WRECK, -5, 7), new KnownStructure(Constants.id("lighthouse"), 1, 2)));
        List<ChartStructure> all = StructureReveal.shown(ports, known, true, 0, 0);
        assertEquals(List.of(new ChartStructure(StructureKind.VILLAGE, 100, 200, "Port Royal"),
                new ChartStructure(StructureKind.ISLAND, 9, 9, "island_9_9"),
                new ChartStructure(StructureKind.WRECK, -5, 7, ""),
                new ChartStructure(StructureKind.OTHER, 1, 2, "lighthouse")), all, "every port, then the revealed structures");
        List<ChartStructure> revealedOnly = StructureReveal.shown(ports, known, false, 0, 0);
        assertEquals(3, revealedOnly.size(), "without ports_always_known only the revealed port");
        assertEquals("Port Royal", revealedOnly.get(0).label());
        assertEquals(List.of(), StructureReveal.shown(List.of(), ChartSightings.EMPTY, true, 0, 0));
    }
}
