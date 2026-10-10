package com.richardsenger.piratesnships.ship.decor.wood;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.BiFunction;

/**
 * A block of a {@link WoodFamily} that knows its wood (WOOD1, docs/woods.md): renderers pick the wood's child model
 * ({@code WoodModels}), recipes and behaviour can ask which wood a placed member is.
 */
public interface WoodBlock {

    /** A wood by its {@link ShipWood#id()} (WOOD3). */
    Codec<ShipWood> WOOD_CODEC = Codec.STRING.comapFlatMap(id -> {
        ShipWood wood = ShipWood.byId(id);
        return wood == null ? DataResult.error(() -> "Unknown wood " + id) : DataResult.success(wood);
    }, ShipWood::id);

    /** The wood this member is made of. */
    ShipWood wood();

    /**
     * The block codec of a family's block class whose constructor takes the wood and the properties (WOOD3): for a
     * vanilla parent that demands a {@code codec()} ({@code HorizontalDirectionalBlock}, {@code BedBlock}, ...).
     */
    static <B extends Block & WoodBlock> MapCodec<B> codec(BiFunction<ShipWood, BlockBehaviour.Properties, B> factory) {
        return RecordCodecBuilder.mapCodec(i -> i.group(WOOD_CODEC.fieldOf("wood").forGetter(WoodBlock::wood),
                // vanilla's propertiesCodec() is protected; its field is this unit codec (properties are not data)
                BlockBehaviour.Properties.CODEC.fieldOf("properties").forGetter(b -> BlockBehaviour.Properties.of()))
                .apply(i, factory::apply));
    }
}
