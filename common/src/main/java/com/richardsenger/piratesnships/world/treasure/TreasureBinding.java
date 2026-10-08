package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortIndex;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Which treasure a blank map leads to, and which treasure a chest belongs to (TM1, design.md §10.1), without world
 * access. A blank map binds to the pirate island nearest to the player (horizontal distance to the port's centre, at
 * most the search radius) that still has an unlooted treasure site, and to that island's unlooted site nearest to the
 * player. Ties go by port id and then by site position, so the choice is deterministic.
 */
public final class TreasureBinding {

    /** A treasure site of a port. */
    public record Choice(Port port, TreasureSite site) {
    }

    private TreasureBinding() {
    }

    /** The treasure a blank map used at {@code from} binds to; empty if no island with one lies within {@code radius}. */
    public static Optional<Choice> choose(Collection<Port> ports, ResourceKey<Level> dim, BlockPos from, int radius) {
        double r2 = (double) radius * radius;
        return ports.stream()
                .filter(p -> p.kind() == PortKind.PIRATE_ISLAND && p.dimension().equals(dim) && hasUnlooted(p))
                .filter(p -> PortIndex.horizontalDistanceSqr(p.centre(), from) <= r2)
                .min(Comparator.comparingDouble((Port p) -> PortIndex.horizontalDistanceSqr(p.centre(), from))
                        .thenComparing(p -> p.id().toString()))
                .flatMap(p -> nearestUnlooted(p, from).map(s -> new Choice(p, s)));
    }

    /** The port's unlooted site nearest to {@code from} (horizontally, then by position). */
    public static Optional<TreasureSite> nearestUnlooted(Port port, BlockPos from) {
        return port.treasures().stream().filter(s -> !s.looted())
                .min(Comparator.comparingDouble((TreasureSite s) -> PortIndex.horizontalDistanceSqr(s.pos(), from))
                        .thenComparing(TreasureSite::pos));
    }

    /** The port's first unlooted site in its own order (the give command with a port). */
    public static Optional<TreasureSite> firstUnlooted(Port port) {
        return port.treasures().stream().filter(s -> !s.looted()).findFirst();
    }

    public static boolean hasUnlooted(Port port) {
        return port.treasures().stream().anyMatch(s -> !s.looted());
    }

    /** Whether the chest at {@code chest} belongs to the site: at most {@code reach} blocks from it on every axis. */
    public static boolean atSite(BlockPos site, BlockPos chest, int reach) {
        return Math.abs(site.getX() - chest.getX()) <= reach && Math.abs(site.getY() - chest.getY()) <= reach
                && Math.abs(site.getZ() - chest.getZ()) <= reach;
    }

    /**
     * The port with every unlooted site within {@code reach} of one of {@code chests} marked looted; empty if nothing
     * changed.
     */
    public static Optional<Port> markLooted(Port port, Collection<BlockPos> chests, int reach) {
        List<TreasureSite> out = new ArrayList<>(port.treasures().size());
        boolean changed = false;
        for (TreasureSite s : port.treasures()) {
            if (!s.looted() && chests.stream().anyMatch(c -> atSite(s.pos(), c, reach))) {
                out.add(new TreasureSite(s.pos(), true));
                changed = true;
            } else {
                out.add(s);
            }
        }
        if (!changed) return Optional.empty();
        return Optional.of(new Port(port.id(), port.kind(), port.dimension(), port.centre(), port.box(), port.climate(),
                port.berths(), out));
    }

    /**
     * Whether the site at {@code pos} of {@code port} is marked looted (a bound map then reports the treasure found). A
     * port or site the registry no longer knows changes nothing: the map keeps leading there.
     */
    public static boolean found(Optional<Port> port, BlockPos pos) {
        return port.map(p -> p.treasures().stream().anyMatch(s -> s.pos().equals(pos) && s.looted())).orElse(false);
    }
}
