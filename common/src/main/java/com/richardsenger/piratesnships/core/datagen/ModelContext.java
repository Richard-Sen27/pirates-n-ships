package com.richardsenger.piratesnships.core.datagen;

import com.google.gson.JsonElement;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.blockstates.BlockStateGenerator;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Model datagen context on top of vanilla's generators. Use the public helpers of {@link #blocks()} (e.g.
 * {@code createTrivialCube}), or build block states with {@code MultiVariantGenerator} / {@code ModelTemplates}
 * and write them to {@link #blockStates()} / {@link #models()}. Block items without an explicit item model get one
 * that points at the block model automatically.
 */
public record ModelContext(
        BlockModelGenerators blocks,
        ItemModelGenerators items,
        Consumer<BlockStateGenerator> blockStates,
        BiConsumer<ResourceLocation, Supplier<JsonElement>> models) {

    /** A flat {@code item/generated} model using {@code textures/item/<name>.png}. */
    public void flatItem(Item item) {
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(item), TextureMapping.layer0(item), models);
    }

    /** A handheld (tool-style) item model using {@code textures/item/<name>.png}. */
    public void handheldItem(Item item) {
        ModelTemplates.FLAT_HANDHELD_ITEM.create(ModelLocationUtils.getModelLocation(item), TextureMapping.layer0(item), models);
    }
}
