package com.richardsenger.piratesnships.trade.coin;

import java.util.ArrayList;
import java.util.List;

/** Pure coin arithmetic for {@link Wallet}: counting slot counts, planning an all-or-nothing take, splitting a payout. */
public final class CoinMath {

    private CoinMath() {
    }

    /** Sum of the coin counts per slot (negative counts are ignored). */
    public static long count(int[] slotCounts) {
        long total = 0;
        for (int c : slotCounts) total += Math.max(0, c);
        return total;
    }

    /**
     * How many coins to take from each slot to remove exactly {@code amount}: smallest stacks first (so partial stacks
     * are used up before full ones), ties by slot order. {@code null} if the slots hold fewer coins. Amount 0 = all zero.
     */
    public static int[] planTake(int[] slotCounts, long amount) {
        if (amount < 0 || count(slotCounts) < amount) return null;
        int[] take = new int[slotCounts.length];
        Integer[] order = new Integer[slotCounts.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> slotCounts[a] != slotCounts[b] ? Integer.compare(slotCounts[a], slotCounts[b]) : Integer.compare(a, b));
        long left = amount;
        for (int i : order) {
            if (left == 0) break;
            int c = Math.max(0, slotCounts[i]);
            int t = (int) Math.min(c, left);
            take[i] = t;
            left -= t;
        }
        return take;
    }

    /** Stack sizes for {@code amount} coins at {@code maxStack} per stack (full stacks, then the rest). */
    public static List<Integer> split(long amount, int maxStack) {
        List<Integer> out = new ArrayList<>();
        int max = Math.max(1, maxStack);
        long left = Math.max(0, amount);
        while (left > 0) {
            int n = (int) Math.min(max, left);
            out.add(n);
            left -= n;
        }
        return out;
    }
}
