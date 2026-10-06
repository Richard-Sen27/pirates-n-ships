package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.Condition;
import net.minecraft.data.models.blockstates.MultiPartGenerator;
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
 * Datagen for flags. The flagpole's look: a multipart block state with the pole (hand-made {@code block/flagpole}) always and,
 * per shown flag kind and facing, the kind's cloth model ({@link FlagClothModel}: one block high, 1.5 blocks long,
 * built in code and written through {@link ModelContext#models()}), rotated by the block state to the facing. The
 * cloth model points north; other facings rotate it by {@link FlagClothModel#yRotation}. A struck flag
 * ({@code flag=none}) shows only the pole.
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
        for (RegistryEntry<Item, Item> flag : Flags.flagItems()) m.flatItem(flag.get());
        Block pole = ShipDecor.FLAGPOLE.get();
        // Hand-made Blockbench model (art/models/flagpole.bbmodel): 3 px pole at the block centre, so the cloth's
        // hoist (from the centre) stays inside it; the cleat sits on a diagonal, clear of the cloth in every facing.
        ResourceLocation poleModel = ModelLocationUtils.getModelLocation(pole);
        MultiPartGenerator gen = MultiPartGenerator.multiPart(pole).with(Variant.variant().with(VariantProperties.MODEL, poleModel));
        for (FlagKind kind : FlagKind.values()) {
            if (kind == FlagKind.NONE) continue;
            ResourceLocation cloth = FlagClothModel.modelId(kind);
            m.models().accept(cloth, () -> FlagClothModel.json(kind));
            for (Direction d : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(FlagpoleBlock.FLAG, kind).term(FlagpoleBlock.FACING, d),
                        Variant.variant().with(VariantProperties.MODEL, cloth).with(VariantProperties.Y_ROT, rotation(d)));
            }
        }
        m.blockStates().accept(gen);
    }

    private static VariantProperties.Rotation rotation(Direction d) {
        return switch (FlagClothModel.yRotation(d)) {
            case 90 -> VariantProperties.Rotation.R90;
            case 180 -> VariantProperties.Rotation.R180;
            case 270 -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
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
