package com.richardsenger.piratesnships.station;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import com.richardsenger.piratesnships.station.winch.RiggingReport;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Why an order reached nobody (Q5): the captain's whistle and {@code /pirates crew order} used to answer only "0 of 0
 * crew carry it out", which reads the same whether the captain stood on no ship, the ship has no station for the order,
 * its winch finds no sails, or there is simply no crew aboard. {@link #why} tells these apart, each with a hint.
 */
public final class OrderHints {

    static final String KEY = "message." + Constants.MOD_ID + ".order_hint.";
    public static final String KEY_NO_SHIP = KEY + "no_ship";
    public static final String KEY_NO_STATION = KEY + "no_station";
    public static final String KEY_NO_SAILS = KEY + "no_sails";
    public static final String KEY_CANNOT = KEY + "cannot";
    public static final String KEY_NOTHING_TO_DO = KEY + "nothing_to_do";
    public static final String KEY_NO_CREW = KEY + "no_crew";

    private OrderHints() {
    }

    /**
     * Why {@code order} given on {@code ship} (null: the captain stands on none) set nobody to work and opened no job:
     * no ship; no station on it takes the order; every such station is unable (a winch without sails: the rigging's
     * first problem); every such station has nothing to do; or there are stations with work but no crew at them and
     * none free on deck. For {@code /pirates crew order}: a winch without sails names the rigging's first problem.
     */
    public static Component why(ServerLevel level, @Nullable ShipBody ship, CrewOrder order) {
        return why(level, ship, order, true);
    }

    /**
     * {@link #why} for the action bar (the captain's whistle, MSG1): without the rigging's problem, which is too long
     * for one line ("This ship has no sails").
     */
    public static Component brief(ServerLevel level, @Nullable ShipBody ship, CrewOrder order) {
        return why(level, ship, order, false);
    }

    private static Component why(ServerLevel level, @Nullable ShipBody ship, CrewOrder order, boolean detail) {
        Component name = Component.translatable(order.nameKey());
        if (ship == null) {
            return Component.translatable(KEY_NO_SHIP);
        }
        Set<StationRef> refs = new LinkedHashSet<>();
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockState(p).getBlock() instanceof StationBlock) {
                StationRef ref = Stations.at(level, p);
                if (ref != null && ref.ship().equals(ship.id()) && Stations.accepts(level, ref, order)) refs.add(ref);
            }
        }
        if (refs.isEmpty()) {
            return Component.translatable(KEY_NO_STATION, name);
        }
        int unable = 0;
        int done = 0;
        for (StationRef ref : refs) {
            int t = Stations.workTicks(level, ref, order);
            if (t < 0) unable++;
            else if (t == 0) done++;
        }
        if (unable + done == refs.size()) {
            if (done > 0) {
                return Component.translatable(KEY_NOTHING_TO_DO, name);
            }
            if (order instanceof SailOrder) {
                if (!detail) {
                    return Component.translatable(com.richardsenger.piratesnships.sailing.block.SailWinchBlock.KEY_NO_SAILS);
                }
                Component problem = RiggingReport.of(level, ship).problem();
                return Component.translatable(KEY_NO_SAILS, problem == null ? Component.literal("?") : problem);
            }
            return Component.translatable(KEY_CANNOT, name);
        }
        return Component.translatable(KEY_NO_CREW, name);
    }

    /** The English lines of the Q5 messages (datagen): these hints and the rigging report. */
    public static void lang(LangBuilder lang) {
        RiggingReport.lang(lang);
        lang.add(KEY_NO_SHIP, "Stand on your ship's deck to give orders")
                .add(KEY_NO_STATION, "No station aboard can %s")
                .add(KEY_NO_SAILS, "No sails on this ship: %s")
                .add(KEY_CANNOT, "No station can %s right now")
                .add(KEY_NOTHING_TO_DO, "Nothing left to %s")
                .add(KEY_NO_CREW, "No crew free to %s");
    }
}
