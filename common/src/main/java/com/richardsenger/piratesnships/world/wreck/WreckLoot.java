package com.richardsenger.piratesnships.world.wreck;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.mob.kraken.KrakenContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * The chest loot of the wrecks (design.md §10.1, §10.2; ST5): every vanilla chest in the {@code wreck} structure pieces
 * carries {@code LootTable: "pirates_n_ships:chests/wreck"} in its block entity data, so the loot is rolled when a
 * player first opens it. Written by datagen through vanilla's loot table codec; a datapack can replace it at
 * {@code data/pirates_n_ships/loot_table/chests/wreck.json}.
 *
 * <p>Always 3-12 doubloons; two to four rolls of ship's stores (rum, salted fish, rope, nails, a stack of lead shot,
 * now and then a cutlass at a low weight); one chest in 40 also holds a little kraken ink.
 */
public final class WreckLoot {

    public static final ResourceKey<LootTable> WRECK = ResourceKey.create(Registries.LOOT_TABLE, Constants.id("chests/wreck"));
    public static final int MIN_DOUBLOONS = 3;
    public static final int MAX_DOUBLOONS = 12;
    public static final float KRAKEN_INK_CHANCE = 0.025f;

    private WreckLoot() {
    }

    /** Called from the world module's {@code gatherData}. */
    public static void gather(DataContributions data) {
        data.encoded(PackOutput.Target.DATA_PACK, "loot_table", WRECK.location(), LootTable.DIRECT_CODEC, wreck());
    }

    public static LootTable wreck() {
        return LootTable.lootTable().setParamSet(LootContextParamSets.CHEST)
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(TradeContent.DOUBLOON.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(MIN_DOUBLOONS, MAX_DOUBLOONS)))))
                .withPool(LootPool.lootPool().setRolls(UniformGenerator.between(2, 4))
                        .add(LootItem.lootTableItem(TradeContent.RUM.get()).setWeight(10)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2))))
                        .add(LootItem.lootTableItem(CrewContent.SALTED_FISH.get()).setWeight(10)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 5))))
                        .add(LootItem.lootTableItem(TriangularSailContent.ROPE.get()).setWeight(8)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 4))))
                        .add(LootItem.lootTableItem(AssemblyContent.NAILS.get()).setWeight(8)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 8))))
                        .add(LootItem.lootTableItem(CombatContent.LEAD_SHOT.get()).setWeight(6)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(8, 16))))
                        .add(LootItem.lootTableItem(CombatContent.CUTLASS.get()).setWeight(1)))
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(KrakenContent.KRAKEN_INK.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2))))
                        .when(LootItemRandomChanceCondition.randomChance(KRAKEN_INK_CHANCE)))
                .build();
    }
}
