package com.richardsenger.piratesnships.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.impl.client.rendering.ColorProviderRegistryImpl;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.junit.jupiter.api.Test;

/**
 * ITC1: the client setup hands our item colour handlers to Fabric API's {@code ColorProviderRegistry.ITEM}. Fabric
 * queues registrations made before the client builds its {@link ItemColors} and passes them to
 * {@link ItemColors#register} when it does (its {@code ItemColors} mixin calls {@code initialize}); this test plays
 * that part with a recording {@code ItemColors}. A null item stands in for ours (real items need a bootstrapped
 * registry); Fabric keys the queue by item, so each handler here has one.
 */
class FabricItemColorsTest {

    private record Registration(ItemColor color, ItemLike item) { }

    @Test
    void handlersReachVanillasItemColors() {
        ItemColor water = (stack, tint) -> tint == 0 ? 0xFF3F76E4 : -1;
        Supplier<Item> none = () -> null;
        ClientEvents.registerItemColor(water, none);
        FabricClientSetup.registerItemColors();

        List<Registration> registered = new ArrayList<>();
        ItemColors recording = new ItemColors() {
            @Override
            public void register(ItemColor color, ItemLike... items) {
                for (ItemLike item : items) registered.add(new Registration(color, item));
            }
        };
        ColorProviderRegistryImpl.ITEM.initialize(recording);

        assertEquals(List.of(new Registration(water, null)), registered.stream().filter(r -> r.color() == water).toList());
    }
}
