package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;

/** The plant rule on a built footprint (HV1c): offset plants, two-block plants, and the sections a change touches. */
class PlantFootprintTest {

    private static PlantFootprint of(long[] cells, long[] offset) {
        return new PlantFootprint(new LongOpenHashSet(cells), new LongOpenHashSet(offset));
    }

    @Test
    void offsetPlantsReadTheShiftedSet() {
        long a = BlockPos.asLong(1, 5, 1), b = BlockPos.asLong(2, 5, 1);
        PlantFootprint f = of(new long[] {a}, new long[] {b});
        assertTrue(f.hides(a, false, 0));
        assertFalse(f.hides(b, false, 0));
        assertTrue(f.hides(b, true, 0));
        assertFalse(f.hides(a, true, 0));
    }

    @Test
    void twoBlockPlantsHideAsAWhole() {
        long cut = BlockPos.asLong(0, 64, 0);
        PlantFootprint f = of(new long[] {}, new long[] {cut});
        // the upper half is cut: the lower half one below goes too, and the other way round
        assertTrue(f.hides(BlockPos.asLong(0, 63, 0), true, 1));
        assertTrue(f.hides(BlockPos.asLong(0, 65, 0), true, -1));
        // a one-block plant below the cut cell stays, and so does a lower half whose upper half is elsewhere
        assertFalse(f.hides(BlockPos.asLong(0, 63, 0), true, 0));
        assertFalse(f.hides(BlockPos.asLong(0, 62, 0), true, 1));
    }

    @Test
    void changedSectionsIncludeTheBlocksAboveAndBelow() {
        PlantFootprint before = PlantFootprint.EMPTY;
        PlantFootprint after = of(new long[] {BlockPos.asLong(3, 15, 3)}, new long[] {BlockPos.asLong(3, 15, 3)});
        Long2ObjectMap<LongList> changed = PlantFootprint.changedBySection(before, after);
        // y 14 and 15 in section 0, y 16 (the upper half of a plant rooted in the changed block) in section 1
        assertEquals(2, changed.size(), "sections " + changed.keySet());
        assertEquals(2, changed.get(SectionPos.asLong(0, 0, 0)).size());
        assertTrue(changed.get(SectionPos.asLong(0, 1, 0)).contains(BlockPos.asLong(3, 16, 3)));
        assertTrue(PlantFootprint.changedBySection(after, after).isEmpty());
        // a change in the offset set alone counts: (3,15,3) left it, (4,15,3) entered it, each with its two neighbours
        PlantFootprint shifted = of(new long[] {BlockPos.asLong(3, 15, 3)}, new long[] {BlockPos.asLong(4, 15, 3)});
        assertEquals(6, PlantFootprint.changedBySection(after, shifted).values().stream().mapToInt(LongList::size).sum());
    }
}
