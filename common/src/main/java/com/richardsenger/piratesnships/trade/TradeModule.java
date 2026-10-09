package com.richardsenger.piratesnships.trade;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;

import java.util.List;

/**
 * The {@code trade} module (design.md §10.3, §4.9 cargo weight): trade good definitions, port markets with dynamic
 * prices, delivery contracts, plunder and port fee rules, cargo weight. Items ({@code trade.content}), the harbor
 * master's desk and market screen ({@code trade.desk}, {@code trade.client}) and cargo containers come from other
 * packages and call {@link TradeService}.
 */
public final class TradeModule implements ModModule {

    @Override
    public String id() {
        return "trade";
    }

    @Override
    public void registerConfig() {
        TradeConfig.init();
    }

    @Override
    public void registerContent() {
        TradeGoods.init();
        PlunderMark.init();
        com.richardsenger.piratesnships.trade.cargo.CargoContainers.init();
        com.richardsenger.piratesnships.trade.desk.HarborDesks.init();
        // CW1: the cargo force group (vanilla containers' weight on ships)
        com.richardsenger.piratesnships.ship.cargo.ShipCargo.registerContent();
    }

    @Override
    public void registerPayloads() {
        com.richardsenger.piratesnships.trade.net.MarketBackend.registerPayloads();
    }

    @Override
    public void registerEvents() {
        com.richardsenger.piratesnships.platform.event.CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> {
            TradeCommands.register(dispatcher);
            com.richardsenger.piratesnships.trade.desk.HarborDeskCommands.register(dispatcher);
        });
        com.richardsenger.piratesnships.platform.event.CommonEvents.PLAYER_LOGOUT.register(p -> {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.richardsenger.piratesnships.trade.net.MarketBackend.close(sp);
        });
        com.richardsenger.piratesnships.platform.event.CommonEvents.SERVER_STOPPED.register(s -> com.richardsenger.piratesnships.trade.net.MarketBackend.clear());
        com.richardsenger.piratesnships.platform.event.CommonEvents.SERVER_TICK_END.register(com.richardsenger.piratesnships.trade.net.MarketBackend::onServerTick);
        com.richardsenger.piratesnships.ship.cargo.ShipCargo.registerEvents();
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.trade.client.TradeClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.definitions(TradeGoods.TYPE, TradeGoods.DEFAULTS);
        data.lang(lang -> lang
                .add(CargoWeight.LoadLevel.LIGHT.translationKey(), "Light")
                .add(CargoWeight.LoadLevel.LADEN.translationKey(), "Laden")
                .add(CargoWeight.LoadLevel.HEAVILY_LADEN.translationKey(), "Heavily laden")
                .add(CargoWeight.LoadLevel.OVERLOADED.translationKey(), "Overloaded"));
        data.lang(com.richardsenger.piratesnships.trade.cargo.CargoText::lang);
        data.lang(TradeCommands::lang);
        com.richardsenger.piratesnships.trade.desk.HarborDeskData.gather(data);
        com.richardsenger.piratesnships.trade.cargo.CargoPhysicsData.gather(data);
        data.lang(com.richardsenger.piratesnships.ship.cargo.ShipCargo::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(TradeGameTests.class, CargoGameTests.class, com.richardsenger.piratesnships.trade.desk.HarborDeskGameTests.class,
                com.richardsenger.piratesnships.trade.cargo.CargoWeightGameTests.class);
    }
}
