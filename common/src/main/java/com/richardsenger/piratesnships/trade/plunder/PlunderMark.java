package com.richardsenger.piratesnships.trade.plunder;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import com.mojang.serialization.Codec;

/**
 * The {@code pirates_n_ships:plundered} item data component: a unit marker on stacks taken from captured or sunk
 * ships. Saved and synced. Marked and unmarked stacks don't merge, which keeps plunder visible in inventories.
 * The rule for selling it is {@link PlunderRules}; this class only carries the mark.
 */
public final class PlunderMark {

    public static final RegistryEntry<DataComponentType<?>, DataComponentType<Unit>> PLUNDERED = ModRegistry.dataComponent("plundered",
            b -> b.persistent(Codec.unit(Unit.INSTANCE)).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    private PlunderMark() {
    }

    /** Loads the class so the component is registered. Called from {@code registerContent()}. */
    public static void init() {
    }

    public static boolean isPlundered(ItemStack stack) {
        return stack.has(PLUNDERED.get());
    }

    public static ItemStack mark(ItemStack stack) {
        stack.set(PLUNDERED.get(), Unit.INSTANCE);
        return stack;
    }

    public static ItemStack clear(ItemStack stack) {
        stack.remove(PLUNDERED.get());
        return stack;
    }
}
