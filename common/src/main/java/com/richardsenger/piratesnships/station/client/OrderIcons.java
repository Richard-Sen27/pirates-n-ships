package com.richardsenger.piratesnships.station.client;

import com.richardsenger.piratesnships.station.order.WhistleOrder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a whistle order's {@link WhistleOrder.Icon} (client only, ART19): an item like an inventory item, a GUI sprite
 * with {@code GuiGraphics.blitSprite} at 16x16. Used by the whistle wheel ({@link WhistleMenuScreen}) and the helm
 * screen's order buttons.
 */
public final class OrderIcons {

    /** Icons are 16x16, like an item. */
    public static final int SIZE = 16;

    private static final Map<ResourceLocation, ItemStack> STACKS = new HashMap<>();

    private OrderIcons() {
    }

    /** Draws {@code icon} with its top-left corner at {@code x, y}, 16x16 in the current pose. */
    public static void draw(GuiGraphics g, WhistleOrder.Icon icon, int x, int y) {
        switch (icon.kind()) {
            case SPRITE -> g.blitSprite(icon.id(), x, y, SIZE, SIZE);
            case ITEM -> g.renderItem(STACKS.computeIfAbsent(icon.id(), id -> new ItemStack(BuiltInRegistries.ITEM.get(id))), x, y);
        }
    }
}
