package com.richardsenger.piratesnships.trade.coin;

import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The one place for doubloon handling (design.md §10.2), for trade, fines, bounties and wages. A player's coins are the
 * doubloon stacks anywhere in their inventory (main, hotbar, offhand); plundered doubloons count too.
 *
 * <pre>{@code
 * long coins = Wallet.count(player);
 * if (Wallet.take(player, 120)) { ... }   // all or nothing
 * Wallet.give(player, 300);               // fills the inventory, drops the rest at the player's feet
 * }</pre>
 * Server side only.
 */
public final class Wallet {

    private Wallet() {
    }

    public static boolean isCoin(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TradeContent.DOUBLOON.get());
    }

    public static long count(Player player) {
        return count(player.getInventory());
    }

    public static long count(Container container) {
        return CoinMath.count(slotCounts(container));
    }

    public static boolean has(Player player, long amount) {
        return amount <= 0 || count(player) >= amount;
    }

    /** Removes exactly {@code amount} doubloons if the player has them; otherwise changes nothing. */
    public static boolean take(Player player, long amount) {
        boolean ok = take(player.getInventory(), amount);
        if (ok && amount > 0) player.containerMenu.broadcastChanges();
        return ok;
    }

    public static boolean take(Container container, long amount) {
        if (amount <= 0) return amount == 0;
        int[] plan = CoinMath.planTake(slotCounts(container), amount);
        if (plan == null) return false;
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] > 0) container.removeItem(i, plan[i]);
        }
        container.setChanged();
        return true;
    }

    /** Gives {@code amount} doubloons: into the inventory where they fit, dropped next to the player otherwise. */
    public static void give(Player player, long amount) {
        if (amount <= 0) return;
        int max = TradeContent.DOUBLOON.get().getDefaultMaxStackSize();
        for (int n : CoinMath.split(amount, max)) {
            player.getInventory().placeItemBackInInventory(new ItemStack(TradeContent.DOUBLOON.get(), n));
        }
        player.containerMenu.broadcastChanges();
    }

    private static int[] slotCounts(Container container) {
        int[] counts = new int[container.getContainerSize()];
        for (int i = 0; i < counts.length; i++) {
            ItemStack s = container.getItem(i);
            counts[i] = isCoin(s) ? s.getCount() : 0;
        }
        return counts;
    }
}
