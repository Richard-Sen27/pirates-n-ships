package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.data.DefinitionType;
import java.util.ArrayList;
import java.util.List;
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
     * at [4, 3, 14], within the gun crews' supply range of all four guns; TPL2: a crow's nest on the mast top at
     * [4, 20, 13], ratlines on both sides of the mast from the quarterdeck up to it, and the flag on a taffrail staff.
     * Same hull, helm and waterline. Not sold at the shipwright.
     */
    public static final ShipTemplate NAVY_SLOOP_ARMED = new ShipTemplate(Constants.id("ships/navy_sloop_armed"),
            nameKey(NAVY_SLOOP_ARMED_ID), Optional.of(new BlockPos(4, 8, 22)), Optional.of(2), Direction.NORTH, 0, false);

    public static final ResourceLocation PIRATE_SLOOP_ARMED_ID = Constants.id("pirate_sloop_armed");

    /** The pirate raiders' sloop (WS4c): the same hull and guns as {@link #NAVY_SLOOP_ARMED}. Not sold at the shipwright. */
    public static final ShipTemplate PIRATE_SLOOP_ARMED = new ShipTemplate(Constants.id("ships/pirate_sloop_armed"),
            nameKey(PIRATE_SLOOP_ARMED_ID), Optional.of(new BlockPos(4, 8, 22)), Optional.of(2), Direction.NORTH, 0, false);

    /** The brig's helm on the quarterdeck (SHIP1, art/schematics/brig.py). */
    public static final BlockPos BRIG_HELM = new BlockPos(4, 12, 27);
    /**
     * The brig's waterline row: the keel (0), the bottom (1) and the hold (2..4) under water, the gun deck floor (5) and
     * the gun ports (6) clear of the sea (she settles about 0.4 of a row deeper, BrigGameTests).
     */
    public static final int BRIG_WATERLINE = 4;

    public static final ResourceLocation BRIG_ID = Constants.id("brig");

    /**
     * The brig (SHIP1, art/schematics/brig.py): two masts with two square sails each and a jib, 9×26×32, bow to the
     * north; a hold three rows deep in three watertight compartments, a lower gun deck pierced for eight guns (the lids
     * shut), the weather deck and a cabin under the raised quarterdeck. 1247 blocks; sold at the shipwright for 725
     * doubloons, the starter sloop's 400 in proportion to her blocks (1247 against 685).
     */
    public static final ShipTemplate BRIG = new ShipTemplate(Constants.id("ships/brig"), nameKey(BRIG_ID),
            Optional.of(BRIG_HELM), Optional.of(BRIG_WATERLINE), Direction.NORTH, 725);

    /**
     * The armed brig's ratline nets (SHIP1): a fan of three strands on each side, from the railing latches on the
     * weather deck's rail abaft the main mast (x 0 and 8, y 10, z 22..24) up to the mast hoops under the crow's nest
     * (x 3 and 5, y 25, z 19).
     */
    public static final List<ShipTemplate.Net> BRIG_NETS = brigNets();

    public static final ResourceLocation NAVY_BRIG_ARMED_ID = Constants.id("navy_brig_armed");

    /**
     * The navy patrols' brig (SHIP1, art/schematics/navy_brig_armed.py): {@link #BRIG} with four carriage cannons a
     * side on the lower gun deck behind open gun ports, four shot lockers (barrels) between them, a crow's nest on the
     * main masthead with mast hoops under it, railing latches on the rail and the {@link #BRIG_NETS} strung between
     * them on placement, and two swivel guns on the quarterdeck rails. Same hull, helm and waterline. Not sold.
     */
    public static final ShipTemplate NAVY_BRIG_ARMED = new ShipTemplate(Constants.id("ships/navy_brig_armed"),
            nameKey(NAVY_BRIG_ARMED_ID), Optional.of(BRIG_HELM), Optional.of(BRIG_WATERLINE), Direction.NORTH, 0, false,
            BRIG_NETS);

    public static final ResourceLocation PIRATE_BRIG_ARMED_ID = Constants.id("pirate_brig_armed");

    /** The pirate raiders' brig (SHIP1): the same blocks and nets as {@link #NAVY_BRIG_ARMED}. Not sold. */
    public static final ShipTemplate PIRATE_BRIG_ARMED = new ShipTemplate(Constants.id("ships/pirate_brig_armed"),
            nameKey(PIRATE_BRIG_ARMED_ID), Optional.of(BRIG_HELM), Optional.of(BRIG_WATERLINE), Direction.NORTH, 0, false,
            BRIG_NETS);

    public static final Map<ResourceLocation, ShipTemplate> DEFAULTS = Map.of(STARTER_SLOOP_ID, STARTER_SLOOP,
            STARTER_SLOOP_BASIC_ID, STARTER_SLOOP_BASIC, NAVY_SLOOP_ARMED_ID, NAVY_SLOOP_ARMED,
            PIRATE_SLOOP_ARMED_ID, PIRATE_SLOOP_ARMED, BRIG_ID, BRIG, NAVY_BRIG_ARMED_ID, NAVY_BRIG_ARMED,
            PIRATE_BRIG_ARMED_ID, PIRATE_BRIG_ARMED);

    private static List<ShipTemplate.Net> brigNets() {
        List<ShipTemplate.Net> out = new ArrayList<>();
        for (int[] side : new int[][] {{0, 3}, {8, 5}}) {
            for (int z = 22; z <= 24; z++) {
                out.add(new ShipTemplate.Net(new BlockPos(side[0], 10, z), new BlockPos(side[1], 25, 19)));
            }
        }
        return List.copyOf(out);
    }

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
