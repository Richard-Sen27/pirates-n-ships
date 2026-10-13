package com.richardsenger.piratesnships.chart.sample;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;
import com.richardsenger.piratesnships.core.datagen.ModTagsProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Datagen of the block tag {@code pirates_n_ships:chart_built} (CHART5), the blocks that mark a chart cell as built.
 * Natural blocks are never in it: no logs or leaves (a forest stays natural), no stone, sand, dirt or grass, and none
 * of the terracotta colours that badlands generate (plain, white, orange, yellow, brown, red, light gray).
 */
public final class ChartBuiltData {

    private ChartBuiltData() {
    }

    public static void contribute(ModTagsProvider<Block> tags) {
        var t = tags.tag(ChartBuilt.TAG);
        t.addTag(BlockTags.PLANKS).addTag(BlockTags.WOODEN_SLABS).addTag(BlockTags.WOODEN_STAIRS)
                .addTag(BlockTags.FENCES).addTag(BlockTags.FENCE_GATES).addTag(BlockTags.DOORS).addTag(BlockTags.TRAPDOORS)
                .addTag(BlockTags.WOOL).addTag(BlockTags.WOOL_CARPETS).addTag(BlockTags.STONE_BRICKS)
                .addTag(BlockTags.WALLS).addTag(BlockTags.BEDS).addTag(BlockTags.CAMPFIRES);
        t.add(Blocks.BRICKS, Blocks.BRICK_SLAB, Blocks.BRICK_STAIRS, Blocks.BRICK_WALL,
                Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS,
                Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_WALL,
                Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_WALL,
                Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB,
                Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS,
                Blocks.DIRT_PATH, Blocks.LANTERN, Blocks.SOUL_LANTERN,
                Blocks.BARREL, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.CRAFTING_TABLE,
                Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BOOKSHELF, Blocks.HAY_BLOCK, Blocks.SCAFFOLDING);
        for (DyeColor colour : DyeColor.values()) {
            t.add(BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_stained_glass")),
                    BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_stained_glass_pane")),
                    BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_glazed_terracotta")),
                    BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_concrete")),
                    BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_concrete_powder")));
            if (!NATURAL_TERRACOTTA.contains(colour)) t.add(BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_terracotta")));
        }
        // every block of this mod is man-made: decks, railings, cleats, helms, boards, desks, stalls, cots, name boards
        BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> Constants.MOD_ID.equals(id.getNamespace()))
                .sorted()
                .forEach(id -> t.add(BuiltInRegistries.BLOCK.get(id)));
    }

    /** The terracotta colours that badlands generate on their own. */
    private static final java.util.Set<DyeColor> NATURAL_TERRACOTTA = java.util.EnumSet.of(
            DyeColor.WHITE, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.BROWN, DyeColor.RED, DyeColor.LIGHT_GRAY);
}
