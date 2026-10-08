package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.rpg.deeds.Deed;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The deed to faction event mapping of WS1b (design.md §10.4, §15). */
class FactionDeedsTest {

    @Test
    void everyDeedMapsAsSpecified() {
        Map<Deed, FactionEvent> expected = new EnumMap<>(Deed.class);
        expected.put(Deed.PLUNDER_MERCHANT, FactionEvent.MERCHANT_PLUNDERED);
        expected.put(Deed.KILL_NAVY, FactionEvent.NAVY_KILLED_BY_PIRATE);
        expected.put(Deed.ATTACK_NAVY, FactionEvent.NAVY_ATTACKED);
        expected.put(Deed.KILL_PIRATE, FactionEvent.PIRATE_KILLED_BY_NAVY);
        expected.put(Deed.ATTACK_VILLAGER, FactionEvent.MERCHANT_ATTACKED);
        expected.put(Deed.ATTACK_MERCHANT_SHIP, FactionEvent.MERCHANT_ATTACKED);
        expected.put(Deed.TURN_IN_PIRATE, FactionEvent.PIRATE_TURNED_IN);
        expected.put(Deed.FENCE_PLUNDER, FactionEvent.PLUNDER_FENCED);
        expected.put(Deed.TRADE_VILLAGE, FactionEvent.PORT_TRADE);
        expected.put(Deed.SINK_MERCHANT, FactionEvent.CONVOY_SUNK);
        expected.put(Deed.SINK_NAVY, FactionEvent.PATROL_LOST);
        expected.put(Deed.CAPTURE_NAVY, FactionEvent.PATROL_LOST);
        expected.put(Deed.SINK_PIRATE, FactionEvent.PIRATE_SHIP_LOST);
        expected.put(Deed.CAPTURE_PIRATE, FactionEvent.PIRATE_SHIP_LOST);
        for (Deed deed : Deed.values()) {
            assertEquals(Optional.ofNullable(expected.get(deed)), FactionDeeds.eventFor(deed, 0), deed.id());
        }
    }

    @Test
    void pirateKillCountsForTheNavyOnlyWhileNavyAligned() {
        assertEquals(Optional.of(FactionEvent.PIRATE_KILLED_BY_NAVY), FactionDeeds.eventFor(Deed.KILL_PIRATE, 0));
        assertEquals(Optional.of(FactionEvent.PIRATE_KILLED_BY_NAVY), FactionDeeds.eventFor(Deed.KILL_PIRATE, 100));
        assertEquals(Optional.empty(), FactionDeeds.eventFor(Deed.KILL_PIRATE, -1));
        assertEquals(Optional.empty(), FactionDeeds.eventFor(Deed.KILL_PIRATE, -50));
    }

    @Test
    void navyReputationMattersOnlyForPirateKills() {
        for (Deed deed : Deed.values()) {
            if (deed == Deed.KILL_PIRATE) continue;
            assertEquals(FactionDeeds.eventFor(deed, 0), FactionDeeds.eventFor(deed, -100), deed.id());
        }
    }
}
