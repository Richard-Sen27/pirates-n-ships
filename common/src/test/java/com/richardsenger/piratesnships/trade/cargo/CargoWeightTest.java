package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.trade.TradeFixtures;
import com.richardsenger.piratesnships.trade.good.TradeGoodIndex;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.richardsenger.piratesnships.trade.cargo.CargoWeight.LoadLevel;
import java.util.List;

import static com.richardsenger.piratesnships.trade.cargo.CargoWeight.LoadLevel.*;
import static org.junit.jupiter.api.Assertions.*;

class CargoWeightTest {

    private static final CargoWeight.Params W = CargoWeight.Params.DEFAULTS;
    private static TradeGoodIndex index;

    @BeforeAll
    static void bootstrap() {
        TradeFixtures.bootstrap();
        index = TradeGoodIndex.build(TradeFixtures.GOODS, id -> true, msg -> { });
    }

    @Test
    void weightOfStacks() {
        double w = CargoWeight.total(List.of(new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.SUGAR, 32),
                new ItemStack(Items.DIRT, 10), ItemStack.EMPTY), index, W);
        assertEquals(64 * 1.0 + 32 * 0.25 + 10 * W.defaultItemWeight(), w, 1e-9);
        assertEquals(0.0, CargoWeight.weightOf(ResourceLocation.parse("minecraft:sugar"), 0, index, W));
        // Mod goods by id, without the item existing
        assertEquals(10 * 0.1, CargoWeight.weightOf(ResourceLocation.parse("pirates_n_ships:spices"), 10, index, W), 1e-12);
    }

    @Test
    void unitsMatchProvisions() {
        // A rum bottle weighs the same as cargo and as provisions; non-goods weigh like one food item
        assertEquals(ProvisionSettings.DEFAULTS.rumWeightPerUnit(), TradeGoods.DEFAULTS.get(TradeGoods.RUM).weight());
        assertEquals(ProvisionSettings.DEFAULTS.foodWeightPerUnit(), W.defaultItemWeight());
        assertEquals(30.0, CargoWeight.shipTotal(20.0, 10.0));
        assertEquals(20.0, CargoWeight.shipTotal(20.0, -1.0));
    }

    @Test
    void loadLevels() {
        assertEquals(LIGHT, LoadLevel.of(0, 100, W));
        assertEquals(LIGHT, LoadLevel.of(32, 100, W));
        assertEquals(LADEN, LoadLevel.of(33, 100, W));
        assertEquals(LADEN, LoadLevel.of(74, 100, W));
        assertEquals(HEAVILY_LADEN, LoadLevel.of(75, 100, W));
        assertEquals(HEAVILY_LADEN, LoadLevel.of(100, 100, W));
        assertEquals(OVERLOADED, LoadLevel.of(101, 100, W));
        assertEquals(OVERLOADED, LoadLevel.of(1, 0, W));
        assertEquals(LIGHT, LoadLevel.of(0, 0, W));
        assertEquals("pirates_n_ships.load_level.heavily_laden", HEAVILY_LADEN.translationKey());
    }

    @Test
    void toggleAndFactor() {
        assertEquals(50.0, CargoWeight.shipEffect(50, W));
        assertEquals(100.0, CargoWeight.shipEffect(50, new CargoWeight.Params(true, 2.0, 0.25, 0.33, 0.75, 1.0)));
        assertEquals(0.0, CargoWeight.shipEffect(50, new CargoWeight.Params(false, 2.0, 0.25, 0.33, 0.75, 1.0)));
    }
}
