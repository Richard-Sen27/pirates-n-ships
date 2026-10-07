package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The placed sea chest's 54 slots. {@link BaseContainerBlockEntity} already moves the contents and the custom name
 * between the block entity and the item's {@code container} / {@code custom_name} components (placing the item,
 * the loot table's component copy, pick-block), exactly like the shulker box.
 */
public class SeaChestBlockEntity extends BaseContainerBlockEntity {

    public static final String TITLE_KEY = "container." + Constants.MOD_ID + ".sea_chest";

    private NonNullList<ItemStack> items = SeaChestContents.emptySlots();

    public SeaChestBlockEntity(BlockPos pos, BlockState state) {
        super(SeaChestContent.BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SeaChestContents.SIZE;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return SeaChestContents.canHold(stack);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SeaChestMenu(containerId, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 0.9f);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 0.9f);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = SeaChestContents.emptySlots();
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, false, registries);
    }
}
