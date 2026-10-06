package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.good.TradeGoodIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Cargo weight of a set of container positions (design.md §4.9), for the ship integration, which supplies the
 * positions of a ship's blocks. Counts bulk cargo containers and every block entity with a vanilla {@link Container}
 * (chests, barrels, hoppers, shulker boxes, ...); a stack's nested content (a shulker box or a filled crate in a chest)
 * counts too. Positions are deduplicated; unloaded or non-container positions weigh nothing.
 *
 * <p>Provision pantries report their own weight ({@code ProvisionStore.totalWeight()}); pass them in {@code skip} so
 * nothing is counted twice. Ship total = {@code CargoWeight.shipTotal(weigh(..., isPantry), pantriesWeight)}.
 */
public final class CargoWeighing {

    private CargoWeighing() {
    }

    public static double weigh(Level level, Iterable<BlockPos> positions) {
        return weigh(level, positions, be -> false);
    }

    public static double weigh(Level level, Iterable<BlockPos> positions, Predicate<BlockEntity> skip) {
        TradeGoodIndex goods = TradeService.goods(level);
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos p : positions) unique.add(p.immutable());
        double total = 0;
        for (BlockPos p : unique) {
            if (!level.isLoaded(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be == null || skip.test(be)) continue;
            total += weigh(be, goods);
        }
        return total;
    }

    /** Weight of one block entity's content (0 if it holds nothing we know). */
    public static double weigh(BlockEntity be, TradeGoodIndex goods) {
        if (be instanceof CargoContainerBlockEntity c) {
            BulkCargo cargo = c.cargo();
            return stackWeight(cargo.kind().copyWithCount(cargo.count()), goods, 0) + stackWeight(c.residue(), goods, 0);
        }
        if (be instanceof Container container) {
            double w = 0;
            for (int i = 0; i < container.getContainerSize(); i++) w += stackWeight(container.getItem(i), goods, 0);
            return w;
        }
        return 0;
    }

    /** A stack's weight including what it carries (shulker box contents, a filled crate's cargo), two levels deep. */
    public static double stackWeight(ItemStack stack, TradeGoodIndex goods, int depth) {
        if (stack.isEmpty()) return 0;
        double w = CargoWeight.weightOf(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), goods, TradeConfig.cargoParams());
        if (depth >= 2) return w;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack inner : contents.nonEmptyItems()) w += stack.getCount() * stackWeight(inner, goods, depth + 1);
        }
        BulkCargo bulk = stack.get(CargoContainers.BULK_CARGO.get());
        if (bulk != null) w += stack.getCount() * stackWeight(bulk.kind().copyWithCount(bulk.count()), goods, depth + 1);
        return w;
    }
}
