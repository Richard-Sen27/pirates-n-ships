package com.richardsenger.piratesnships.crew.content;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.crew.galley.PantryBlock;
import com.richardsenger.piratesnships.crew.galley.PantryBlockEntity;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlock;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Ship provisions (design.md §7.4): long-keeping foods, the anti-scurvy lime, and the pantry and water barrel
 * blocks with their block entities (behavior in {@code crew.galley}).
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

    public static final RegistryEntry<Block, PantryBlock> PANTRY = ModRegistry.blockWithItem("pantry", () -> new PantryBlock(woodProps().noOcclusion()));
    public static final RegistryEntry<Block, WaterBarrelBlock> WATER_BARREL = ModRegistry.blockWithItem("water_barrel", () -> new WaterBarrelBlock(woodProps().noOcclusion()));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<PantryBlockEntity>> PANTRY_BLOCK_ENTITY =
            ModRegistry.blockEntity("pantry", PantryBlockEntity::new, PANTRY);
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<WaterBarrelBlockEntity>> WATER_BARREL_BLOCK_ENTITY =
            ModRegistry.blockEntity("water_barrel", WaterBarrelBlockEntity::new, WATER_BARREL);

    /** Water rations in a water barrel item (from a broken barrel). Absent = a full barrel. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<Integer>> WATER_RATIONS = ModRegistry.dataComponent(
            "water_rations", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

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
