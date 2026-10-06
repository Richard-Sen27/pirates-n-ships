package com.richardsenger.piratesnships.core.registry;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Typed registration helpers for feature modules. Declare entries as {@code static final} fields in the module's
 * own registration class and trigger it from {@code ModModule.registerContent()}:
 *
 * <pre>{@code
 * public final class ShipBlocks {
 *     public static final RegistryEntry<Block, HelmBlock> HELM = ModRegistry.blockWithItem("helm", () -> new HelmBlock(props()));
 *     public static void init() {}
 * }
 * }</pre>
 *
 * Every item registered here is shown in the mod's creative tab, in registration order.
 */
public final class ModRegistry {

    private static final List<RegistryEntry<Item, ? extends Item>> ITEMS = new ArrayList<>();
    private static final List<RegistryEntry<Block, ? extends Block>> BLOCKS = new ArrayList<>();

    private ModRegistry() {
    }

    public static synchronized <B extends Block> RegistryEntry<Block, B> block(String name, Supplier<B> factory) {
        RegistryEntry<Block, B> entry = Services.REGISTRY.register(Registries.BLOCK, name, factory);
        BLOCKS.add(entry);
        return entry;
    }

    /** A block plus a plain {@link BlockItem} with the same name. */
    public static <B extends Block> RegistryEntry<Block, B> blockWithItem(String name, Supplier<B> factory) {
        RegistryEntry<Block, B> block = block(name, factory);
        item(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static synchronized <I extends Item> RegistryEntry<Item, I> item(String name, Supplier<I> factory) {
        RegistryEntry<Item, I> entry = Services.REGISTRY.register(Registries.ITEM, name, factory);
        ITEMS.add(entry);
        return entry;
    }

    @SafeVarargs
    public static <T extends BlockEntity> RegistryEntry<BlockEntityType<?>, BlockEntityType<T>> blockEntity(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... blocks) {
        return Services.REGISTRY.register(Registries.BLOCK_ENTITY_TYPE, name, () -> {
            Block[] valid = new Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) valid[i] = blocks[i].get();
            return BlockEntityType.Builder.of(factory, valid).build(null);
        });
    }

    /** An entity type. Living entities also need {@code Services.REGISTRY.registerEntityAttributes}. */
    public static <E extends Entity> RegistryEntry<EntityType<?>, EntityType<E>> entity(String name, Supplier<EntityType.Builder<E>> builder) {
        return Services.REGISTRY.register(Registries.ENTITY_TYPE, name, () -> builder.get().build(Constants.MOD_ID + ":" + name));
    }

    public static RegistryEntry<SoundEvent, SoundEvent> sound(String name) {
        return Services.REGISTRY.register(Registries.SOUND_EVENT, name, () -> SoundEvent.createVariableRangeEvent(Constants.id(name)));
    }

    public static <T> RegistryEntry<DataComponentType<?>, DataComponentType<T>> dataComponent(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return Services.REGISTRY.register(Registries.DATA_COMPONENT_TYPE, name, () -> builder.apply(DataComponentType.builder()).build());
    }

    public static <M extends AbstractContainerMenu> RegistryEntry<MenuType<?>, MenuType<M>> menu(String name, Supplier<MenuType<M>> factory) {
        return Services.REGISTRY.register(Registries.MENU, name, factory);
    }

    public static RegistryEntry<CreativeModeTab, CreativeModeTab> creativeTab(String name, Supplier<CreativeModeTab> factory) {
        return Services.REGISTRY.register(Registries.CREATIVE_MODE_TAB, name, factory);
    }

    /** Every item registered through this class, in registration order. */
    public static synchronized List<RegistryEntry<Item, ? extends Item>> items() {
        return List.copyOf(ITEMS);
    }

    /** Every block registered through this class, in registration order. */
    public static synchronized List<RegistryEntry<Block, ? extends Block>> blocks() {
        return List.copyOf(BLOCKS);
    }
}
