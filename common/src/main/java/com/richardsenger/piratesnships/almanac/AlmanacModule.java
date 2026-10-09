package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The {@code almanac} module: the Captain's Almanac, the in-game guide (docs/design.md §3.4). ALM1: the book item, the
 * placed tome with its opening animation, and the open/close payloads; the chapters (ALM2), the screen (ALM3) and the
 * Ponder scenes (ALM4) build on them.
 */
public final class AlmanacModule implements ModModule {

    @Override
    public String id() {
        return "almanac";
    }

    @Override
    public void registerConfig() {
        AlmanacConfig.init();
    }

    @Override
    public void registerContent() {
        AlmanacContent.init();
    }

    @Override
    public void registerPayloads() {
        AlmanacReading.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_STOPPED.register(AlmanacReading::onServerStopped);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.almanac.client.AlmanacClientSetup.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(AlmanacContent.ALMANAC, "Captain's Almanac")
                .add(AlmanacItem.TOOLTIP, "Use to read; sneak-use on a block to set it down"));
        data.models(m -> {
            Block tome = AlmanacContent.ALMANAC.get();
            // the renderer draws the whole book (ENTITYBLOCK_ANIMATED): the block model is only the breaking particle
            ResourceLocation model = ModelTemplates.PARTICLE_ONLY.create(tome,
                    TextureMapping.particle(Constants.id("item/captains_almanac")), m.models());
            m.blockStates().accept(MultiVariantGenerator.multiVariant(tome, Variant.variant().with(VariantProperties.MODEL, model)));
            m.flatItem(AlmanacContent.ALMANAC_ITEM.get());
        });
        data.blockLoot(loot -> loot.dropSelf(AlmanacContent.ALMANAC.get()));
        data.recipes(out -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, AlmanacContent.ALMANAC_ITEM.get())
                .requires(Items.BOOK).requires(Items.LEATHER, 2).requires(Items.COPPER_INGOT, 2)
                .unlockedBy("has_book", InventoryChangeTrigger.TriggerInstance.hasItems(Items.BOOK))
                .save(out, AlmanacContent.ALMANAC.id()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(AlmanacGameTests.class);
    }
}
