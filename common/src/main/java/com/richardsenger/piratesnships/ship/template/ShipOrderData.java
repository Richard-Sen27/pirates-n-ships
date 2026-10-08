package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.core.datagen.DataContributions;

/**
 * Datagen of the shipwright orders (SW1): lang of the receipt, the order and pickup messages and the commands, (the receipt's
 * item model is hand-made). Called from {@code trade.desk.HarborDeskData} (the desk carries the Orders tab).
 */
public final class ShipOrderData {

    private ShipOrderData() {
    }

    public static void gather(DataContributions data) {
        data.lang(lang -> {
            lang.item(ShipOrderContent.SHIP_RECEIPT, "Ship Receipt")
                    .add(ShipReceiptText.KEY_SHIP_AT, "%s at %s")
                    .add(ShipReceiptText.KEY_READY_IN, "Ready in %s days")
                    .add(ShipReceiptText.KEY_READY, "Ready for pickup")
                    .add(ShipReceiptText.KEY_HINT, "Hand it in at the village's harbor master's desk")
                    .add(ShipOrders.KEY_ORDERED, "Ordered: %s, ready in %s days. Keep the receipt")
                    .add(ShipOrders.KEY_DISABLED, "The shipwright takes no orders on this server")
                    .add(ShipOrders.KEY_NOT_VILLAGE, "There is no shipwright at this port")
                    .add(ShipOrders.KEY_NO_SESSION, "You are too far from the desk")
                    .add(ShipOrders.KEY_UNKNOWN_TEMPLATE, "The shipwright does not build %s")
                    .add(ShipOrders.KEY_TOO_MANY, "The shipwright already has %s ships on the slipway; come back later")
                    .add(ShipOrders.KEY_NO_COINS, "You need %s doubloons")
                    .add(ShipOrders.KEY_NO_LOGS, "You need %s logs")
                    .add(ShipOrders.KEY_NO_WOOL, "You need %s wool")
                    .add(ShipOrders.KEY_PICKED_UP, "Your %s lies at berth %s")
                    .add(ShipOrders.KEY_NOT_ASSEMBLED, "Your %s lies at berth %s, but could not be assembled: use its helm")
                    .add(ShipOrders.KEY_NOT_READY, "Not ready: %s days to go")
                    .add(ShipOrders.KEY_NO_BERTH, "No berth is free; come back later")
                    .add(ShipOrders.KEY_LOST, "This order was lost")
                    .add(ShipOrders.KEY_WRONG_PORT, "This receipt belongs to the shipwright at %s");
            ShipOrderCommands.lang(lang);
        });
        // The receipt's item model is hand-made (art/models/ship_receipt.bbmodel, design.md §4.8, ART4)
    }
}
