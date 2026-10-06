package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The persisted flood state of a ship: its breaches and the water in each compartment. Compartment ids are not stable
 * across analyses, so each compartment's water is keyed on its lowest cell (a plot position): on load, the compartment of
 * the fresh analysis that contains that cell takes the volume. If the hull changed while unloaded so that the cell is no
 * longer in a compartment, that water is lost (the same rule as {@link FloodSimulation#rebind}).
 *
 * @param breaches packed plot positions ({@link BlockPos#asLong})
 * @param water    water per compartment
 */
public record FloodState(long[] breaches, List<Water> water) {

    /** Water of one compartment, keyed by a plot cell inside it. */
    public record Water(long cell, double volume) {
    }

    static final int VERSION = 1;

    public FloodState {
        breaches = breaches.clone();
        water = List.copyOf(water);
    }

    /** Captures the current state. */
    public static FloodState capture(BreachSet breaches, FloodSimulation sim) {
        HullAnalysis a = sim.analysis();
        HullGrid g = a.grid();
        List<Water> water = new ArrayList<>();
        for (Compartment c : a.compartments()) {
            double v = sim.volume(c.id());
            if (v > 0) {
                int i = c.cellsByHeight()[0];
                water.add(new Water(BlockPos.asLong(g.originX() + g.x(i), g.originY() + g.y(i), g.originZ() + g.z(i)), v));
            }
        }
        return new FloodState(breaches.toArray(), water);
    }

    /** Applies the water to a simulation of a fresh analysis. Returns the volume that found no compartment. */
    public double applyWater(FloodSimulation sim) {
        HullAnalysis a = sim.analysis();
        HullGrid g = a.grid();
        double lost = 0;
        for (Water w : water) {
            BlockPos p = BlockPos.of(w.cell());
            int x = p.getX() - g.originX(), y = p.getY() - g.originY(), z = p.getZ() - g.originZ();
            int c = g.inBounds(x, y, z) ? a.compartmentAt(x, y, z) : -1;
            if (c < 0) {
                lost += w.volume();
            } else {
                sim.setVolume(c, sim.volume(c) + w.volume());
            }
        }
        return lost;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putLongArray("breaches", breaches);
        ListTag list = new ListTag();
        for (Water w : water) {
            CompoundTag e = new CompoundTag();
            e.putLong("cell", w.cell());
            e.putDouble("volume", w.volume());
            list.add(e);
        }
        tag.put("water", list);
        return tag;
    }

    public static FloodState fromTag(CompoundTag tag) {
        List<Water> water = new ArrayList<>();
        for (Tag t : tag.getList("water", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            double v = e.getDouble("volume");
            if (v > 0 && Double.isFinite(v)) {
                water.add(new Water(e.getLong("cell"), v));
            }
        }
        return new FloodState(tag.getLongArray("breaches"), water);
    }

    public boolean isEmpty() {
        return breaches.length == 0 && water.isEmpty();
    }
}
