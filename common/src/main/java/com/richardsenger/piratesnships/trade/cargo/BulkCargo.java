package com.richardsenger.piratesnships.trade.cargo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * The content of a bulk container as one value: block entity save data and the {@code pirates_n_ships:bulk_cargo}
 * component on a broken crate's item (so a crate keeps its cargo when broken, like a shulker box). Equality compares
 * the kind by item and components, because {@link ItemStack} has no {@code equals}.
 */
public record BulkCargo(ItemStack kind, int count) {

    public static final Codec<BulkCargo> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("kind").forGetter(BulkCargo::kind),
            ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(BulkCargo::count)
    ).apply(i, BulkCargo::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BulkCargo> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, BulkCargo::kind, ByteBufCodecs.VAR_INT, BulkCargo::count, BulkCargo::new);

    public BulkCargo {
        kind = kind.copyWithCount(1);
    }

    public static BulkCargo of(BulkStore store) {
        return new BulkCargo(store.kind(), store.count());
    }

    public BulkStore toStore() {
        return new BulkStore(kind, count);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BulkCargo b && count == b.count && ItemStack.isSameItemSameComponents(kind, b.kind);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(kind) + count;
    }
}
