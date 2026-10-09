package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.data.AlmanacData;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code almanac} module: the Captain's Almanac, the in-game guide (docs/design.md §3.4). ALM1: the book item, the
 * placed tome with its opening animation, and the open/close payloads; ALM2: the chapters, their sync and the last
 * page per player ({@link AlmanacData}, hooked in here); the screen (ALM3) and the Ponder scenes (ALM4) build on them.
 * GUIDE2: a new player gets one Almanac on first join ({@link AlmanacGift}).
 */
public final class AlmanacModule implements ModModule {

    @Override
    public String id() {
        return "almanac";
    }

    @Override
    public void registerConfig() {
        AlmanacConfig.init();
        AlmanacData.registerConfig();
    }

    @Override
    public void registerContent() {
        AlmanacContent.init();
        AlmanacGift.init();
        AlmanacData.registerContent();
    }

    @Override
    public void registerPayloads() {
        AlmanacReading.registerPayloads();
        AlmanacData.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_STOPPED.register(AlmanacReading::onServerStopped);
        CommonEvents.PLAYER_LOGIN.register(AlmanacGift::onLogin);
        AlmanacData.registerEvents();
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.almanac.client.AlmanacClientSetup.init();
        AlmanacData.initClient();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(AlmanacContent.ALMANAC, "Captain's Almanac")
                .add(AlmanacItem.TOOLTIP, "Use to read; sneak-use on a block to set it down"));
        data.models(m -> {
            Block tome = AlmanacContent.ALMANAC.get();
            // the renderer draws the whole book (ENTITYBLOCK_ANIMATED): the block model is only the breaking particle,
            // taken from the item model's texture (ART14)
            ResourceLocation model = ModelTemplates.PARTICLE_ONLY.create(tome,
                    TextureMapping.particle(Constants.id("item/captains_almanac_tome")), m.models());
            m.blockStates().accept(MultiVariantGenerator.multiVariant(tome, Variant.variant().with(VariantProperties.MODEL, model)));
            // the item model is the Blockbench tome (art/models/captains_almanac.bbmodel, ART14)
            m.handMadeItem(AlmanacContent.ALMANAC_ITEM.get());
        });
        data.blockLoot(loot -> loot.dropSelf(AlmanacContent.ALMANAC.get()));
        data.recipes(out -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, AlmanacContent.ALMANAC_ITEM.get())
                .requires(Items.BOOK).requires(Items.LEATHER, 2).requires(Items.COPPER_INGOT, 2)
                .unlockedBy("has_book", InventoryChangeTrigger.TriggerInstance.hasItems(Items.BOOK))
                .save(out, AlmanacContent.ALMANAC.id()));
        AlmanacData.gatherData(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        List<Class<?>> tests = new ArrayList<>(List.of(AlmanacGameTests.class, AlmanacGiftGameTests.class));
        tests.addAll(AlmanacData.gameTestClasses());
        return List.copyOf(tests);
    }
}
