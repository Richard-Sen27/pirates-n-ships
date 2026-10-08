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

    public static final ResourceLocation STARTER_SLOOP_BASIC_ID = Constants.id("starter_sloop_basic");

    /**
     * The human's basic starter sloop (art/schematics/starter_sloop_basic.schem): the same 9×21×29 hull and helm as
     * {@link #STARTER_SLOOP}, but less fitted out (no cleats; the yards sit in front of a continuous mast), so it is
     * cheaper. Same hull rows, so the same waterline row 2.
     */
    public static final ShipTemplate STARTER_SLOOP_BASIC = new ShipTemplate(Constants.id("ships/starter_sloop_basic"),
            nameKey(STARTER_SLOOP_BASIC_ID), Optional.of(new BlockPos(4, 8, 22)), Optional.of(2), Direction.NORTH, 300);

    public static final ResourceLocation NAVY_SLOOP_ARMED_ID = Constants.id("navy_sloop_armed");

    /**
     * The navy patrols' sloop (WS4c, art/schematics/navy_sloop_armed.py): {@link #STARTER_SLOOP} with two cannons a side
     * in the waist, firing through gun ports cut in the bulwark, and a shot locker (barrel) in the hold beside the mast
     * at [4, 3, 14], within the gun crews' supply range of all four guns. Same hull, helm and waterline. Not sold at
     * the shipwright.
     */
    public static final ShipTemplate NAVY_SLOOP_ARMED = new ShipTemplate(Constants.id("ships/navy_sloop_armed"),
            nameKey(NAVY_SLOOP_ARMED_ID), Optional.of(new BlockPos(4, 8, 22)), Optional.of(2), Direction.NORTH, 0, false);

    public static final ResourceLocation PIRATE_SLOOP_ARMED_ID = Constants.id("pirate_sloop_armed");

    /** The pirate raiders' sloop (WS4c): the same hull and guns as {@link #NAVY_SLOOP_ARMED}. Not sold at the shipwright. */
    public static final ShipTemplate PIRATE_SLOOP_ARMED = new ShipTemplate(Constants.id("ships/pirate_sloop_armed"),
            nameKey(PIRATE_SLOOP_ARMED_ID), Optional.of(new BlockPos(4, 8, 22)), Optional.of(2), Direction.NORTH, 0, false);

    public static final Map<ResourceLocation, ShipTemplate> DEFAULTS = Map.of(STARTER_SLOOP_ID, STARTER_SLOOP,
            STARTER_SLOOP_BASIC_ID, STARTER_SLOOP_BASIC, NAVY_SLOOP_ARMED_ID, NAVY_SLOOP_ARMED,
            PIRATE_SLOOP_ARMED_ID, PIRATE_SLOOP_ARMED);

    private ShipTemplates() {
    }

    public static String nameKey(ResourceLocation id) {
        return "ship_template." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** Loads the definition type and the shipwright orders' content (the ship receipt, SW1). */
    public static void init() {
        ShipOrderContent.init();
    }
}
