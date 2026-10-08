package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * The careers' {@code Deeds.listen} listener (docs/design.md §15, CAR1), registered once from
 * {@code CareerModule.registerEvents()}. Every recorded deed reaches {@link Careers#onDeed}: the counters (by
 * {@link CareerRules#counters}: a pirate captain victim, {@code careers.captain_kinds}, counts
 * {@code careers.captain_weight} pirate kills; fence sales count their doubloons), desertion and the letter of marque.
 * Deeds only arrive while {@code reputation.enabled} is on.
 */
public final class CareerDeeds {

    private CareerDeeds() {
    }

    public static void onDeed(ServerPlayer player, Deed deed, DeedContext context, Map<Faction, Integer> applied) {
        Careers.onDeed(player, deed, context);
    }
}
