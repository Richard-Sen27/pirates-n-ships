package com.richardsenger.piratesnships.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import com.richardsenger.piratesnships.trade.contract.ContractGenerator;
import com.richardsenger.piratesnships.trade.contract.ContractParams;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.market.ProfileDeriver;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import com.richardsenger.piratesnships.trade.plunder.PortFees;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.richardsenger.piratesnships.trade.TradeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeConfigAndCodecTest {

    @BeforeAll
    static void bootstrap() {
        TradeFixtures.bootstrap();
    }

    @AfterEach
    void reset() {
        TradeConfig.CONTRACTS_ENABLED.reset();
        TradeConfig.CARGO_WEIGHT_AFFECTS_SHIPS.reset();
        TradeConfig.PORT_FEES.reset();
        TradeConfig.PLUNDER_ENABLED.reset();
        TradeConfig.SPREAD.reset();
        TradeConfig.HEAVY_AT.reset();
    }

    @Test
    void configDefaultsEqualParameterDefaults() {
        assertEquals(MarketParams.DEFAULTS, TradeConfig.marketParams());
        assertEquals(ContractParams.DEFAULTS, TradeConfig.contractParams());
        assertEquals(PlunderRules.Params.DEFAULTS, TradeConfig.plunderParams());
        assertEquals(PortFees.Params.DEFAULTS, TradeConfig.feeParams());
        assertEquals(com.richardsenger.piratesnships.trade.fees.DockingRules.Params.DEFAULTS, TradeConfig.dockingParams());
        assertEquals(CargoWeight.Params.DEFAULTS, TradeConfig.cargoParams());
        assertEquals(List.of("cargo_trade", "cargo_weight_affects_ships"), TradeConfig.CARGO_WEIGHT_AFFECTS_SHIPS.path());
    }

    @Test
    void togglesReachTheRules() {
        TradeConfig.CARGO_WEIGHT_AFFECTS_SHIPS.set(false);
        assertEquals(0.0, CargoWeight.shipEffect(100, TradeConfig.cargoParams()));
        TradeConfig.PORT_FEES.set(false);
        assertEquals(0, PortFees.dockingFee(PortKind.NAVY_OUTPOST, 0, TradeConfig.feeParams()));
        TradeConfig.PLUNDER_ENABLED.set(false);
        assertEquals(PlunderRules.Outcome.NORMAL, PlunderRules.judge(PortKind.NAVY_OUTPOST, true, 10, TradeConfig.plunderParams()).outcome());
        TradeConfig.CONTRACTS_ENABLED.set(false);
        PortProfile from = ProfileDeriver.derive(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, 1, GOODS);
        PortProfile to = ProfileDeriver.derive(PortKind.NAVY_OUTPOST, Climate.COLD, 2, GOODS);
        assertTrue(ContractGenerator.generate(ResourceLocation.parse("t:a"), from,
                List.of(new ContractGenerator.Destination(ResourceLocation.parse("t:b"), to, 1000, 0)), GOODS, 1, 1,
                TradeConfig.contractParams(), TradeConfig.marketParams()).isEmpty());
    }

    @Test
    void adapterKeepsValuesSafe() {
        TradeConfig.SPREAD.set(0.0);
        assertTrue(TradeConfig.marketParams().spread() > 0, "spread never reaches 0");
        TradeConfig.HEAVY_AT.set(0.1);
        CargoWeight.Params c = TradeConfig.cargoParams();
        assertTrue(c.ladenAt() <= c.heavyAt() && c.heavyAt() <= c.overloadedAt(), "thresholds stay ordered");
    }

    private static <T> void roundTrip(Codec<T> codec, T value) {
        assertEquals(value, codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).getOrThrow());
        assertEquals(value, codec.parse(NbtOps.INSTANCE, codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow()).getOrThrow());
    }

    @Test
    void codecRoundTrips() {
        PortProfile profile = ProfileDeriver.derive(PortKind.PIRATE_ISLAND, Climate.ARID, -77, GOODS);
        roundTrip(PortProfile.CODEC, profile);
        Market m = Market.fresh(profile, 100).buy(SUGAR, SUGAR_DEF, 10, 200, unlimited()).market()
                .sell(com.richardsenger.piratesnships.trade.good.TradeGoods.FISH, GOODS.require(com.richardsenger.piratesnships.trade.good.TradeGoods.FISH), 3, 300, unlimited()).market();
        roundTrip(Market.CODEC, m);
        DeliveryContract offered = new DeliveryContract(UUID.randomUUID(), SUGAR, 64, ResourceLocation.parse("t:a"),
                ResourceLocation.parse("t:b"), 1, 3, 9, 250, 50, DeliveryContract.State.OFFERED, Optional.empty());
        roundTrip(DeliveryContract.CODEC, offered);
        DeliveryContract accepted = offered.accept(UUID.randomUUID(), 2).contract();
        roundTrip(DeliveryContract.CODEC, accepted);
        roundTrip(TradeData.Snapshot.CODEC, new TradeData.Snapshot(Map.of(ResourceLocation.parse("t:a"), m),
                List.of(offered, accepted), Map.of(ResourceLocation.parse("t:a"), 4L)));
        assertEquals(GoodRole.NOT_TRADED, PortProfile.CODEC.parse(JsonOps.INSTANCE,
                PortProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow()).getOrThrow().role(ResourceLocation.parse("x:y")));
    }
}
