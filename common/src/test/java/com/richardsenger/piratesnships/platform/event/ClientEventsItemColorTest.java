package com.richardsenger.piratesnships.platform.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

/**
 * ITC1: {@link ClientEvents#registerItemColor} keeps each handler with its items, in registration order, for the
 * loaders to hand on ({@code NeoForgeClientSetup.registerItemColors}, {@code FabricClientSetup.registerItemColors};
 * both are tested in their modules). The item suppliers are not resolved at registration time.
 */
class ClientEventsItemColorTest {

    @Test
    void handlersKeepTheirItemsAndOrder() {
        ItemColor first = (stack, tint) -> 0xFF112233;
        ItemColor second = (stack, tint) -> -1;
        Supplier<Item> a = () -> { throw new AssertionError("resolved too early"); };
        Supplier<Item> b = () -> { throw new AssertionError("resolved too early"); };
        Supplier<Item> c = () -> { throw new AssertionError("resolved too early"); };
        ClientEvents.registerItemColor(first, a, b);
        ClientEvents.registerItemColor(second, c);

        List<ClientEvents.ItemColorHandler> ours = ClientEvents.itemColors().stream()
                .filter(h -> h.color() == first || h.color() == second).toList();
        assertEquals(2, ours.size());
        assertSame(first, ours.get(0).color());
        assertEquals(List.of(a, b), ours.get(0).items());
        assertSame(second, ours.get(1).color());
        assertEquals(List.of(c), ours.get(1).items());
        assertEquals(0xFF112233, ours.get(0).color().getColor(null, 0), "the registered colour itself");
    }
}
