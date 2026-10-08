package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.world.PlunderNotice.Holding;
import com.richardsenger.piratesnships.trade.cargo.BulkCargo;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.cargo.CargoContainers;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The world side of {@link PlunderNotice}: the plunder-marked units in a ship's containers (LAW3). Walks the block
 * entities the way the cargo weighing does ({@code trade.cargo.CargoWeighing}): bulk cargo containers (their load and
 * residue) and every vanilla {@link Container}, with a stack's nested content (a shulker box, a filled crate) two
 * levels deep.
 */
public final class PlunderAboard {

    private static final int MAX_DEPTH = 2;

    private PlunderAboard() {
    }

    /** Marked units in {@code blockEntities} (removed ones are skipped). */
    public static long markedUnits(Iterable<BlockEntity> blockEntities) {
        List<Holding> all = new ArrayList<>();
        for (BlockEntity be : blockEntities) {
            if (!be.isRemoved()) holdings(be, all);
        }
        return PlunderNotice.markedUnits(all);
    }

    static void holdings(BlockEntity be, List<Holding> out) {
        if (be instanceof CargoContainerBlockEntity c) {
            BulkCargo cargo = c.cargo();
            if (!cargo.kind().isEmpty()) out.add(holding(cargo.kind(), cargo.count(), 0));
            ItemStack residue = c.residue();
            if (!residue.isEmpty()) out.add(holding(residue, residue.getCount(), 0));
            return;
        }
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) out.add(holding(s, s.getCount(), 0));
            }
        }
    }

    /** {@code count} units of {@code stack}'s kind with what each carries. */
    static Holding holding(ItemStack stack, int count, int depth) {
        boolean marked = PlunderMark.isPlundered(stack);
        if (depth >= MAX_DEPTH) return Holding.of(count, marked);
        List<Holding> inner = new ArrayList<>();
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack s : contents.nonEmptyItems()) inner.add(holding(s, s.getCount(), depth + 1));
        }
        BulkCargo bulk = stack.get(CargoContainers.BULK_CARGO.get());
        if (bulk != null && !bulk.kind().isEmpty()) inner.add(holding(bulk.kind(), bulk.count(), depth + 1));
        return new Holding(count, marked, inner);
    }
}
