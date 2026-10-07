package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Datagen for flags. The flagpole's block state shows only the pole (hand-made {@code block/flagpole}) in every state;
 * the cloth is drawn by the block entity renderer ({@code client/FlagClothRenderer}, geometry in
 * {@link FlagClothModel}) at the flag's continuous downwind yaw.
 */
public final class FlagData {

    private FlagData() {
    }

    public static void gather(DataContributions data) {
        data.lang(FlagData::lang);
        data.models(FlagData::models);
        data.recipes(FlagData::recipes);
        data.itemTags(tags -> tags.tag(Flags.FLAGS)
                .add(Flags.MERCHANT_FLAG.get(), Flags.NAVY_FLAG.get(), Flags.JOLLY_ROGER_FLAG.get())
                .addTag(ItemTags.BANNERS));
    }

    private static void lang(LangBuilder lang) {
        lang.item(Flags.MERCHANT_FLAG, "Merchant Flag")
                .item(Flags.NAVY_FLAG, "Navy Flag")
                .item(Flags.JOLLY_ROGER_FLAG, "Jolly Roger")
                .add(FlagpoleBlockEntity.kindKey(FlagKind.NONE), "no flag")
                .add(FlagpoleBlockEntity.kindKey(FlagKind.MERCHANT), "merchant flag")
                .add(FlagpoleBlockEntity.kindKey(FlagKind.NAVY), "navy flag")
                .add(FlagpoleBlockEntity.kindKey(FlagKind.JOLLY_ROGER), "Jolly Roger")
                .add(FlagpoleBlockEntity.kindKey(FlagKind.CUSTOM), "custom flag");
        Map<FlagpoleMachine.Feedback, String> feedback = Map.ofEntries(
                Map.entry(FlagpoleMachine.Feedback.NO_FLAG, "This flagpole flies no flag. Use a flag or a banner on it."),
                Map.entry(FlagpoleMachine.Feedback.CANCELLED, "Stopped working the flagpole"),
                Map.entry(FlagpoleMachine.Feedback.STARTED_HOIST, "Hoisting the %s..."),
                Map.entry(FlagpoleMachine.Feedback.STARTED_STRIKE, "Striking the colors..."),
                Map.entry(FlagpoleMachine.Feedback.STARTED_RAISE, "Raising the %s..."),
                Map.entry(FlagpoleMachine.Feedback.STARTED_TAKE_DOWN, "Taking down the %s..."),
                Map.entry(FlagpoleMachine.Feedback.HOISTED, "The %s is flying"),
                Map.entry(FlagpoleMachine.Feedback.STRUCK, "Colors struck: you signal surrender"),
                Map.entry(FlagpoleMachine.Feedback.RAISED, "The %s is flying again"),
                Map.entry(FlagpoleMachine.Feedback.TAKEN_DOWN, "Took down the flag"));
        feedback.forEach((f, text) -> lang.add(FlagpoleBlockEntity.feedbackKey(f), text));
        String k = FlagCommands.KEY;
        lang.add(k + "not_a_flagpole", "That block is not a flagpole")
                .add(k + "unknown_kind", "Unknown flag kind")
                .add(k + "no_flag", "That flagpole flies no flag")
                .add(k + "get", "Flag: %s, %s. Hoisted by: %s. Pending: %s")
                .add(k + "status.no_flag", "no flag")
                .add(k + "status.flying", "flying")
                .add(k + "status.struck", "struck")
                .add(k + "set", "Flagpole now flies: %s")
                .add(k + "struck", "Colors struck")
                .add(k + "raised", "Colors raised");
    }

    private static void models(ModelContext m) {
        // The flag items' models are hand-made folded bundles (art/models/<flag>.bbmodel, design.md §4.8, ART1b)
        Block pole = ShipDecor.FLAGPOLE.get();
        // Hand-made Blockbench model (art/models/flagpole.bbmodel) in every state: the cloth is drawn by the
        // FlagClothRenderer at the flag's exact downwind yaw (FL1), not by the block model.
        ResourceLocation poleModel = ModelLocationUtils.getModelLocation(pole);
        m.blockStates().accept(MultiVariantGenerator.multiVariant(pole, Variant.variant().with(VariantProperties.MODEL, poleModel)));
    }

    private static void recipes(RecipeOutput out) {
        flagRecipe(out, Flags.MERCHANT_FLAG, Items.WHITE_WOOL, Items.RED_DYE);
        flagRecipe(out, Flags.NAVY_FLAG, Items.BLUE_WOOL, Items.WHITE_DYE);
        flagRecipe(out, Flags.JOLLY_ROGER_FLAG, Items.BLACK_WOOL, Items.BONE);
    }

    /** Shapeless: a stick, two wool and a marker item. */
    private static void flagRecipe(RecipeOutput out, RegistryEntry<Item, Item> flag, Item wool, Item marker) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, flag.get())
                .requires(Items.STICK).requires(wool, 2).requires(marker)
                .unlockedBy("has_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(wool).getPath(),
                        InventoryChangeTrigger.TriggerInstance.hasItems(wool))
                .save(out, flag.id());
    }
}
