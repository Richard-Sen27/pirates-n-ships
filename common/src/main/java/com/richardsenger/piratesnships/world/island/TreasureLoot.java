package com.richardsenger.piratesnships.world.island;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.mob.kraken.KrakenContent;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * The buried-treasure loot table {@code pirates_n_ships:chests/buried_treasure} (design.md §10.1, WG2), written by
 * datagen through vanilla's loot table codec; a datapack can replace it. One pool of 3-5 rolls; per roll (weight):
 * doubloons 8-24 (25), rum 1-3 (15), salt pork 2-5 (15), iron ingots 2-6 (12), emeralds 1-2 (8), lead shot 4-8 (6),
 * a pistol (3), kraken ink (1).
 */
public final class TreasureLoot {

    public static final int MIN_ROLLS = 3;
    public static final int MAX_ROLLS = 5;

    private TreasureLoot() {
    }

    public static LootTable buriedTreasure() {
        return LootTable.lootTable().setParamSet(LootContextParamSets.CHEST)
                .withPool(LootPool.lootPool().setRolls(UniformGenerator.between(MIN_ROLLS, MAX_ROLLS))
                        .add(entry(TradeContent.DOUBLOON.get(), 25, 8, 24))
                        .add(entry(TradeContent.RUM.get(), 15, 1, 3))
                        .add(entry(CrewContent.SALT_PORK.get(), 15, 2, 5))
                        .add(entry(Items.IRON_INGOT, 12, 2, 6))
                        .add(entry(Items.EMERALD, 8, 1, 2))
                        .add(entry(CombatContent.LEAD_SHOT.get(), 6, 4, 8))
                        .add(LootItem.lootTableItem(CombatContent.PISTOL.get()).setWeight(3))
                        .add(LootItem.lootTableItem(KrakenContent.KRAKEN_INK.get()).setWeight(1)))
                .build();
    }

    private static LootPoolSingletonContainer.Builder<?> entry(ItemLike item, int weight, int min, int max) {
        return LootItem.lootTableItem(item).setWeight(weight).apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }
}
