package com.richardsenger.piratesnships.crew.galley;

import com.mojang.serialization.DataResult;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.ProvisionClassifier;
import com.richardsenger.piratesnships.crew.provisions.ProvisionRules;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStacks;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import com.richardsenger.piratesnships.crew.provisions.ProvisionUpdate;
import com.richardsenger.piratesnships.crew.provisions.ProvisioningState;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.crew.provisions.SpoiledFood;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The pantry (design.md §6 galley, §7.4): a 27-slot container with a vanilla chest screen. Its {@link ProvisionStore}
 * follows the real stacks (stacks = amounts, store = ages) and is persisted with the items.
 *
 * <p><b>Automation:</b> players may put anything in (the vanilla chest menu cannot filter, and leftovers such as
 * glass bottles have to stay somewhere). Hoppers and other automation may only <em>insert provisions</em> and only
 * <em>extract non-provisions</em> (empty bottles, bowls, buckets, rotten flesh, junk): a hopper chain feeds the galley
 * without filling it with cobblestone, and a hopper below works as a waste chute without draining the food.
 *
 * <p><b>Spoilage</b> runs on the pantry's own clock ({@code agedUntil}, game time): a slow tick every
 * {@link ProvisionsConfig#PANTRY_TICK_INTERVAL} ticks and every read ({@link #catchUp}) advance it with nobody eating.
 * The rules give the same result for any interval.
 */
public class PantryBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ProvisionContainer {

    public static final int SIZE = 27;
    private static final int[] ALL_SLOTS = IntStream.range(0, SIZE).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private ProvisionStore store = ProvisionStore.EMPTY;
    private boolean storeDirty = true;
    /** Game time up to which spoilage was applied; -1 = not yet (the first catch-up only starts the clock). */
    private long agedUntil = -1;
    /** Keeps the block's {@code load} (Sable mass, CW1) in step with the content. */
    private final com.richardsenger.piratesnships.trade.cargo.CargoLoad.Tracker load = new com.richardsenger.piratesnships.trade.cargo.CargoLoad.Tracker();

    public PantryBlockEntity(BlockPos pos, BlockState state) {
        super(CrewContent.PANTRY_BLOCK_ENTITY.get(), pos, state);
    }

    // --- provisions ---------------------------------------------------------------------------------------------

    /** The store in step with the items, without advancing time. */
    public ProvisionStore store(ProvisionSettings s) {
        if (storeDirty) {
            store = ProvisionStacks.toStore(items, s, store);
            storeDirty = false;
        }
        return store;
    }

    public long agedUntil() {
        return agedUntil;
    }

    @Override
    public ProvisionStore catchUp(long gameTime, ProvisionSettings s) {
        ProvisionStore current = store(s);
        if (agedUntil < 0 || gameTime <= agedUntil) {
            agedUntil = Math.max(agedUntil, gameTime);
            return current;
        }
        long ticks = gameTime - agedUntil;
        agedUntil = gameTime;
        if (!s.consumptionEnabled() || !s.spoilageEnabled() || current.isEmpty()) {
            return current;
        }
        ProvisionUpdate u = ProvisionRules.advance(current, ProvisioningState.INITIAL, CrewHeadcount.NOBODY, s, ticks);
        if (u.outcome().spoiled().isEmpty()) {
            store = u.store();
            setChangedQuietly();
            return store;
        }
        ProvisionPool.Share share = new ProvisionPool.Share(u.outcome().consumed(), u.outcome().spoiled());
        applyShare(share, u.store(), gameTime, s);
        return store;
    }

    @Override
    public ProvisionStore storeAt(long anchorTime, ProvisionSettings s) {
        if (agedUntil >= 0 && agedUntil > anchorTime) {
            return s.consumptionEnabled() && s.spoilageEnabled() ? ProvisionPool.rewound(store(s), agedUntil - anchorTime) : store(s);
        }
        return catchUp(anchorTime, s);
    }

    @Override
    public void applyShare(ProvisionPool.Share share, ProvisionStore remaining, long gameTime, ProvisionSettings s) {
        List<ItemStack> extra = new ArrayList<>();
        if (!share.isEmpty()) {
            extra.addAll(ProvisionStacks.apply(this, ProvisionStacks.removals(items, share.removed(), s)));
            if (ProvisionsConfig.SPOILED_FOOD_RESULT.get() == SpoiledFood.ROTTEN_FLESH) {
                int rotten = share.spoiled().values().stream().mapToInt(Integer::intValue).sum();
                while (rotten > 0) {
                    int n = Math.min(rotten, Items.ROTTEN_FLESH.getDefaultMaxStackSize());
                    extra.add(new ItemStack(Items.ROTTEN_FLESH, n));
                    rotten -= n;
                }
            }
        }
        for (ItemStack stack : extra) {
            ItemStack rest = insert(stack);
            if (!rest.isEmpty() && level != null) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, rest);
            }
        }
        store = ProvisionStacks.toStore(items, s, remaining);
        storeDirty = false;
        agedUntil = Math.max(agedUntil, gameTime);
        setChangedQuietly();
    }

    /** Puts a stack into the pantry (merging first), returns what did not fit. */
    ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
            ItemStack in = items.get(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest)) {
                int n = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(n);
                rest.shrink(n);
            }
        }
        for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        return rest;
    }

    /** Marks the block entity for saving and comparators without throwing away the store. */
    private void setChangedQuietly() {
        load.markDirty();
        boolean dirty = storeDirty;
        super.setChanged();
        storeDirty = dirty;
    }

    /** Slow server tick: catches spoilage up every {@link ProvisionsConfig#PANTRY_TICK_INTERVAL} ticks (spread by position). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PantryBlockEntity pantry) {
        long time = level.getGameTime();
        int interval = Math.max(1, ProvisionsConfig.PANTRY_TICK_INTERVAL.get());
        if (pantry.agedUntil < 0 || Math.floorMod(time + pos.asLong(), interval) == 0) {
            pantry.catchUp(time, ProvisionsConfig.settings());
        }
        pantry.load.tick(level, pos, com.richardsenger.piratesnships.trade.cargo.CargoMass.PANTRY,
                () -> com.richardsenger.piratesnships.trade.cargo.CargoWeighing.weigh(pantry, com.richardsenger.piratesnships.trade.TradeService.goods(level)));
    }

    @Override
    public void setChanged() {
        storeDirty = true;
        load.markDirty();
        super.setChanged();
    }

    // --- container ----------------------------------------------------------------------------------------------

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container." + Constants.MOD_ID + ".pantry");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
        storeDirty = true;
        load.markDirty();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    /** Automation inserts provisions only (players are not limited, see the class comment). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isProvision(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return isProvision(stack);
    }

    /** Automation takes out everything that is not a provision (leftovers, rotten flesh, junk). */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return !isProvision(stack);
    }

    public static boolean isProvision(ItemStack stack) {
        return ProvisionClassifier.classify(stack, ProvisionsConfig.settings()).isPresent();
    }

    // --- persistence --------------------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        store = tag.contains("provisions")
                ? ProvisionStore.CODEC.parse(NbtOps.INSTANCE, tag.get("provisions")).result().orElse(ProvisionStore.EMPTY)
                : ProvisionStore.EMPTY;
        agedUntil = tag.contains("aged_until") ? tag.getLong("aged_until") : -1;
        // the stacks stay the authority on amounts; the next read reconciles the store with them
        storeDirty = true;
        load.markDirty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        // stored as last reconciled; reconciling here would need loaded tags, which a save does not guarantee
        DataResult<net.minecraft.nbt.Tag> encoded = ProvisionStore.CODEC.encodeStart(NbtOps.INSTANCE, store);
        encoded.result().ifPresent(t -> tag.put("provisions", t));
        tag.putLong("aged_until", agedUntil);
    }
}
