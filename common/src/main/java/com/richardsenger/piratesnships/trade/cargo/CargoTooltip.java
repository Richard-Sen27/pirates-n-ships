package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extra tooltip lines of the trade module: "Plundered" on a stack with the plunder mark (or a filled container
 * whose cargo is plundered), and "count × good" on a container item carrying {@code bulk_cargo}. Shown by
 * {@code trade.client.TradeClient} through {@code ClientEvents.ITEM_TOOLTIP}.
 */
public final class CargoTooltip {

    private CargoTooltip() {
    }

    /** The lines for {@code stack} (reads mod data components, so it needs the game's registries). */
    public static List<Component> lines(ItemStack stack) {
        BulkCargo cargo = stack.get(CargoContainers.BULK_CARGO.get());
        boolean plundered = PlunderMark.isPlundered(stack) || cargo != null && PlunderMark.isPlundered(cargo.kind());
        return lines(plundered, cargo);
    }

    /** The lines for a stack that is (or holds) plundered goods and carries {@code cargo}; empty when neither. */
    public static List<Component> lines(boolean plundered, @Nullable BulkCargo cargo) {
        List<Component> out = new ArrayList<>(2);
        if (cargo != null) {
            out.add(Component.translatable(CargoText.TOOLTIP, cargo.count(), cargo.kind().getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        if (plundered) out.add(Component.translatable(CargoText.PLUNDERED_TOOLTIP).withStyle(ChatFormatting.RED));
        return out;
    }

    /** Inserts {@code extra} right below the item name (line 0), above vanilla's own and advanced lines. */
    public static void insertBelowName(List<Component> tooltip, List<Component> extra) {
        if (!extra.isEmpty()) tooltip.addAll(Math.min(1, tooltip.size()), extra);
    }
}
