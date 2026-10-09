package com.richardsenger.piratesnships.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.junit.jupiter.api.Test;

/**
 * ITC1: the client setup hands our item colour handlers to NeoForge's {@link RegisterColorHandlersEvent.Item}, which
 * passes them to vanilla's {@link ItemColors#register}. The event runs on the real NeoForge class; a recording
 * {@code ItemColors} stands in for the client's, and null items for ours (real items need a bootstrapped registry, the
 * pattern of {@code CoatArmorModelExtensionTest}).
 */
class NeoForgeItemColorsTest {

    private record Registration(ItemColor color, int items) { }

    @Test
    void handlersReachVanillasItemColorsWithAllTheirItems() {
        ItemColor water = (stack, tint) -> tint == 0 ? 0xFF3F76E4 : -1;
        Supplier<Item> none = () -> null;
        ClientEvents.registerItemColor(water, none, none);

        List<Registration> registered = new ArrayList<>();
        ItemColors recording = new ItemColors() {
            @Override
            public void register(ItemColor color, ItemLike... items) {
                registered.add(new Registration(color, items.length));
            }
        };
        NeoForgeClientSetup.registerItemColors(new RegisterColorHandlersEvent.Item(recording, new BlockColors()));

        List<Registration> ours = registered.stream().filter(r -> r.color() == water).toList();
        assertEquals(1, ours.size(), "one registration per handler");
        assertSame(water, ours.getFirst().color(), "the handler itself");
        assertEquals(2, ours.getFirst().items(), "for every item of it");
    }
}
