package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.client.MarketLines;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The ship receipt's tooltip (SW1): "Starter Sloop at Village 120 40" and below it "Ready in 1.5 days" or "Ready for
 * pickup". Pure apart from building components; {@link #state} and {@link #lines} take today's world day.
 */
public final class ShipReceiptText {

    static final String KEY = "item." + Constants.MOD_ID + ".ship_receipt.";
    public static final String KEY_SHIP_AT = KEY + "ship_at";
    public static final String KEY_READY_IN = KEY + "ready_in";
    public static final String KEY_READY = KEY + "ready";
    public static final String KEY_HINT = KEY + "hint";

    /** What the tooltip says about the progress. */
    public record State(boolean ready, String daysLeft) {
        public String key() {
            return ready ? KEY_READY : KEY_READY_IN;
        }
    }

    private ShipReceiptText() {
    }

    public static State state(double finishDay, double today) {
        boolean ready = ShipOrderMath.ready(finishDay, today);
        return new State(ready, ready ? "0.0" : ShipOrderMath.formatDays(ShipOrderMath.remaining(finishDay, today)));
    }

    /** The receipt's tooltip lines below its name. */
    public static List<Component> lines(ShipReceipt receipt, double today) {
        State s = state(receipt.finishDay(), today);
        Component ship = Component.translatable(KEY_SHIP_AT, Component.translatable(receipt.name()),
                MarketLines.portName(receipt.port())).withStyle(ChatFormatting.GRAY);
        Component progress = (s.ready() ? Component.translatable(KEY_READY) : Component.translatable(KEY_READY_IN, s.daysLeft()))
                .withStyle(s.ready() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return List.of(ship, progress, Component.translatable(KEY_HINT).withStyle(ChatFormatting.DARK_GRAY));
    }
}
