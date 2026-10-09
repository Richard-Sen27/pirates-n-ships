package com.richardsenger.piratesnships.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.junit.jupiter.api.Test;

/**
 * HUD4: the client setup hands our HUD layers to NeoForge's {@link RegisterGuiLayersEvent} by their order. A layer
 * registered below the chat lands directly under vanilla's {@code CHAT} layer (two of them keep their registration
 * order), an above-all layer at the end, after every vanilla layer. The event runs on the real NeoForge class with a
 * list of vanilla layers, as NeoForge's {@code Gui} fills it.
 */
class NeoForgeHudLayerOrderTest {

    private static final LayeredDraw.Layer NOOP = (g, d) -> { };

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("pirates_n_ships_test", path);
    }

    private static int indexOf(List<GuiLayerManager.NamedLayer> layers, ResourceLocation name) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).name().equals(name)) return i;
        }
        return -1;
    }

    @Test
    void belowChatLayersGoUnderTheChatAndAboveAllLayersOnTop() {
        LayeredDraw.Layer ship = (g, d) -> { };
        ClientEvents.registerHudLayer(id("above"), NOOP);
        ClientEvents.registerHudLayerBelowChat(id("below_first"), ship);
        ClientEvents.registerHudLayerBelowChat(id("below_second"), NOOP);

        List<GuiLayerManager.NamedLayer> layers = new ArrayList<>(List.of(
                new GuiLayerManager.NamedLayer(VanillaGuiLayers.TITLE, NOOP),
                new GuiLayerManager.NamedLayer(VanillaGuiLayers.CHAT, NOOP),
                new GuiLayerManager.NamedLayer(VanillaGuiLayers.TAB_LIST, NOOP)));
        NeoForgeClientSetup.registerHudLayers(new RegisterGuiLayersEvent(layers));

        int title = indexOf(layers, VanillaGuiLayers.TITLE), chat = indexOf(layers, VanillaGuiLayers.CHAT);
        int first = indexOf(layers, id("below_first")), second = indexOf(layers, id("below_second"));
        assertTrue(title < first, "below-chat layers stay above the layers before the chat: " + layers);
        assertEquals(first + 1, second, "two below-chat layers keep their registration order");
        assertEquals(second + 1, chat, "directly under the chat");
        assertSame(ship, layers.get(first).layer(), "the registered layer itself");
        assertEquals(layers.size() - 1, indexOf(layers, id("above")), "an above-all layer over every vanilla layer");
    }
}
