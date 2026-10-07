package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.upkeep.UpkeepText;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The crew info lines of HM1 (docs/design.md §7.1), shared by the captain's whistle and {@code /pirates crew info}:
 * "Jack: morale 65, off duty · crew 3 / bunks 2". Also holds the hammock's text keys and their English text, which
 * {@code crew.content.CrewContentModule} writes to the lang file.
 */
public final class CrewInfo {

    static final String KEY = "message." + Constants.MOD_ID + ".hammock.";
    /** "%s: morale %s, %s" (name, morale, status). */
    public static final String KEY_CREW_LINE = KEY + "crew_line";
    /** "crew %s / bunks %s". */
    public static final String KEY_SHIP_LINE = KEY + "ship_line";
    public static final String KEY_STATUS_STATION = KEY + "status.station";
    public static final String KEY_STATUS_HAMMOCK = KEY + "status.hammock";
    public static final String KEY_STATUS_FREE = KEY + "status.free";
    /** Spoken at dawn by a crew member that had no hammock. */
    public static final String KEY_NO_HAMMOCK = KEY + "no_hammock";
    /** "%s · %s": a crew line followed by its ship line. */
    public static final String KEY_WITH_SHIP = KEY + "with_ship";

    static final String COMMAND_KEY = "commands." + Constants.MOD_ID + ".crew.info.";
    /** "This ship: %s (%s hammocks)". */
    public static final String KEY_COMMAND_SHIP = COMMAND_KEY + "ship";
    public static final String KEY_COMMAND_NO_SHIP = COMMAND_KEY + "no_ship";

    /** English text of every key above, for the lang file. */
    public static final Map<String, String> LANG = lang();

    private CrewInfo() {
    }

    /**
     * "Jack: morale 65, off duty", followed by " · crew 3 / bunks 2" when it belongs to a loaded ship; "off duty,
     * unpaid" when it went unpaid at the last dawn (CR2).
     */
    public static Component crewLine(ServerLevel level, CrewMember crew) {
        Component status = crew.isUnpaid() ? Component.translatable(UpkeepText.STATUS_UNPAID, status(crew)) : status(crew);
        Component line = Component.translatable(KEY_CREW_LINE, crew.getDisplayName(), CrewMorale.get(crew), status);
        ShipBody ship = shipOf(level, crew);
        return ship == null ? line : Component.translatable(KEY_WITH_SHIP, line, shipLine(level, ship));
    }

    /** "crew 3 / bunks 2" for {@code ship}. */
    public static Component shipLine(ServerLevel level, ShipBody ship) {
        ShipBunks.Count n = ShipBunks.count(level, ship);
        return Component.translatable(KEY_SHIP_LINE, n.crew(), n.bunks());
    }

    public static Component status(CrewMember crew) {
        if (crew.assignment() != null) return Component.translatable(KEY_STATUS_STATION);
        if (crew.rest() != null) return Component.translatable(KEY_STATUS_HAMMOCK);
        return Component.translatable(KEY_STATUS_FREE);
    }

    private static @Nullable ShipBody shipOf(ServerLevel level, CrewMember crew) {
        UUID id = ShipBunks.shipIdOf(level, crew);
        return id == null ? null : SableShips.byId(level, id);
    }

    private static Map<String, String> lang() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(KEY_CREW_LINE, "%s: morale %s, %s");
        m.put(KEY_SHIP_LINE, "crew %s / bunks %s");
        m.put(KEY_WITH_SHIP, "%s · %s");
        m.put(KEY_STATUS_STATION, "at a station");
        m.put(KEY_STATUS_HAMMOCK, "in a hammock");
        m.put(KEY_STATUS_FREE, "off duty");
        m.put(KEY_NO_HAMMOCK, "No hammock for me… another night on the bare planks.");
        m.put(KEY_COMMAND_SHIP, "This ship: %s (%s hammocks)");
        m.put(KEY_COMMAND_NO_SHIP, "Stand on a ship, or name the crew members");
        m.put(HammockBlock.KEY_CREW_ONLY, "Hammocks are for the crew: they turn in here at night");
        m.put(HammockItem.KEY_NO_SUPPORT, "A hammock hangs between two supports at the same height: a fence, wall, log or solid block at each end");
        return Map.copyOf(m);
    }
}
