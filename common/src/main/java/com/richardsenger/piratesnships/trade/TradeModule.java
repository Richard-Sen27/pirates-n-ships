package com.richardsenger.piratesnships.trade;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;

import java.util.List;

/**
 * The {@code trade} module (design.md §10.3, §4.9 cargo weight): trade good definitions, port markets with dynamic
 * prices, delivery contracts, plunder and port fee rules, cargo weight. Items ({@code trade.content}), ports, the
 * market screen and cargo containers come from other packages and call {@link TradeService}.
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
    }

    @Override
    public void gatherData(DataContributions data) {
        data.definitions(TradeGoods.TYPE, TradeGoods.DEFAULTS);
        data.lang(lang -> lang
                .add(CargoWeight.LoadLevel.LIGHT.translationKey(), "Light")
                .add(CargoWeight.LoadLevel.LADEN.translationKey(), "Laden")
                .add(CargoWeight.LoadLevel.HEAVILY_LADEN.translationKey(), "Heavily laden")
                .add(CargoWeight.LoadLevel.OVERLOADED.translationKey(), "Overloaded"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(TradeGameTests.class);
    }
}
