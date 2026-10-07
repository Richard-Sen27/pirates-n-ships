package com.richardsenger.piratesnships.trade.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bulk cargo container: a {@link BulkStore} plus a two-slot vanilla {@link Container} view for hoppers and other
 * container code. Slot 0 is the input (always empty unless something forced in more than fits; that residue stays
 * there and is drained when room appears), slot 1 shows up to one stack of the content for extraction.
 *
 * <p>Vanilla code mutates the returned stacks directly ({@code removeItem} splits the output stack, a failed hopper
 * move puts the count back with {@code setCount}), so the view is reconciled lazily: before every access the
 * difference between the published output count and the output stack's current count is taken from the store.
 */
public class CargoContainerBlockEntity extends BlockEntity implements Container {

    public static final int INPUT = 0;
    public static final int OUTPUT = 1;

    private final BulkStore store = new BulkStore();
    private ItemStack input = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private int published;
    private final CargoLoad.Tracker load = new CargoLoad.Tracker();

    public CargoContainerBlockEntity(BlockPos pos, BlockState state) {
        super(CargoContainers.BLOCK_ENTITY.get(), pos, state);
    }

    public CargoContainers.Kind kind() {
        return getBlockState().getBlock() instanceof CargoContainerBlock b ? b.kind() : CargoContainers.Kind.CRATE;
    }

    public BulkStore.Rules rules() {
        return kind().rules();
    }

    // --- Store API (use these; the Container methods are for vanilla code) --------------------------------------

    /** The content, reconciled. Mutate only through the methods below. */
    public BulkCargo cargo() {
        sync();
        return BulkCargo.of(store);
    }

    public ItemStack heldKind() {
        sync();
        return store.kind();
    }

    public int count() {
        sync();
        return store.count();
    }

    public int capacity() {
        sync();
        return store.isEmpty() ? 0 : store.capacity(rules());
    }

    public BulkStore.Refusal check(ItemStack stack) {
        sync();
        return store.check(stack, rules());
    }

    public int room(ItemStack stack) {
        sync();
        return store.room(stack, rules());
    }

    /** Moves as much of {@code stack} in as fits and shrinks it accordingly; returns how many went in. */
    public int insert(ItemStack stack) {
        sync();
        int n = store.insert(stack, rules(), false);
        if (n > 0) {
            stack.shrink(n);
            changed();
        }
        return n;
    }

    /** All {@code n} of the kind or nothing. */
    public boolean insertAll(ItemStack kindStack, int n) {
        sync();
        if (!store.insertAll(kindStack, n, rules())) return false;
        changed();
        return true;
    }

    /** Removes up to {@code max} items as one (possibly oversized) stack. */
    public ItemStack extract(int max) {
        sync();
        ItemStack out = store.extract(max, false);
        if (!out.isEmpty()) changed();
        return out;
    }

    public int signal() {
        sync();
        return store.signal(rules());
    }

    /** Stack residue that didn't fit (normally empty); dropped when the block is broken. */
    public ItemStack residue() {
        sync();
        return input;
    }

    // --- Reconciliation -----------------------------------------------------------------------------------------

    private void sync() {
        boolean dirty = false;
        if (published > 0 || !output.isEmpty()) {
            int now = output.isEmpty() || !store.isSameKind(output) ? 0 : output.getCount();
            int delta = published - now;
            if (delta != 0) {
                ItemStack k = store.kind();
                int left = store.count() - delta;
                store.set(k, Math.max(0, left));
                dirty = true;
            }
        }
        if (!input.isEmpty()) {
            int n = store.insert(input, rules(), false);
            if (n > 0) {
                input = input.copyWithCount(input.getCount() - n);
                if (input.getCount() <= 0) input = ItemStack.EMPTY;
                dirty = true;
            }
        }
        publish();
        if (dirty) {
            load.markDirty();
            super.setChanged();
        }
    }

    private void publish() {
        if (store.isEmpty()) {
            output = ItemStack.EMPTY;
            published = 0;
        } else {
            int n = Math.min(store.count(), store.kindView().getMaxStackSize());
            output = store.kind().copyWithCount(n);
            published = n;
        }
    }

    private void changed() {
        publish();
        setChanged();
    }

    /** Server tick: keeps {@link CargoLoad#LOAD} in step with the content (debounced). */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CargoContainerBlockEntity be) {
        CargoMass.Profile profile = state.getBlock() instanceof CargoContainerBlock b ? b.massProfile() : CargoMass.CRATE;
        be.load.tick(level, pos, profile, () -> CargoWeighing.weigh(be, com.richardsenger.piratesnships.trade.TradeService.goods(level)));
    }

    @Override
    public void setChanged() {
        load.markDirty();
        sync();
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        super.setChanged();
    }

    // --- Container ----------------------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        int pending = published - (output.isEmpty() ? 0 : output.getCount());
        return store.count() - pending <= 0 && input.isEmpty();
    }

    // getItem and removeItem don't reconcile: a hopper puts a failed move back through the returned stack and calls
    // setChanged() only on success, so the output stack object must stay the same until then
    @Override
    public ItemStack getItem(int slot) {
        return slot == INPUT ? input : slot == OUTPUT ? output : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack out = slot == INPUT ? input.split(amount) : slot == OUTPUT ? output.split(amount) : ItemStack.EMPTY;
        if (!out.isEmpty()) {
            load.markDirty();
            super.setChanged();
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, Integer.MAX_VALUE);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        sync();
        if (slot == INPUT) {
            input = stack;
        } else if (slot == OUTPUT) {
            // Replace the shown stack: the old shown items leave, the new ones (if they fit) arrive
            store.extract(published, false);
            if (!stack.isEmpty()) {
                int n = store.insert(stack, rules(), false);
                if (n < stack.getCount()) input = stack.copyWithCount(stack.getCount() - n);
            }
        }
        changed();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        sync();
        return slot == INPUT && input.isEmpty() && store.room(stack, rules()) >= stack.getCount();
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return slot == OUTPUT || slot == INPUT;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        store.clear();
        input = ItemStack.EMPTY;
        publish();
        setChanged();
    }

    // --- Save, components ---------------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        RegistryOps<net.minecraft.nbt.Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        store.clear();
        input = ItemStack.EMPTY;
        if (tag.contains("cargo")) {
            BulkCargo.CODEC.parse(ops, tag.get("cargo")).result().ifPresent(c -> store.set(c.kind(), c.count()));
        }
        if (tag.contains("residue")) input = ItemStack.parseOptional(registries, tag.getCompound("residue"));
        publish();
        load.markDirty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        sync();
        RegistryOps<net.minecraft.nbt.Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (!store.isEmpty()) BulkCargo.CODEC.encodeStart(ops, BulkCargo.of(store)).result().ifPresent(t -> tag.put("cargo", t));
        if (!input.isEmpty()) tag.put("residue", input.save(registries));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        input.get(DataComponents.MAX_STACK_SIZE);
        BulkCargo c = input.get(CargoContainers.BULK_CARGO.get());
        store.clear();
        if (c != null) store.set(c.kind(), c.count());
        publish();
        load.markDirty();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        sync();
        if (!store.isEmpty()) {
            components.set(CargoContainers.BULK_CARGO.get(), BulkCargo.of(store));
            components.set(DataComponents.MAX_STACK_SIZE, 1);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("cargo");
    }

    /** Lets the block drop residue when broken (the bulk content goes with the item, see the loot table). */
    void dropResidue() {
        if (level != null && !input.isEmpty()) {
            Block.popResource(level, worldPosition, input);
            input = ItemStack.EMPTY;
        }
    }
}
