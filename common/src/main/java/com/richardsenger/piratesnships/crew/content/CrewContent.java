package com.richardsenger.piratesnships.crew.content;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.crew.galley.PantryBlock;
import com.richardsenger.piratesnships.crew.galley.PantryBlockEntity;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlock;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlockEntity;
import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import com.richardsenger.piratesnships.crew.hammock.HammockItem;
import com.richardsenger.piratesnships.crew.hammock.HammockSeat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.material.PushReaction;
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
 * blocks with their block entities (behavior in {@code crew.galley}); the hammock, its item and the invisible seat a
 * sleeping crew member lies on (HM1, design.md §7.1, behavior in {@code crew.hammock}).
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

    /** The crew's bunk (HM1): a two-block canvas bed hung between two supports. Undyed canvas; colours come later. */
    public static final RegistryEntry<Block, HammockBlock> HAMMOCK = ModRegistry.block("hammock", () -> new HammockBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).sound(SoundType.WOOL).strength(0.8f).noOcclusion()
                    .ignitedByLava().pushReaction(PushReaction.DESTROY)));
    public static final RegistryEntry<Item, HammockItem> HAMMOCK_ITEM = ModRegistry.item("hammock",
            () -> new HammockItem(HAMMOCK.get(), new Item.Properties().stacksTo(16)));
    /** The invisible place a sleeping crew member lies on, inside the ship's plot (like the station seat). */
    public static final RegistryEntry<EntityType<?>, EntityType<HammockSeat>> HAMMOCK_SEAT = ModRegistry.entity("hammock_seat",
            () -> EntityType.Builder.<HammockSeat>of(HammockSeat::new, MobCategory.MISC)
                    .sized(0.25f, 0.01f).noSummon().fireImmune().clientTrackingRange(10).updateInterval(1));

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
