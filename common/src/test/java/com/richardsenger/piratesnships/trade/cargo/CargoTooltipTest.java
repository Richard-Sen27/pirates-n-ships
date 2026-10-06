package com.richardsenger.piratesnships.trade.cargo;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoTooltipTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TranslatableContents tr(Component c) {
        return assertInstanceOf(TranslatableContents.class, c.getContents());
    }

    @Test
    void plainStackGetsNoLines() {
        assertTrue(CargoTooltip.lines(false, null).isEmpty());
    }

    @Test
    void plunderedStackSaysSo() {
        List<Component> lines = CargoTooltip.lines(true, null);
        assertEquals(1, lines.size());
        assertEquals(CargoText.PLUNDERED_TOOLTIP, tr(lines.get(0)).getKey());
    }

    @Test
    void filledCrateShowsCountAndGoodThenPlunderLine() {
        List<Component> lines = CargoTooltip.lines(true, new BulkCargo(new ItemStack(Items.SUGAR, 5), 640));
        assertEquals(2, lines.size());
        TranslatableContents content = tr(lines.get(0));
        assertEquals(CargoText.TOOLTIP, content.getKey());
        assertEquals(640, content.getArgs()[0]);
        assertEquals(Items.SUGAR.getDescriptionId(), tr((Component) content.getArgs()[1]).getKey());
        assertEquals(CargoText.PLUNDERED_TOOLTIP, tr(lines.get(1)).getKey());
    }

    @Test
    void linesGoBelowTheName() {
        List<Component> tooltip = new ArrayList<>(List.of(Component.literal("name"), Component.literal("advanced")));
        CargoTooltip.insertBelowName(tooltip, CargoTooltip.lines(true, null));
        assertEquals(3, tooltip.size());
        assertEquals("name", tooltip.get(0).getString());
        assertEquals(CargoText.PLUNDERED_TOOLTIP, tr(tooltip.get(1)).getKey());
        assertEquals("advanced", tooltip.get(2).getString());

        List<Component> empty = new ArrayList<>();
        CargoTooltip.insertBelowName(empty, CargoTooltip.lines(true, null));
        assertEquals(1, empty.size());
    }
}
