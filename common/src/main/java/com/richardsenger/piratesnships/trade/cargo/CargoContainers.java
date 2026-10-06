package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Locale;

/**
 * Registration of the bulk cargo containers (design.md §10.3, §4.9). The blocks themselves stay registered in
 * {@code ship.decor.ShipDecor} ({@code cargo_crate}, {@code cargo_barrel}); this class adds their block entity type,
 * the {@code bulk_cargo} item component and the capacity rules.
 */
public final class CargoContainers {

    /** Crates count capacity in stacks of the held item; barrels in items (so small stacks like rum fit better). */
    public enum Kind implements StringRepresentable {
        CRATE, BARREL;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        public int capacity(ItemStack kindStack) {
            return switch (this) {
                case CRATE -> BulkStore.stacksCapacity(kindStack, TradeConfig.CRATE_CAPACITY_STACKS.get());
                case BARREL -> TradeConfig.BARREL_CAPACITY_ITEMS.get();
            };
        }

        public BulkStore.Rules rules() {
            return new BulkStore.Rules(this::capacity, CargoContainers::accepts, PlunderMark::isPlundered);
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final RegistryEntry<DataComponentType<?>, DataComponentType<BulkCargo>> BULK_CARGO = ModRegistry.dataComponent("bulk_cargo",
            b -> b.persistent(BulkCargo.CODEC).networkSynchronized(BulkCargo.STREAM_CODEC));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<CargoContainerBlockEntity>> BLOCK_ENTITY = ModRegistry.blockEntity(
            "cargo_container", CargoContainerBlockEntity::new, ShipDecor.CARGO_CRATE, ShipDecor.CARGO_BARREL);

    private CargoContainers() {
    }

    /**
     * Loads the class so the entries are registered, and exposes the two-slot container view to other mods' pipes
     * (item handler capability / transfer API). Called from {@code registerContent()}.
     */
    public static void init() {
        Services.CAPABILITIES.registerBlockContainer(BLOCK_ENTITY);
    }

    /** What an empty container accepts (config {@code containers.accepts}); stackability is checked by {@link BulkStore}. */
    public static boolean accepts(ItemStack stack) {
        return switch (TradeConfig.CONTAINER_ACCEPTS.get()) {
            case ANY_STACKABLE -> true;
            case TRADE_GOODS_ONLY -> TradeService.goods(false).of(stack).isPresent();
        };
    }

    /** Loot table: the block with its cargo ({@code bulk_cargo}, and stack size 1 while filled). Not lost in explosions. */
    public static LootTable.Builder lootTable(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(block).apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                        .include(BULK_CARGO.get()).include(DataComponents.MAX_STACK_SIZE).include(DataComponents.CUSTOM_NAME))));
    }
}
