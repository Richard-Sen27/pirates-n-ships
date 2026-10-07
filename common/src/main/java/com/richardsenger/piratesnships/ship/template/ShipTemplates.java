package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.data.DefinitionType;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * The ship template definitions (server only: the client never needs them). Our defaults are written by datagen;
 * their structures come from {@code art/schematics/} through {@code tools/schem_to_structure.py}.
 */
public final class ShipTemplates {

    public static final DefinitionType<ShipTemplate> TYPE = DefinitionType.create("ship_template", ShipTemplate.CODEC);

    public static final ResourceLocation STARTER_SLOOP_ID = Constants.id("starter_sloop");

    /**
     * The human's starter sloop (art/schematics/starter_sloop.schem): 9×21×29, bow to the north, helm on the
     * quarterdeck. Waterline row 2: keel (0) and bottom (1) and the lowest hold row under water, the wale (3) above.
     */
    public static final ShipTemplate STARTER_SLOOP = new ShipTemplate(Constants.id("ships/starter_sloop"),
            nameKey(STARTER_SLOOP_ID), Optional.of(new BlockPos(4, 8, 22)), Optional.of(2), Direction.NORTH, 400);

    public static final Map<ResourceLocation, ShipTemplate> DEFAULTS = Map.of(STARTER_SLOOP_ID, STARTER_SLOOP);

    private ShipTemplates() {
    }

    public static String nameKey(ResourceLocation id) {
        return "ship_template." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static void init() {
    }
}
