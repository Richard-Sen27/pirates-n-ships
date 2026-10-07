package com.richardsenger.piratesnships.combat.grapple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code grapple_loaded} data component (GR3, docs/design.md §8.3): the grappling hook sitting in the musket (the
 * only launcher since GR4). The hook item travels with all its own data and comes out again with the fired hook.
 *
 * @param hook  the hook item (one)
 * @param taken whether it was taken from the player's inventory (not in creative), so the fired hook is given back
 *              when it is released
 */
public record LoadedHook(ItemStack hook, boolean taken) {

    public static final Codec<LoadedHook> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("hook").forGetter(LoadedHook::hook),
            Codec.BOOL.optionalFieldOf("taken", true).forGetter(LoadedHook::taken)
    ).apply(i, LoadedHook::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadedHook> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, LoadedHook::hook,
            ByteBufCodecs.BOOL, LoadedHook::taken,
            LoadedHook::new);

    public LoadedHook {
        hook = hook.copyWithCount(1);
    }

    /** Data components need value equality; {@link ItemStack} has none of its own. */
    @Override
    public boolean equals(Object o) {
        return o instanceof LoadedHook other && taken == other.taken && ItemStack.matches(hook, other.hook);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(hook) + Boolean.hashCode(taken);
    }
}
