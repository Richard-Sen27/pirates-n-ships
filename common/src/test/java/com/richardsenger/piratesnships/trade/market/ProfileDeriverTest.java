package com.richardsenger.piratesnships.trade.market;

import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.richardsenger.piratesnships.trade.TradeFixtures.GOODS;
import static org.junit.jupiter.api.Assertions.*;

class ProfileDeriverTest {

    private static final int SEEDS = 400;

    @Test
    void deterministic() {
        for (PortKind kind : PortKind.values()) {
            for (Climate climate : Climate.values()) {
                for (long seed = 0; seed < 20; seed++) {
                    assertEquals(ProfileDeriver.derive(kind, climate, seed, GOODS), ProfileDeriver.derive(kind, climate, seed, GOODS));
                }
            }
        }
    }

    @Test
    void seedsGiveDifferentProfiles() {
        var a = ProfileDeriver.derive(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, 1, GOODS);
        boolean differs = false;
        for (long seed = 2; seed < 20 && !differs; seed++) {
            differs = !ProfileDeriver.derive(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, seed, GOODS).roles().equals(a.roles());
        }
        assertTrue(differs);
    }

    @Test
    void everyPortProducesAndDemandsSomething() {
        for (PortKind kind : PortKind.values()) {
            for (Climate climate : Climate.values()) {
                for (long seed = 0; seed < SEEDS; seed++) {
                    PortProfile p = ProfileDeriver.derive(kind, climate, seed, GOODS);
                    assertFalse(p.goodsWith(GoodRole.PRODUCES).isEmpty(), kind + " " + climate + " " + seed);
                    assertFalse(p.goodsWith(GoodRole.DEMANDS).isEmpty(), kind + " " + climate + " " + seed);
                    assertEquals(GOODS.size(), p.roles().size());
                }
            }
        }
    }

    private static double share(PortKind kind, Climate climate, ResourceLocation good, GoodRole role) {
        int n = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            if (ProfileDeriver.derive(kind, climate, seed, GOODS).role(good) == role) n++;
        }
        return n / (double) SEEDS;
    }

    @Test
    void climateDecidesWhatIsProduced() {
        assertTrue(share(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, TradeGoods.SUGAR, GoodRole.PRODUCES)
                > 3 * share(PortKind.SEAFARER_VILLAGE, Climate.COLD, TradeGoods.SUGAR, GoodRole.PRODUCES));
        assertTrue(share(PortKind.SEAFARER_VILLAGE, Climate.COLD, TradeGoods.SPICES, GoodRole.DEMANDS)
                > 3 * share(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, TradeGoods.SPICES, GoodRole.DEMANDS));
        assertTrue(share(PortKind.SEAFARER_VILLAGE, Climate.COLD, TradeGoods.FISH, GoodRole.PRODUCES)
                > 2 * share(PortKind.SEAFARER_VILLAGE, Climate.ARID, TradeGoods.FISH, GoodRole.PRODUCES));
    }

    @Test
    void portKindDecidesWhatIsTraded() {
        // Navy outposts are supplied with powder, pirates want it
        assertTrue(share(PortKind.NAVY_OUTPOST, Climate.TEMPERATE, TradeGoods.GUNPOWDER, GoodRole.PRODUCES)
                > 3 * share(PortKind.PIRATE_ISLAND, Climate.TEMPERATE, TradeGoods.GUNPOWDER, GoodRole.PRODUCES));
        assertTrue(share(PortKind.PIRATE_ISLAND, Climate.TEMPERATE, TradeGoods.GUNPOWDER, GoodRole.DEMANDS)
                > share(PortKind.NAVY_OUTPOST, Climate.TEMPERATE, TradeGoods.GUNPOWDER, GoodRole.DEMANDS));
        // Garrisons need food
        assertTrue(share(PortKind.NAVY_OUTPOST, Climate.TROPICAL, TradeGoods.GRAIN, GoodRole.DEMANDS)
                > share(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, TradeGoods.GRAIN, GoodRole.DEMANDS));
        // Pirate islands trade the fewest goods
        int villageNot = 0, pirateNot = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            villageNot += ProfileDeriver.derive(PortKind.SEAFARER_VILLAGE, Climate.TEMPERATE, seed, GOODS).goodsWith(GoodRole.NOT_TRADED).size();
            pirateNot += ProfileDeriver.derive(PortKind.PIRATE_ISLAND, Climate.TEMPERATE, seed, GOODS).goodsWith(GoodRole.NOT_TRADED).size();
        }
        assertTrue(pirateNot > 2 * villageNot);
    }

    @Test
    void addingAGoodLaterKeepsExistingRoles() {
        Map<ResourceLocation, TradeGood> fewer = new HashMap<>(TradeGoods.DEFAULTS);
        fewer.remove(TradeGoods.COCOA);
        for (long seed = 0; seed < 50; seed++) {
            PortProfile small = ProfileDeriver.derive(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, seed, Definitions.of("trade_good", fewer));
            PortProfile extended = small.extendedWith(GOODS);
            for (var e : small.roles().entrySet()) assertEquals(e.getValue(), extended.role(e.getKey()));
            assertEquals(ProfileDeriver.roll(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, seed, TradeGoods.COCOA, GOODS.require(TradeGoods.COCOA)),
                    extended.role(TradeGoods.COCOA));
            assertSame(extended, extended.extendedWith(GOODS), "nothing new: same instance");
        }
        assertEquals(GoodRole.NOT_TRADED, ProfileDeriver.derive(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, 0, GOODS)
                .role(ResourceLocation.parse("unknown:good")));
    }
}
