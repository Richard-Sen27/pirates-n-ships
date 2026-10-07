package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The powder and shot supply of a crewed gun (C9): every block entity implementing vanilla's {@link Container} (chests,
 * barrels, our cargo crates, any modded container that implements it) on the gun's ship within
 * {@code cannons.crew.supply_range} blocks of the gun, nearest first. Items are taken through the {@link Container}
 * methods like a hopper would, so a cargo crate reconciles its bulk store as usual. Ranges and the take maths are
 * {@link CrewSupplyRules}.
 */
final class CrewSupply {

    /** One matching stack of the supply. */
    private record Stack(Container container, int slot, int count) {
    }

    private final List<Container> containers;

    private CrewSupply(List<Container> containers) {
        this.containers = containers;
    }

    /**
     * The containers on {@code ship} within {@code range} blocks of the gun at plot position {@code gun}, nearest
     * first. Plot positions near the gun belong to its ship; the ship check guards against a neighbouring plot.
     */
    static CrewSupply around(ServerLevel level, UUID ship, BlockPos gun, int range) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(gun.offset(-range, -range, -range), gun.offset(range, range, range))) {
            if (!CrewSupplyRules.inRange(gun, p, range) || p.equals(gun)) continue;
            if (level.getBlockEntity(p) instanceof Container) {
                found.add(p.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(gun::distSqr));
        List<Container> out = new ArrayList<>();
        for (BlockPos p : found) {
            ShipBody body = SableShips.containing(level, p);
            if (body != null && body.id().equals(ship) && level.getBlockEntity(p) instanceof Container c) {
                out.add(c);
            }
        }
        return new CrewSupply(out);
    }

    boolean isEmpty() {
        return containers.isEmpty();
    }

    private List<Stack> stacks(Predicate<ItemStack> what) {
        List<Stack> out = new ArrayList<>();
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && what.test(s)) out.add(new Stack(c, i, s.getCount()));
            }
        }
        return out;
    }

    private static int[] counts(List<Stack> stacks) {
        return stacks.stream().mapToInt(Stack::count).toArray();
    }

    /** Whether the supply holds at least {@code n} items matching {@code what}. */
    boolean has(Predicate<ItemStack> what, int n) {
        return n <= 0 || CrewSupplyRules.covers(counts(stacks(what)), n);
    }

    /**
     * A copy of the first matching stack with count {@code n} (to hand to the gun's load, which records what went in),
     * or null when the supply holds fewer than {@code n}. Takes nothing.
     */
    @Nullable ItemStack peek(Predicate<ItemStack> what, int n) {
        List<Stack> stacks = stacks(what);
        if (stacks.isEmpty() || !CrewSupplyRules.covers(counts(stacks), n)) return null;
        Stack first = stacks.get(0);
        return first.container().getItem(first.slot()).copyWithCount(n);
    }

    /** Takes {@code n} items matching {@code what}, nearest container and first slot first; false (nothing taken) when short. */
    boolean take(Predicate<ItemStack> what, int n) {
        List<Stack> stacks = stacks(what);
        int[] plan = CrewSupplyRules.takePlan(counts(stacks), n);
        if (plan == null) return false;
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] <= 0) continue;
            Stack s = stacks.get(i);
            s.container().removeItem(s.slot(), plan[i]);
            s.container().setChanged();
        }
        return true;
    }
}
