package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.util.function.Supplier;

/**
 * Registration of the sea chest (docs/design.md §11): the block in every wood (WOOD2) with its 54-slot block entity,
 * the wearable item and the floating entity (which carries the wood it was launched from).
 */
public final class SeaChestContent {

    /**
     * The sea chest in every wood (WOOD2, docs/woods.md): {@code sea_chest} is dark oak's legacy id, the others
     * {@code <wood>_sea_chest}; each member's item is a wearable {@link SeaChestItem}. Not flammable, so a fire on deck
     * doesn't take the contents with it (the woods' colour and sound, but no {@code ignitedByLava}).
     */
    public static final WoodFamily<SeaChestBlock> CHESTS = WoodFamily.register("sea_chest", ShipWood.DARK_OAK,
            wood -> new SeaChestBlock(wood, BlockBehaviour.Properties.of().mapColor(wood.mapColor()).instrument(NoteBlockInstrument.BASS)
                    .strength(2.5f).sound(wood.sound())),
            block -> new SeaChestItem(block, new Item.Properties().stacksTo(1)));

    /** The legacy dark oak chest (the family's lead). */
    public static final RegistryEntry<Block, SeaChestBlock> BLOCK = CHESTS.lead();

    /** The legacy dark oak chest's item. */
    public static final Supplier<SeaChestItem> ITEM = () -> item(ShipWood.DARK_OAK);

    /** One block entity type for every wood's chest. */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<SeaChestBlockEntity>> BLOCK_ENTITY =
            CHESTS.blockEntity("sea_chest", SeaChestBlockEntity::new);

    /** Seats its holder on a floating sea chest and paddles it (SC2). */
    public static final RegistryEntry<Item, PaddleItem> PADDLE = ModRegistry.item("paddle",
            () -> new PaddleItem(new Item.Properties().stacksTo(1)));

    /** 0.875 wide like the block model's footprint when floating, 0.75 high. */
    public static final RegistryEntry<EntityType<?>, EntityType<SeaChestEntity>> ENTITY = ModRegistry.entity("sea_chest",
            () -> EntityType.Builder.<SeaChestEntity>of(SeaChestEntity::new, MobCategory.MISC).sized(0.875f, 0.75f)
                    .fireImmune().clientTrackingRange(10).updateInterval(3));

    private SeaChestContent() {
    }

    /** The item of {@code wood}'s sea chest. */
    public static SeaChestItem item(ShipWood wood) {
        return (SeaChestItem) CHESTS.get(wood).asItem();
    }

    /** The chest of {@code wood}, or the legacy dark oak one for a wood without a member (its mod gone). */
    public static SeaChestBlock block(@org.jetbrains.annotations.Nullable ShipWood wood) {
        return wood != null && CHESTS.woods().contains(wood) ? CHESTS.get(wood) : BLOCK.get();
    }

    /** Loads the class so the entries are registered; exposes the block's 54 slots to other mods' pipes. */
    public static void init() {
        Services.CAPABILITIES.registerBlockContainer(BLOCK_ENTITY);
    }
}
