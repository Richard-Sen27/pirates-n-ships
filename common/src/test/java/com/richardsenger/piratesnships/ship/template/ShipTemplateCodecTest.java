package com.richardsenger.piratesnships.ship.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipTemplateCodecTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ShipTemplate parse(String json) {
        return ShipTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void fullEntryParses() {
        ShipTemplate t = parse("""
                {"structure": "pirates_n_ships:ships/starter_sloop", "name": "ship_template.pirates_n_ships.starter_sloop",
                 "helm": [4, 8, 22], "waterline": 2, "bow": "north", "price": 400}""");
        assertEquals(ShipTemplates.STARTER_SLOOP, t);
    }

    @Test
    void optionalFieldsDefault() {
        ShipTemplate t = parse("{\"structure\": \"example:ships/cog\", \"name\": \"cog\"}");
        assertEquals(Optional.empty(), t.helm());
        assertEquals(Optional.empty(), t.waterline());
        assertEquals(Direction.NORTH, t.bow());
        assertEquals(0, t.price());
        // waterline default: one row below the helm, or the bottom row for a hull without one
        assertEquals(7, t.waterlineFor(new BlockPos(4, 8, 22)));
        assertEquals(0, t.waterlineFor(null));
        assertEquals(0, t.waterlineFor(new BlockPos(0, 0, 0)));
        // an explicit waterline wins over the helm
        assertEquals(2, ShipTemplates.STARTER_SLOOP.waterlineFor(new BlockPos(4, 8, 22)));
    }

    @Test
    void verticalBowAndNegativePriceAreRejected() {
        assertTrue(ShipTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"structure\": \"a:b\", \"name\": \"n\", \"bow\": \"up\"}")).isError());
        assertTrue(ShipTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"structure\": \"a:b\", \"name\": \"n\", \"price\": -1}")).isError());
        assertTrue(ShipTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"name\": \"n\"}")).isError());
    }

    @Test
    void roundTrip() {
        ShipTemplate t = new ShipTemplate(Constants.id("ships/x"), "k", Optional.of(new BlockPos(1, 2, 3)), Optional.empty(),
                Direction.EAST, 12);
        JsonElement json = ShipTemplate.CODEC.encodeStart(JsonOps.INSTANCE, t).getOrThrow();
        assertEquals(t, ShipTemplate.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals("ship_template.pirates_n_ships.ships.x", ShipTemplates.nameKey(Constants.id("ships/x")));
    }
}
