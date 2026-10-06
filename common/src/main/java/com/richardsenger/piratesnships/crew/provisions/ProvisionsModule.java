package com.richardsenger.piratesnships.crew.provisions;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The {@code crew.provisions} module: provision rules (design.md §7.4), item classification and its tags, config.
 * The pantry block, crew and HUD that use it come from other modules.
 */
public final class ProvisionsModule implements ModModule {

    /** Convention tag for fruit, present on NeoForge and Fabric. Optional so a missing tag never breaks loading. */
    private static final ResourceLocation C_FRUITS = ResourceLocation.fromNamespaceAndPath("c", "foods/fruit");

    @Override
    public String id() {
        return "crew.provisions";
    }

    @Override
    public void registerConfig() {
        ProvisionsConfig.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.itemTags(tags -> {
            tags.tag(ProvisionTags.PRESERVED).add(Items.BREAD, Items.COOKIE, Items.DRIED_KELP, Items.HONEY_BOTTLE,
                    Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
            tags.tag(ProvisionTags.ANTI_SCURVY).add(Items.APPLE, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                    Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.MELON_SLICE).addOptionalTag(C_FRUITS);
            tags.tag(ProvisionTags.FRESH_WATER).add(Items.WATER_BUCKET);
            tags.tag(ProvisionTags.WATER_BARREL);
            tags.tag(ProvisionTags.RUM);
            tags.tag(ProvisionTags.EXCLUDED).add(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
                    Items.PUFFERFISH, Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT);
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ProvisionsGameTests.class);
    }
}
