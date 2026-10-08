package com.richardsenger.piratesnships.rpg.deeds;

import com.richardsenger.piratesnships.rpg.reputation.Faction;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Told about every recorded deed (careers and the faction state later). Runs on the server thread. */
@FunctionalInterface
public interface DeedListener {

    /**
     * @param applied the reputation deltas the deed applied, per faction (only non-zero entries)
     */
    void onDeed(ServerPlayer player, Deed deed, DeedContext context, Map<Faction, Integer> applied);
}
