package com.richardsenger.piratesnships.world.port;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** NAME3: commands find a port by its id, its id's path, its name (any case) or a name's unique start. */
class PortLookupTest {

    private static final ResourceLocation MARLOW = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_1192_1530");
    private static final ResourceLocation MARIGOLD = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_-40_96");
    private static final ResourceLocation ANNE = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "navy_outpost_-5240_2455");
    private static final ResourceLocation REACH = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "pirate_island_-3931_-9000");

    private static final List<PortLookup.Entry> PORTS = List.of(
            new PortLookup.Entry(MARLOW, "Port Marlow"),
            new PortLookup.Entry(MARIGOLD, "Port Marigold"),
            new PortLookup.Entry(ANNE, "Fort St. Anne"),
            new PortLookup.Entry(REACH, "Dead Man's Reach"));

    private static PortLookup.Result find(String typed) {
        return PortLookup.find(PORTS, typed);
    }

    @Test
    void byFullIdAndByPath() {
        assertEquals(new PortLookup.Result.Found(ANNE), find("pirates_n_ships:navy_outpost_-5240_2455"));
        assertEquals(new PortLookup.Result.Found(ANNE), find("navy_outpost_-5240_2455"));
        assertEquals(new PortLookup.Result.Found(ANNE), PortLookup.find(PORTS, ResourceLocation.withDefaultNamespace("navy_outpost_-5240_2455")));
    }

    @Test
    void byNameInAnyCaseAndQuoted() {
        assertEquals(new PortLookup.Result.Found(MARLOW), find("Port Marlow"));
        assertEquals(new PortLookup.Result.Found(MARLOW), find("port  MARLOW"));
        assertEquals(new PortLookup.Result.Found(REACH), find("\"dead man's reach\""));
        assertEquals(new PortLookup.Result.Found(ANNE), find("fort st. anne"));
    }

    @Test
    void aUniqueStartIsEnoughAndASharedOneAsksWhich() {
        assertEquals(new PortLookup.Result.Found(REACH), find("Dead"));
        PortLookup.Result r = find("Port Mar");
        PortLookup.Result.Ambiguous a = assertInstanceOf(PortLookup.Result.Ambiguous.class, r);
        assertEquals(List.of(MARIGOLD, MARLOW), a.candidates().stream().map(PortLookup.Entry::id).toList());
    }

    @Test
    void anExactNameWinsOverLongerOnes() {
        List<PortLookup.Entry> ports = List.of(new PortLookup.Entry(MARLOW, "Greyhaven"), new PortLookup.Entry(ANNE, "Greyhaven Battery"));
        assertEquals(new PortLookup.Result.Found(MARLOW), PortLookup.find(ports, "greyhaven"));
        assertEquals(new PortLookup.Result.Found(MARLOW), PortLookup.find(ports, ResourceLocation.withDefaultNamespace("greyhaven")));
    }

    @Test
    void nothingFits() {
        assertInstanceOf(PortLookup.Result.None.class, find("Tortuga"));
        assertInstanceOf(PortLookup.Result.None.class, find(""));
        assertInstanceOf(PortLookup.Result.None.class, find("pirates_n_ships:village_0_0"));
    }
}
