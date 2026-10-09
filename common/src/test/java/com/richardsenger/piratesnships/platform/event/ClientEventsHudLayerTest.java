package com.richardsenger.piratesnships.platform.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * HUD4: HUD layers carry their order. {@link ClientEvents#registerHudLayer} goes above all, {@link
 * ClientEvents#registerHudLayerBelowChat} below the chat, and {@link ClientEvents#hudLayers(ClientEvents.HudOrder)}
 * hands each loader the layers of one order in registration order (Fabric's {@code MixinGui} and its above-all
 * callback draw from it; NeoForge's mapping is tested in the neoforge module).
 */
class ClientEventsHudLayerTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("pirates_n_ships_test", "client_events_" + path);
    }

    private static List<ResourceLocation> ours(ClientEvents.HudOrder order) {
        return ClientEvents.hudLayers(order).stream().map(ClientEvents.HudLayer::id)
                .filter(i -> i.getPath().startsWith("client_events_")).toList();
    }

    @Test
    void layersKeepTheirOrderAndRegistrationSequence() {
        LayeredDraw.Layer noop = (g, d) -> { };
        ClientEvents.registerHudLayer(id("a"), noop);
        ClientEvents.registerHudLayerBelowChat(id("b"), noop);
        ClientEvents.registerHudLayer(id("c"), noop);
        ClientEvents.registerHudLayerBelowChat(id("d"), noop);

        assertEquals(List.of(id("a"), id("c")), ours(ClientEvents.HudOrder.ABOVE_ALL));
        assertEquals(List.of(id("b"), id("d")), ours(ClientEvents.HudOrder.BELOW_CHAT));
        assertEquals(ClientEvents.HudOrder.BELOW_CHAT, ClientEvents.hudLayers().stream()
                .filter(l -> l.id().equals(id("b"))).findFirst().orElseThrow().order());
    }
}
