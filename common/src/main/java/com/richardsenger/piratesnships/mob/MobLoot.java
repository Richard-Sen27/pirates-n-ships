package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Entity loot tables of the humanoid mobs (written by datagen through vanilla's loot table codec; a datapack can
 * replace them at {@code data/pirates_n_ships/loot_table/entities/<mob>.json}). Pirates drop 1-3 doubloons and, when
 * killed by a player, a cutlass 1 time in 10; navy soldiers and officers drop 0-2 lead shot and 0-2 gunpowder; sailors
 * drop nothing; sharks drop 0-1 raw cod and, 1 time in 20, a prismarine shard. {@code mobs.drops} switches all of them
 * off.
 */
public final class MobLoot {

    public static final float CUTLASS_CHANCE = 0.1f;
    public static final float SHARK_SHARD_CHANCE = 0.05f;

    private MobLoot() {
    }

    public static LootTable pirate() {
        return LootTable.lootTable().setParamSet(LootContextParamSets.ENTITY)
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(TradeContent.DOUBLOON.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 3)))))
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(CombatContent.CUTLASS.get()))
                        .when(LootItemKilledByPlayerCondition.killedByPlayer())
                        .when(LootItemRandomChanceCondition.randomChance(CUTLASS_CHANCE)))
                .build();
    }

    public static LootTable navy() {
        return LootTable.lootTable().setParamSet(LootContextParamSets.ENTITY)
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(CombatContent.LEAD_SHOT.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0, 2)))))
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(Items.GUNPOWDER)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0, 2)))))
                .build();
    }

    public static LootTable shark() {
        return LootTable.lootTable().setParamSet(LootContextParamSets.ENTITY)
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(Items.COD)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0, 1)))))
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(Items.PRISMARINE_SHARD))
                        .when(LootItemRandomChanceCondition.randomChance(SHARK_SHARD_CHANCE)))
                .build();
    }
}
