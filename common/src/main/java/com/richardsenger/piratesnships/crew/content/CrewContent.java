package com.richardsenger.piratesnships.crew.content;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Ship provisions (design.md §7.4): long-keeping foods, the anti-scurvy lime, and the pantry and water barrel
 * blocks. The blocks are plain solid blocks for now; they become containers in a later package.
 */
public final class CrewContent {

    public static final FoodProperties HARDTACK_FOOD = new FoodProperties.Builder().nutrition(4).saturationModifier(0.4f).build();
    public static final FoodProperties SALTED_FISH_FOOD = new FoodProperties.Builder().nutrition(5).saturationModifier(0.5f).build();
    public static final FoodProperties SALT_PORK_FOOD = new FoodProperties.Builder().nutrition(6).saturationModifier(0.6f).build();
    public static final FoodProperties LIME_FOOD = new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build();

    public static final RegistryEntry<Item, Item> HARDTACK = food("hardtack", HARDTACK_FOOD);
    public static final RegistryEntry<Item, Item> SALTED_FISH = food("salted_fish", SALTED_FISH_FOOD);
    public static final RegistryEntry<Item, Item> SALT_PORK = food("salt_pork", SALT_PORK_FOOD);
    public static final RegistryEntry<Item, Item> LIME = food("lime", LIME_FOOD);

    public static final RegistryEntry<Block, Block> PANTRY = ModRegistry.blockWithItem("pantry", () -> new Block(woodProps()));
    public static final RegistryEntry<Block, Block> WATER_BARREL = ModRegistry.blockWithItem("water_barrel", () -> new Block(woodProps()));

    private CrewContent() {
    }

    private static RegistryEntry<Item, Item> food(String name, FoodProperties food) {
        return ModRegistry.item(name, () -> new Item(new Item.Properties().food(food)));
    }

    /** Like a vanilla barrel. */
    private static BlockBehaviour.Properties woodProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                .strength(2.5f).sound(SoundType.WOOD).ignitedByLava();
    }

    public static void init() {
    }
}
