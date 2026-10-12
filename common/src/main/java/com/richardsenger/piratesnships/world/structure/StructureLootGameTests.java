package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * STR6: loot in the port structures' chests. The pieces' generators write {@code pirates_n_ships:chests/<structure>/<kind>}
 * into their vanilla chests ({@code buildspec.Piece.loot_chest}); datagen writes the tables ({@link StructureLoot}); the
 * processor list every port pool element names ({@code pirates_n_ships:connections}) drops them while
 * {@code world.structures.loot} is off ({@link LootChestProcessor}); {@code world.structures.loot_rolls} scales the rolls
 * ({@link ConfiguredRolls}).
 */
public final class StructureLootGameTests {

    /** The table every chest of a piece names, by piece ({@code <group>/<piece>}); each piece has at least one chest. */
    static final Map<String, ResourceKey<LootTable>> EXPECTED = expected();

    /** One piece per table, placed for real by {@link #placedChestsGiveLootFromTheirTable}. */
    static final List<String> PLACED = List.of("village/house_small", "village/inn", "village/net_loft", "navy_outpost/barracks",
            "navy_outpost/armoury", "navy_outpost/store_house", "pirate_island/tent", "pirate_island/tavern_hut",
            "pirate_island/captains_hut");

    private static Map<String, ResourceKey<LootTable>> expected() {
        Map<String, ResourceKey<LootTable>> out = new LinkedHashMap<>();
        for (String p : List.of("house_small", "house_tall", "house_fisher", "house_garden", "chapel", "warehouse")) {
            out.put("village/" + p, StructureLoot.VILLAGE_HOUSE);
        }
        for (String p : List.of("tavern", "inn")) out.put("village/" + p, StructureLoot.VILLAGE_TAVERN);
        for (String p : List.of("shipwright", "net_loft")) out.put("village/" + p, StructureLoot.VILLAGE_SHIPWRIGHT);
        for (String p : List.of("barracks", "barracks_long", "barracks_mess", "brig", "brig_large", "officers_quarters", "fort_gate")) {
            out.put("navy_outpost/" + p, StructureLoot.OUTPOST_BARRACKS);
        }
        out.put("navy_outpost/armoury", StructureLoot.OUTPOST_ARMOURY);
        for (String p : List.of("store_house", "wall_tower", "round_tower")) out.put("navy_outpost/" + p, StructureLoot.OUTPOST_STORE_HOUSE);
        for (String p : List.of("camp_start", "tent", "tent_large", "hut_porch", "hut_stilts", "hut_thatch")) {
            out.put("pirate_island/" + p, StructureLoot.ISLAND_HUT);
        }
        for (String p : List.of("tavern_hut", "tavern_deck", "tavern_long")) out.put("pirate_island/" + p, StructureLoot.ISLAND_TAVERN);
        for (String p : List.of("captains_hut", "captains_lookout", "captains_cabin")) {
            out.put("pirate_island/" + p, StructureLoot.ISLAND_CAPTAIN);
        }
        return out;
    }

