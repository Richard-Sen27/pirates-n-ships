package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import com.richardsenger.piratesnships.world.treasure.TreasureMapContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The loot of the port structures' chests (STR6, design.md §10.2): one table per kind of building,
 * {@code pirates_n_ships:chests/<structure>/<kind>}, written by datagen through vanilla's loot table codec (a datapack can
 * replace any of them). The pieces' generators write the table into each vanilla chest's block entity data
 * ({@code buildspec.Piece.loot_chest}, no seed, so the loot is rolled when a player first opens the chest). Every pool's
 * rolls are {@link ConfiguredRolls} (scaled by {@code world.structures.loot_rolls}); {@link LootChestProcessor} drops the
 * table at placement while {@code world.structures.loot} is off. Wrecks ({@code chests/wreck}) and the buried treasure
 * ({@code chests/buried_treasure}) keep their own tables.
 *
 * <ul>
 *     <li>{@code village/house} (houses, the warehouse, the chapel's alms chest): household goods and food, a few coins.</li>
 *     <li>{@code village/tavern} (tavern, inn): rum, food, coins.</li>
 *     <li>{@code village/shipwright} (shipwright, net loft): tools, nails, rope, cloth.</li>
 *     <li>{@code navy_outpost/barracks} (barracks, brig guard rooms, officers' quarters, the gate's office): rations,
 *     shot, powder, now and then a weapon.</li>
 *     <li>{@code navy_outpost/armoury}: weapons, shot, powder, cannonballs.</li>
 *     <li>{@code navy_outpost/store_house} (store house, tower landings): rations, powder, shot.</li>
 *     <li>{@code pirate_island/hut} (tents, huts, the camp's loot heap): rum, food, plunder.</li>
 *     <li>{@code pirate_island/tavern} (the island's taverns): rum, food, coins.</li>
 *     <li>{@code pirate_island/captain} (the captain's huts): a blank treasure map, coins, a weapon.</li>
 * </ul>
 */
public final class StructureLoot {

    /** The prefix of every table here; {@link #isStructureTable} matches it. */
    public static final String PREFIX = Constants.MOD_ID + ":chests/";
    /** The structures whose chests use these tables (the first path segment after {@code chests/}). */
    public static final List<String> STRUCTURES = List.of("village", "navy_outpost", "pirate_island");

    public static final ResourceKey<LootTable> VILLAGE_HOUSE = key("village/house");
    public static final ResourceKey<LootTable> VILLAGE_TAVERN = key("village/tavern");
    public static final ResourceKey<LootTable> VILLAGE_SHIPWRIGHT = key("village/shipwright");
    public static final ResourceKey<LootTable> OUTPOST_BARRACKS = key("navy_outpost/barracks");
    public static final ResourceKey<LootTable> OUTPOST_ARMOURY = key("navy_outpost/armoury");
    public static final ResourceKey<LootTable> OUTPOST_STORE_HOUSE = key("navy_outpost/store_house");
    public static final ResourceKey<LootTable> ISLAND_HUT = key("pirate_island/hut");
    public static final ResourceKey<LootTable> ISLAND_TAVERN = key("pirate_island/tavern");
    public static final ResourceKey<LootTable> ISLAND_CAPTAIN = key("pirate_island/captain");

    private StructureLoot() {
    }

    private static ResourceKey<LootTable> key(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Constants.id("chests/" + path));
    }

    /** Whether {@code table} (a loot table id as written in a chest's {@code LootTable}) is one of these tables' family. */
    public static boolean isStructureTable(String table) {
        if (!table.startsWith(PREFIX)) return false;
        String rest = table.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        return slash > 0 && STRUCTURES.contains(rest.substring(0, slash));
    }

    /** One entry of a pool: {@code min}..{@code max} of an item at a weight. */
    public record Drop(Supplier<? extends ItemLike> item, int weight, int min, int max) {
    }

    /** One pool: {@code min}..{@code max} rolls (scaled by the config) over the drops, all of it at {@code chance}. */
    public record Pool(int min, int max, float chance, List<Drop> drops) {
    }

    /** Every table, by key, as its pools (the items are resolved when a table is built). */
    public static Map<ResourceKey<LootTable>, List<Pool>> tables() {
        Map<ResourceKey<LootTable>, List<Pool>> out = new LinkedHashMap<>();
        out.put(VILLAGE_HOUSE, List.of(
                coins(1, 4, 0.5f),
                pool(2, 4,
                        drop(() -> Items.BREAD, 10, 1, 3), drop(CrewContent.SALTED_FISH, 10, 1, 3),
                        drop(() -> Items.APPLE, 8, 1, 3), drop(() -> Items.POTATO, 8, 2, 5), drop(() -> Items.CARROT, 6, 2, 4),
                        drop(() -> Items.STRING, 5, 1, 3), drop(() -> Items.WHITE_WOOL, 4, 1, 2), drop(() -> Items.CANDLE, 4, 1, 2),
                        drop(TradeContent.CLOTH, 3, 1, 2), drop(CrewContent.LIME, 3, 1, 2), drop(() -> Items.BOOK, 2, 1, 1))));
        out.put(VILLAGE_TAVERN, List.of(
                coins(2, 6, 1.0f),
                pool(2, 4,
                        drop(TradeContent.RUM, 12, 1, 3), drop(() -> Items.BREAD, 8, 1, 3), drop(() -> Items.COOKED_COD, 8, 1, 3),
                        drop(CrewContent.SALT_PORK, 6, 1, 3), drop(CrewContent.HARDTACK, 6, 2, 4), drop(TradeContent.TOBACCO, 5, 1, 3),
                        drop(() -> Items.GLASS_BOTTLE, 4, 1, 3))));
        out.put(VILLAGE_SHIPWRIGHT, List.of(
                coins(1, 3, 0.3f),
                pool(3, 5,
                        drop(AssemblyContent.NAILS, 12, 4, 12), drop(TriangularSailContent.ROPE, 10, 2, 6),
                        drop(TradeContent.CLOTH, 8, 1, 3), drop(() -> Items.SPRUCE_PLANKS, 6, 4, 12),
                        drop(() -> Items.IRON_INGOT, 4, 1, 3), drop(AssemblyContent.CARPENTERS_HAMMER, 2, 1, 1),
                        drop(AssemblyContent.SAW, 2, 1, 1), drop(AssemblyContent.SHIPWRIGHT_TOOLKIT, 1, 1, 1))));
        out.put(OUTPOST_BARRACKS, List.of(
                coins(1, 4, 0.5f),
                pool(2, 4,
                        drop(CrewContent.HARDTACK, 10, 2, 5), drop(CombatContent.LEAD_SHOT, 10, 6, 12),
                        drop(() -> Items.GUNPOWDER, 8, 2, 5), drop(CrewContent.SALT_PORK, 8, 1, 3),
                        drop(CrewContent.LIME, 4, 1, 2), drop(CombatContent.CUTLASS, 2, 1, 1), drop(CombatContent.PISTOL, 1, 1, 1))));
        out.put(OUTPOST_ARMOURY, List.of(
                pool(3, 5,
                        drop(CombatContent.LEAD_SHOT, 12, 8, 16), drop(() -> Items.GUNPOWDER, 10, 3, 8),
                        drop(CombatContent.CANNONBALL, 6, 2, 6), drop(() -> Items.IRON_INGOT, 5, 1, 3),
                        drop(CombatContent.CUTLASS, 4, 1, 1), drop(CombatContent.SABER, 3, 1, 1),
                        drop(CombatContent.PISTOL, 3, 1, 1), drop(CombatContent.MUSKET, 2, 1, 1))));
        out.put(OUTPOST_STORE_HOUSE, List.of(
                pool(3, 5,
                        drop(CrewContent.HARDTACK, 12, 3, 8), drop(CrewContent.SALT_PORK, 10, 2, 6),
                        drop(CrewContent.SALTED_FISH, 8, 2, 6), drop(CrewContent.LIME, 6, 1, 3),
                        drop(() -> Items.GUNPOWDER, 6, 2, 5), drop(CombatContent.LEAD_SHOT, 6, 6, 12),
                        drop(TradeContent.RUM, 4, 1, 2), drop(TriangularSailContent.ROPE, 4, 1, 4))));
        out.put(ISLAND_HUT, List.of(
                coins(1, 6, 0.6f),
                pool(2, 4,
                        drop(TradeContent.RUM, 10, 1, 2), drop(CrewContent.SALTED_FISH, 8, 1, 3),
                        drop(() -> Items.COOKED_COD, 6, 1, 3), drop(TradeContent.TOBACCO, 5, 1, 2),
                        drop(() -> Items.GOLD_NUGGET, 5, 1, 4), drop(CombatContent.LEAD_SHOT, 5, 4, 8),
                        drop(() -> Items.BONE, 3, 1, 2), drop(TradeContent.SPICES, 3, 1, 2), drop(() -> Items.EMERALD, 2, 1, 1))));
        out.put(ISLAND_TAVERN, List.of(
                coins(2, 8, 1.0f),
                pool(2, 4,
                        drop(TradeContent.RUM, 14, 2, 4), drop(TradeContent.TOBACCO, 6, 1, 3),
                        drop(CrewContent.SALT_PORK, 6, 1, 3), drop(() -> Items.COOKED_COD, 6, 1, 3),
                        drop(TradeContent.SPICES, 4, 1, 2), drop(() -> Items.GOLD_NUGGET, 4, 2, 5))));
        out.put(ISLAND_CAPTAIN, List.of(
                // a blank treasure map (TM1): binds to the nearest island's treasure when used
                pool(1, 1, drop(TreasureMapContent.TREASURE_MAP, 1, 1, 1)),
                coins(6, 16, 1.0f),
                pool(1, 1,
                        drop(CombatContent.CUTLASS, 4, 1, 1), drop(CombatContent.PISTOL, 3, 1, 1),
                        drop(CombatContent.SABER, 2, 1, 1), drop(CombatContent.RAPIER, 1, 1, 1)),
                pool(1, 3,
                        drop(TradeContent.RUM, 6, 1, 3), drop(() -> Items.GOLD_INGOT, 3, 1, 2),
                        drop(() -> Items.EMERALD, 3, 1, 2), drop(CombatContent.LEAD_SHOT, 4, 6, 12))));
        return out;
    }

    /** Every item a table can give (for the tests). */
    public static Set<Item> items(ResourceKey<LootTable> key) {
        Set<Item> out = new HashSet<>();
        for (Pool pool : tables().get(key)) {
            for (Drop drop : pool.drops()) out.add(drop.item().get().asItem());
        }
        return out;
    }

    /** Called from the world module's {@code gatherData}. */
    public static void gather(DataContributions data) {
        tables().forEach((key, pools) -> data.encoded(PackOutput.Target.DATA_PACK, "loot_table", key.location(),
                LootTable.DIRECT_CODEC, build(pools)));
    }

    /** The loot table of these pools: chest parameters, every pool's rolls {@link ConfiguredRolls}. */
    public static LootTable build(List<Pool> pools) {
        LootTable.Builder table = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST);
        for (Pool spec : pools) {
            NumberProvider base = spec.min() == spec.max() ? ConstantValue.exactly(spec.min()) : UniformGenerator.between(spec.min(), spec.max());
            LootPool.Builder pool = LootPool.lootPool().setRolls(new ConfiguredRolls(base));
            for (Drop drop : spec.drops()) pool.add(entry(drop.item().get(), drop.weight(), drop.min(), drop.max()));
            if (spec.chance() < 1.0f) pool.when(LootItemRandomChanceCondition.randomChance(spec.chance()));
            table.withPool(pool);
        }
        return table.build();
    }

    // --- builders ------------------------------------------------------------------------------------------------

    private static Pool pool(int min, int max, Drop... drops) {
        return new Pool(min, max, 1.0f, List.of(drops));
    }

    /** {@code min}..{@code max} doubloons with the given chance (1 = always). */
    private static Pool coins(int min, int max, float chance) {
        return new Pool(1, 1, chance, List.of(drop(TradeContent.DOUBLOON, 1, min, max)));
    }

    private static Drop drop(Supplier<? extends ItemLike> item, int weight, int min, int max) {
        return new Drop(item, weight, min, max);
    }

    private static LootPoolSingletonContainer.Builder<?> entry(ItemLike item, int weight, int min, int max) {
        LootPoolSingletonContainer.Builder<?> entry = LootItem.lootTableItem(item).setWeight(weight);
        return min == max && min == 1 ? entry : entry.apply(SetItemCountFunction.setCount(min == max
                ? ConstantValue.exactly(min) : UniformGenerator.between(min, max)));
    }
}
