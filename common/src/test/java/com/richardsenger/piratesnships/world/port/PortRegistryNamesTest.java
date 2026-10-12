package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NAME3: the registry names ports as they register, keeps names through updates, and names a pre-NAME3 save on load. */
class PortRegistryNamesTest {

    private static final long SEED = 12_345L;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Port port(PortKind kind, int x, int z) {
        return new Port(PortService.portId(kind, new BlockPos(x, 64, z)), kind, Level.OVERWORLD, new BlockPos(x, 64, z),
                new BoundingBox(x - 20, 50, z - 20, x + 20, 90, z + 20), Climate.TEMPERATE, List.of());
    }

    private static PortRegistry registry() {
        PortRegistry r = new PortRegistry();
        r.seed(SEED);
        return r;
    }

    @Test
    void addingNamesThePortByTheRule() {
        PortRegistry r = registry();
        Port p = port(PortKind.SEAFARER_VILLAGE, 1192, 1530);
        assertTrue(r.add(p));
        String name = r.index().byId(p.id()).orElseThrow().name();
        assertEquals(PortNames.name(SEED, PortKind.SEAFARER_VILLAGE, 1192, 1530, k -> false), name);
        assertFalse(r.add(p.withName("Somewhere Else")), "a known id is not added again");
        assertEquals(name, r.index().byId(p.id()).orElseThrow().name());
        assertEquals(p.id(), r.byName(name.toUpperCase(java.util.Locale.ROOT)).orElseThrow().id());
    }

    @Test
    void aGivenNameIsKeptUnlessTaken() {
        PortRegistry r = registry();
        r.add(port(PortKind.NAVY_OUTPOST, 0, 0).withName("Fort St. Anne"));
        r.add(port(PortKind.NAVY_OUTPOST, 500, 0).withName("fort st. anne"));
        assertEquals("Fort St. Anne", r.index().all().get(0).name());
        assertNotEquals("fort st. anne", r.index().all().get(1).name());
    }

    @Test
    void updatesKeepTheName() {
        PortRegistry r = registry();
        Port p = port(PortKind.PIRATE_ISLAND, -3931, -9000);
        r.add(p);
        String name = r.index().byId(p.id()).orElseThrow().name();
        assertTrue(r.update(p)); // an unnamed copy, as code that rebuilt the record would pass
        assertEquals(name, r.index().byId(p.id()).orElseThrow().name());
    }

    @Test
    void manyPortsNeverShareAName() {
        PortRegistry r = registry();
        java.util.Random rnd = new java.util.Random(3);
        for (int i = 0; i < 600; i++) {
            PortKind kind = PortKind.values()[i % 3];
            r.add(port(kind, rnd.nextInt(40_000) - 20_000, rnd.nextInt(40_000) - 20_000));
        }
        Set<String> keys = new HashSet<>();
        for (Port p : r.index().all()) assertTrue(keys.add(PortNames.key(p.name())), "repeated " + p.name());
    }

    @Test
    void aPreName3SaveGetsNamesOnLoadTheSameWayEveryTime() {
        PortRegistry before = new PortRegistry();
        before.add(port(PortKind.SEAFARER_VILLAGE, 1192, 1530));
        before.add(port(PortKind.NAVY_OUTPOST, -5240, 2455));
        before.add(port(PortKind.PIRATE_ISLAND, -3931, -9000));
        CompoundTag saved = before.save(new CompoundTag(), null);
        // strip the names, as a world saved before NAME3 has them
        ListTag list = saved.getList("ports", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) list.getCompound(i).remove("name");
        for (int i = 0; i < list.size(); i++) assertFalse(list.getCompound(i).contains("name"));

        PortRegistry a = PortRegistry.load(saved.copy(), null);
        assertTrue(a.index().all().stream().noneMatch(Port::named), "nothing named before the seed is known");
        a.seed(SEED);
        PortRegistry b = PortRegistry.load(saved.copy(), null);
        b.seed(SEED);
        assertTrue(a.isDirty(), "the names are saved from now on");
        List<String> names = a.index().all().stream().map(Port::name).toList();
        assertEquals(names, b.index().all().stream().map(Port::name).toList());
        assertTrue(names.stream().noneMatch(String::isEmpty));
        assertEquals(3, new HashSet<>(names).size());
        assertTrue(names.get(1).startsWith("Fort ") || names.get(1).endsWith(" Battery"), names.get(1));
        // and after the save with names, the next load changes nothing
        PortRegistry c = PortRegistry.load(a.save(new CompoundTag(), null), null);
        c.seed(SEED + 1);
        assertFalse(c.isDirty());
        assertEquals(names, c.index().all().stream().map(Port::name).toList());
        ResourceLocation outpost = a.index().all().get(1).id();
        assertEquals(outpost, c.byName(names.get(1)).orElseThrow().id());
    }
}