    private StructureLootGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(StructureLootGameTests.class);
    }

    /**
     * Every chest of the listed pieces names its piece's table, every loot table any template of ours names is loaded,
     * and the generated tables are exactly {@link StructureLoot#tables}.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_3)
    public static void everyLootChestNamesALoadedTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        List<String> problems = new ArrayList<>();
        Map<String, Set<String>> tablesByPiece = new TreeMap<>();
        manager.listTemplates().filter(id -> id.getNamespace().equals(Constants.MOD_ID)).forEach(id -> {
            StructureTemplate template = manager.get(id).orElse(null);
            if (template == null) return;
            for (String table : lootTables(template)) {
                tablesByPiece.computeIfAbsent(id.getPath(), k -> new TreeSet<>()).add(table);
                LootTable loaded = level.getServer().reloadableRegistries()
                        .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(table)));
                if (loaded == LootTable.EMPTY) problems.add(id + " names " + table + ", which is not loaded");
            }
        });
        EXPECTED.forEach((piece, table) -> {
            Set<String> found = tablesByPiece.get(piece);
            if (found == null || !found.equals(Set.of(table.location().toString()))) {
                problems.add(piece + ": chests name " + found + ", expected only " + table.location());
            }
        });
        tablesByPiece.forEach((piece, tables) -> {
            for (String table : tables) {
                if (StructureLoot.isStructureTable(table) && !EXPECTED.containsKey(piece)) {
                    problems.add(piece + " names " + table + " but is not listed in EXPECTED");
                }
            }
        });
        for (ResourceKey<LootTable> key : StructureLoot.tables().keySet()) {
            if (level.getServer().reloadableRegistries().getLootTable(key) == LootTable.EMPTY) problems.add(key.location() + " is not loaded");
        }
        helper.assertTrue(problems.isEmpty(), problems.size() + " loot chest problems: " + problems);
        helper.succeed();
    }

    /**
     * One piece per table placed through the processor list the pools name: its chests carry the table, and the first
     * one opened holds loot from that table only.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200)
    public static void placedChestsGiveLootFromTheirTable(GameTestHelper helper) {
        for (int i = 0; i < PLACED.size(); i++) {
            String piece = PLACED.get(i);
            ResourceKey<LootTable> table = EXPECTED.get(piece);
            List<ChestBlockEntity> chests = place(helper, piece, new BlockPos(1 + (i % 3) * 15, 1, 1 + (i / 3) * 15));
            helper.assertTrue(!chests.isEmpty(), piece + ": a chest");
            for (ChestBlockEntity chest : chests) {
                helper.assertValueEqual(chest.getLootTable(), table, piece + ": the chest at " + chest.getBlockPos().toShortString());
            }
            ChestBlockEntity chest = chests.get(0);
            chest.unpackLootTable(null);
            List<ItemStack> items = contents(chest);
            helper.assertTrue(!items.isEmpty(), piece + ": loot in the opened chest");
            Set<Item> allowed = StructureLoot.items(table);
            for (ItemStack stack : items) {
                helper.assertTrue(allowed.contains(stack.getItem()), piece + ": " + stack + " is not in " + table.location());
            }
        }
        helper.succeed();
    }

    /** With {@code world.structures.loot} off a placed chest has no table and opens empty (own batch: writes the config). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = "pirates_n_ships_config_world_loot")
    public static void lootOffPlacesEmptyChests(GameTestHelper helper) {
        ConfigOverrides.during(helper, WorldConfig.STRUCTURE_LOOT, false);
        List<ChestBlockEntity> chests = place(helper, "village/house_small", new BlockPos(1, 1, 1));
        helper.assertTrue(!chests.isEmpty(), "a chest");
        for (ChestBlockEntity chest : chests) {
            helper.assertTrue(chest.getLootTable() == null, "no loot table with loot off: " + chest.getLootTable());
            chest.unpackLootTable(null);
            helper.assertTrue(contents(chest).isEmpty(), "the chest opens empty");
        }
        helper.succeed();
    }

    /** {@code world.structures.loot_rolls} scales the rolls: 0 opens empty, 4 gives far more than 1 can (own batch). */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = "pirates_n_ships_config_world_loot_rolls")
    public static void lootRollsScaleTheRolls(GameTestHelper helper) {
        ConfigOverrides.Handle<Double> none = ConfigOverrides.during(helper, WorldConfig.STRUCTURE_LOOT_ROLLS, 0.0);
        List<ChestBlockEntity> chests = place(helper, "navy_outpost/armoury", new BlockPos(1, 1, 1));
        helper.assertTrue(!chests.isEmpty(), "a chest");
        chests.get(0).unpackLootTable(null);
        helper.assertTrue(contents(chests.get(0)).isEmpty(), "scale 0: the chest opens empty");
        none.restore();
        ConfigOverrides.during(helper, WorldConfig.STRUCTURE_LOOT_ROLLS, 4.0);
        List<ChestBlockEntity> again = place(helper, "navy_outpost/armoury", new BlockPos(1, 1, 1));
        again.get(0).unpackLootTable(null);
        // the armoury rolls 3..5 at scale 1; at 4 it rolls 12..20, and every roll fills at least one slot
        int slots = contents(again.get(0)).size();
        helper.assertTrue(slots >= 12, "scale 4: at least 12 stacks, got " + slots);
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    /** Places the template at {@code at} (relative) through the connections processor list; its chests, in order. */
    private static List<ChestBlockEntity> place(GameTestHelper helper, String piece, BlockPos at) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().get(Constants.id(piece))
                .orElseThrow(() -> new IllegalStateException("no template " + piece));
        StructurePlaceSettings settings = new StructurePlaceSettings();
        level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(ConnectionsProcessor.LIST)
                .value().list().forEach(settings::addProcessor);
        BlockPos origin = helper.absolutePos(at);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        BoundingBox box = template.getBoundingBox(settings, origin);
        List<ChestBlockEntity> out = new ArrayList<>();
        BlockPos.betweenClosedStream(box).forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) out.add(chest);
        });
        return out;
    }

    private static List<ItemStack> contents(ChestBlockEntity chest) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (!chest.getItem(i).isEmpty()) out.add(chest.getItem(i));
        }
        return out;
    }

    /** The loot tables a template's blocks name in their data. */
    private static List<String> lootTables(StructureTemplate template) {
        CompoundTag saved = template.save(new CompoundTag());
        ListTag blocks = saved.getList("blocks", Tag.TAG_COMPOUND);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (block.contains("nbt", Tag.TAG_COMPOUND) && block.getCompound("nbt").contains("LootTable", Tag.TAG_STRING)) {
                out.add(block.getCompound("nbt").getString("LootTable"));
            }
        }
        return out;
    }
}
