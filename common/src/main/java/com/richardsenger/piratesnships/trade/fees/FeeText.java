package com.richardsenger.piratesnships.trade.fees;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.network.chat.Component;

/** The harbor dues' messages (PRT1b); English text through datagen ({@link #lang}). */
public final class FeeText {

    public static final String KEY = "message." + Constants.MOD_ID + ".harbor_dues.";
    public static final String PAID = KEY + "paid";
    public static final String PAID_SHIP = KEY + "paid_ship";
    public static final String WAIVED = KEY + "waived";
    public static final String OWED = KEY + "owed";
    public static final String DESK_OWED = KEY + "desk_owed";
    public static final String DESK_SETTLED = KEY + "desk_settled";
    public static final String YOUR_SHIP = KEY + "your_ship";

    private FeeText() {
    }

    /** "Harbor dues at Port Royal: 5 doubloons", by who paid; {@code at} is the port or the harbor master. */
    public static Component charged(DockingRules.Payer payer, Component at, Component ship, int fee) {
        return switch (payer) {
            case WAIVED -> Component.translatable(WAIVED, at);
            case WALLET -> Component.translatable(PAID, at, fee);
            case SHIP -> Component.translatable(PAID_SHIP, at, fee, ship);
            case OWED -> Component.translatable(OWED, at, fee, ship);
        };
    }

    /** A ship's shown name: its name, or "your ship". */
    public static Component shipName(String name) {
        return name == null || name.isBlank() ? Component.translatable(YOUR_SHIP) : Component.literal(name);
    }

    /** The desk's refusal while dues are owed at its port. */
    public static Component deskOwed(int owed) {
        return Component.translatable(DESK_OWED, owed);
    }

    /** The desk took the dues owed from the player's wallet. */
    public static Component deskSettled(int paid) {
        return Component.translatable(DESK_SETTLED, paid);
    }

    public static void lang(DataContributions data) {
        data.lang(lang -> lang
                .add(PAID, "Harbor dues at %s: %s doubloons")
                .add(PAID_SHIP, "Harbor dues at %s: %s doubloons, taken from the coins aboard %s")
                .add(WAIVED, "Harbor dues at %s: waived")
                .add(OWED, "Harbor dues at %s: %s doubloons owed for %s. Pay at the harbor desk with doubloons in hand")
                .add(DESK_OWED, "Harbor dues owed: %s. Use the desk with doubloons in hand to pay")
                .add(DESK_SETTLED, "Harbor dues paid: %s doubloons")
                .add(YOUR_SHIP, "your ship"));
    }
}
