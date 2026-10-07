package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import net.minecraft.network.chat.Component;

/** Translation keys and English text of the treasure maps (TM1). */
public final class TreasureMapText {

    private static final String P = Constants.MOD_ID + ".treasure_map.";
    public static final String TOOLTIP_BLANK = P + "tooltip.blank";
    public static final String TOOLTIP_BOUND = P + "tooltip.bound";
    public static final String TOOLTIP_FOUND = P + "tooltip.found";
    public static final String NONE = P + "none";
    public static final String DISABLED = P + "disabled";
    public static final String FOUND = P + "found";
    public static final String BEARING = P + "bearing";
    public static final String HERE = P + "here";
    public static final String POINT = P + "point.";

    static final String CMD = "commands." + Constants.MOD_ID + ".world.treasure.";
    public static final String CMD_GIVEN = CMD + "given";
    public static final String CMD_NONE = CMD + "none";
    public static final String CMD_NO_PORT = CMD + "no_port";
    public static final String CMD_NO_PLAYER = CMD + "no_player";

    private TreasureMapText() {
    }

    /** "NW, 340 blocks" or "Dig here!". */
    public static Component bearing(TreasureBearing b) {
        if (b.here()) return Component.translatable(HERE);
        return Component.translatable(BEARING, Component.translatable(POINT + b.point().key()), b.distance());
    }

    static void lang(LangBuilder lang) {
        lang.add(TOOLTIP_BLANK, "Blank: use it to find the nearest buried treasure")
                .add(TOOLTIP_BOUND, "Leads to a buried treasure")
                .add(TOOLTIP_FOUND, "The treasure has been found")
                .add(NONE, "No treasure within reach of this map")
                .add(DISABLED, "Treasure maps are disabled on this server")
                .add(FOUND, "The treasure has been found")
                .add(BEARING, "%s, %s blocks")
                .add(HERE, "X marks the spot: dig here!")
                .add(CMD_GIVEN, "Gave %s a treasure map to %s at %s")
                .add(CMD_NONE, "No pirate island with an unfound treasure in this dimension")
                .add(CMD_NO_PORT, "Port %s has no unfound treasure")
                .add(CMD_NO_PLAYER, "Only a player can be given a treasure map");
        for (TreasureBearing.Point p : TreasureBearing.Point.values()) lang.add(POINT + p.key(), p.name());
    }
}
